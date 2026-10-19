# Migrate an Eclipse PDE Workspace to Tycho

This guide tells what `tools/pde2tycho` does. Use it to run the tool, to examine its
output, or to do the migration manually. The result is a self-contained Maven Tycho 4.0.13
build with these parts:

- A parent pom.
- One module for each plug-in or feature.
- A target-definition module.
- A distribution that builds a p2 repository and a product that you can run on macOS and
  Windows.

## 1. Where Eclipse keeps the data that you need

| Data | Location in the workspace |
|------|---------------------------|
| The projects and their locations | `.metadata/.plugins/org.eclipse.core.resources/.projects/<name>/.location`. This is a binary file that contains `URI//file:/path/to/project`. If there is no `.location` file, the project is in the workspace folder. |
| The active target platform | `.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.pde.core.prefs`, key `workspace_target_handle`. The value is `resource:/<project>/<file>.target`, a `file:` URI, or a target in `.metadata/.plugins/org.eclipse.pde.core/.local_targets/`. |
| Launch configurations | `.metadata/.plugins/org.eclipse.debug.core/.launches/*.launch`, and each `*.launch` file in a project |
| The bundles that the last run of a launch used | `.metadata/.plugins/org.eclipse.pde.core/<launch name>/config.ini`, key `osgi.bundles` |

The bundles of a launch are `setEntry` values with the form `id@startLevel:autoStart`.
Examples: `org.apache.felix.scr@2:true` and `org.apache.commons.commons-io@default:default`.
"OSGi Framework" launches use this form. "Eclipse Application" launches with a bundle list
use the same form.

## 2. Run the tool

```bash
tools/pde2tycho/pde2tycho.sh scan <workspace> -o migration.json      # macOS / Linux
tools\pde2tycho\pde2tycho.bat scan <workspace> -o migration.json     # Windows
# review/edit migration.json
tools/pde2tycho/pde2tycho.sh generate migration.json <outdir>
cd <outdir> && mvn clean verify
distribution/scripts/run.sh                                          # or run.bat
```

The first run builds the tool. This needs JDK 17 or later and Maven. Tycho needs them too.
In Claude Code, the `pde2tycho` skill does these steps and the correction loop for you.

| Option | Command | Meaning |
|--------|---------|---------|
| `-o <file>` | scan | The file for the inventory. The default is `migration.json`. |
| `--offline` | scan | Do not use Maven Central. The tool vendors each jar of a Directory location. |
| `--eclipse-home <dir>` | scan | The value for `${eclipse_home}` in Profile and Directory locations. |
| `--add-project <dir>` | scan | Include a project that the workspace did not import. You can use this option more than one time. |
| `--force` | generate | Write into a directory that is not empty. The tool overwrites files. It removes only the `distribution/*.product` file and the vendored jars of an earlier run. The report lists the module directories that remain. |
| `--linux` | generate | Also build `linux/gtk/x86_64`, and add a Linux branch to `run.sh`. |

## 3. The inventory: `migration.json`

`scan` writes all the data that it finds. `generate` reads only this file.

1. Edit `migration.json` to change the result.
2. Keep `migration.json` next to the output. Then a new `generate` run makes the same
   build again.

| Field | Edit it to… |
|-------|-------------|
| `groupId`, `version` | Change the Maven coordinates. The defaults are the common prefix of the bundle names and `1.0.0-SNAPSHOT`. |
| `environments` | Build for other platforms (`os/ws/arch`). |
| `projects[].selected` | Include or exclude a project. Plug-ins, fragments and features become modules. |
| `target.locations` | Change the Maven and p2 locations. The tool copies them into the new `.target` file without change. |
| `target.resolved` / `target.vendor` | Move a jar between "use Maven coordinates" and "vendor the file". |
| `launches[].selected` | Select the launch that becomes the product. Select exactly one launch. |
| `launches[].productId` | Set the product ID. The default is the ID of the launch. If the launch has no ID, the default is `<groupId>.product`. |
| `launches[].bundles` | Add or remove product bundles. A `level` or `autoStart` value of `null` means the PDE value "default". |
| `launches[].vmArgs`, `programArgs` | Set the arguments that the run scripts give. |

## 4. The generated files and why they have this form

| Output | Why it has this form |
|--------|----------------------|
| `pom.xml` (parent) | It contains the Tycho extension and the environments. It contains `target-platform-configuration`, which points to the target module with a **literal** version. Without the literal version, a bundle with a different `Bundle-Version` looks for a target that does not exist. It also contains an override that stops Tycho from compiling test folders (refer to §6). |
| `<project>/pom.xml` | Approximately 10 lines. Tycho is manifest-first: `MANIFEST.MF` and `build.properties` stay the source of truth. The pom version agrees with `Bundle-Version` (`1.2.3.qualifier` ↔ `1.2.3-SNAPSHOT`). |
| `<groupId>.target/<groupId>.target` | It contains the Maven and p2 locations without change. The directory jars that the tool finds on Maven Central (by SHA-1) go into one Maven location. The other jars go into `vendor/plugins/`. The target refers to them as `${project_loc:/<groupId>.target}/vendor/plugins`. |
| `distribution/category.xml` | It contains the selected features. It also contains, as `<bundle>` entries, the workspace bundles that no feature includes. |
| `distribution/<productId>.product` | It is bundle-based, with one `<plugin>` for each launch bundle. The `startLevel` and `autoStart` values come from the launch. It has no native launcher. |
| `distribution/pom.xml` | It makes the product. Then it writes `config.ini` again, so that `java -jar plugins/org.eclipse.osgi_*.jar` works. It copies `configuration/` and the run scripts into the product. It makes an archive for each platform. |
| `distribution/scripts/run.sh`, `run.bat` | They find the built product for the current OS. They give the VM arguments of the launch before `-jar` and the program arguments after it. |
| `MIGRATION-REPORT.md` | It records each decision. It lists which jars the tool vendored and why, and which arguments changed. It also lists the scan warnings and the items that the tool did not migrate. |

## 5. Do the migration manually

1. Make the parent pom as §4 describes. List each module in it.
2. Add a pom of approximately 10 lines to each plug-in and feature (`eclipse-plugin` /
   `eclipse-feature`).
3. Make the target module. Copy the Maven and p2 locations into it.
4. For each jar of a Directory location, search
   `https://search.maven.org/solrsearch/select?q=1:<sha1>`.
5. If the search finds the jar, use its coordinates. If not, copy the jar into
   `vendor/plugins/`.
6. Make the folder `distribution/`. Put these items in it:
   - `category.xml`.
   - A `.product` file that lists the bundles of the launch and their `startLevel` values.
   - The `config.ini` correction from the generated `distribution/pom.xml`.
7. Write run scripts that call `java <vm args> -jar plugins/org.eclipse.osgi_*.jar
   -configuration configuration -console`.

## 6. Unexpected behavior

- **An incorrect Directory path does not cause an error.** Tycho 4.0.13 reads these paths
  as empty folders, without a message: `vendor/plugins`, `${project_loc}/vendor/plugins`
  and `${basedir}/vendor/plugins`. Only `${project_loc:/<module>}/vendor/plugins` works.
  PDE also reads this form.
- **`-Dmaven.test.skip=true` does not stop Tycho from compiling test folders.**
  `tycho-compiler-plugin:testCompile` compiles each folder that has the mark `test="true"`
  in `.classpath`. The generated parent pom unbinds `default-testCompile`. Remove that
  override when you migrate tests (`docs/adding-tests-to-a-tycho-project.md`).
- **A manifest without a final newline loses its last header** when
  `java.util.jar.Manifest` reads it. The tool compensates for this. Other tools may not.
  End the file with a newline.
- **`-XstartOnFirstThread` is only for SWT.** The macOS arguments of the product keep it.
  `run.sh` does not use it, because it blocks AWT and Swing when the product runs with `java -jar`.
- **The `.launch` file does not contain the bundles that PDE added automatically**
  ("include required bundles"). The product includes the bundles that imports *require*.
  Some bundles are necessary only at run time, for example a logging backend. Add each of
  these bundles to the launch bundles in `migration.json`. `scan` shows a warning for each
  one that it finds in `config.ini`.
- **The tool copies symlinked folders in a project as empty directories.** Before you
  build the output, replace the link with the real folder. As an alternative, copy the
  content of the folder into the output.

## 7. Troubleshooting by error message

| Error (first `[ERROR]` line) | Cause | Correction |
|------------------------------|-------|------------|
| `Missing requirement: X requires 'java.package; p' but it could not be found` | The bundle that supplies `p` is not a selected project and is not in the target. | Select its project (or use `scan --add-project`). Or add the bundle to the target, in a Maven location or in `vendor/plugins`. |
| `…requires 'osgi.bundle; B' but it could not be found` | The same cause, for `Require-Bundle`. | The same correction. |
| `…requires 'java.package; p' but it could not be found`, but a jar in `vendor/plugins` supplies `p` | The path form of the Directory location is incorrect. Thus Tycho sees an empty folder. | Set the location to exactly `${project_loc:/<target module>}/vendor/plugins`. |
| `Could not resolve target platform … Maven` / `artifact not found` | A Maven location entry is not in the configured repositories. | The tool copies `target.locations` entries as XML without change. Correct the `groupId`, `artifactId` or `version` there. Or remove that `<dependency>` and vendor the jar. To vendor the jar, put the file into `<target module>/vendor/plugins`. Then add a `target.vendor` entry for it (`jar`, `bsn`, `version`). A new `generate` run then keeps it. |
| `Unresolved requirement … osgi.ee; JavaSE-N` | The BREE of a bundle is newer than the build JDK. | Build with a newer JDK (`JAVA_HOME`). |
| `tycho-compiler-plugin …:testCompile … package org.junit… does not exist` | Somebody removed the `default-testCompile` override and did not add test dependencies. | Restore the override. Or add the test dependencies (refer to the testing how-to). |
| `Feature F includes X, which neither a selected project nor the target provides` (from `generate`) | A selected feature lists a plug-in that is not available. | Clear the selection of the feature. Or add the project of the plug-in. |
| `Launch bundle X is not in the target platform` (from `generate`) | The target does not contain bundle X. Usually, the workspace used the Default or Running Platform target. Thus `scan` found no `.target` file to read. | Add a Maven or p2 (InstallableUnit) location that supplies X to `target.locations`, or add a `target.vendor` entry. Or activate a real `.target` file in Eclipse and do the scan again. |
| `Launch bundle X is a workspace bundle but no selected project provides it` (from `generate`) | The launch uses a project that is not selected or not in the workspace. | Select the project. Or use `scan --add-project <dir>`. |
| The product runs, but a component never activates. | A bundle that PDE added automatically is not in the product. Or the `autoStart` value of a bundle is not `true`. | Read the `config.ini` warnings of the scan. Add the bundle to `launches[].bundles` with `"autoStart": true`. |
| `ERROR: Could not find org.eclipse.osgi jar` from `run.sh` | The build made no product for this OS and CPU. | Add the environment (for example `macosx/cocoa/aarch64`) to `environments`. Build again. |
