# Migrating an Eclipse PDE workspace to Tycho

This guide explains what `tools/pde2tycho` does, so you can run it, review its output, or
do the migration by hand. The result is a self-contained Maven Tycho 4.0.13 build: a
parent pom, one module per plug-in or feature, a target-definition module, and a
distribution that builds a p2 repository plus a runnable product for macOS and Windows.

## 1. Where Eclipse keeps what you need

| Data | Location in the workspace |
|------|---------------------------|
| Which projects exist, and where | `.metadata/.plugins/org.eclipse.core.resources/.projects/<name>/.location` — a binary file containing `URI//file:/path/to/project`. No `.location` means the project sits inside the workspace folder. |
| The active target platform | `.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.pde.core.prefs`, key `workspace_target_handle`: `resource:/<project>/<file>.target`, a `file:` URI, or a target stored in `.metadata/.plugins/org.eclipse.pde.core/.local_targets/` |
| Launch configurations | `.metadata/.plugins/org.eclipse.debug.core/.launches/*.launch`, plus any `*.launch` saved inside a project |
| What a launch actually started last time | `.metadata/.plugins/org.eclipse.pde.core/<launch name>/config.ini`, key `osgi.bundles` |

A launch's bundles are `setEntry` values of the form `id@startLevel:autoStart`, for
example `org.apache.felix.scr@2:true` or `org.apache.commons.commons-io@default:default`.
Both "OSGi Framework" launches and "Eclipse Application" launches with a bundle list
work the same way.

## 2. Running the tool

```bash
tools/pde2tycho/pde2tycho.sh scan <workspace> -o migration.json      # macOS / Linux
tools\pde2tycho\pde2tycho.bat scan <workspace> -o migration.json     # Windows
# review/edit migration.json
tools/pde2tycho/pde2tycho.sh generate migration.json <outdir>
cd <outdir> && mvn clean verify
distribution/scripts/run.sh                                          # or run.bat
```

The first run builds the tool (JDK 17+ and Maven, which Tycho needs anyway). With Claude
Code, the `pde2tycho` skill runs these steps and the fix loop for you.

| Option | Command | Meaning |
|--------|---------|---------|
| `-o <file>` | scan | where to write the inventory (default `migration.json`) |
| `--offline` | scan | skip Maven Central; every directory jar is vendored |
| `--eclipse-home <dir>` | scan | value for `${eclipse_home}` in Profile/Directory locations |
| `--add-project <dir>` | scan | include a project the workspace never imported (repeatable) |
| `--force` | generate | write into a non-empty directory (overwrites, never deletes) |
| `--linux` | generate | also build `linux/gtk/x86_64` and a Linux branch in `run.sh` |

## 3. The inventory: `migration.json`

`scan` writes everything it found; `generate` reads only this file. Edit it to change
the outcome, and keep it next to the output so a regeneration reproduces the build.

| Field | Edit it to… |
|-------|-------------|
| `groupId`, `version` | change Maven coordinates (default: common prefix of the bundle names, `1.0.0-SNAPSHOT`) |
| `environments` | build other platforms (`os/ws/arch`) |
| `projects[].selected` | include or exclude a project (plug-ins, fragments and features become modules) |
| `target.locations` | Maven/p2 locations copied into the new `.target` as-is |
| `target.resolved` / `target.vendor` | move a jar between "use Maven coordinates" and "vendor the file" |
| `launches[].selected` | pick the launch that becomes the product (exactly one) |
| `launches[].productId` | the product id (default: the launch's, else `<groupId>.product`) |
| `launches[].bundles` | add or remove product bundles; `level`/`autoStart` `null` = PDE "default" |
| `launches[].vmArgs`, `programArgs` | arguments the run scripts pass |

## 4. What gets generated, and why

| Output | Why it looks like this |
|--------|------------------------|
| `pom.xml` (parent) | Tycho extension, `target-platform-configuration` pointing at the target module with a **literal** version (a bundle with a different `Bundle-Version` would otherwise look for a target that does not exist), the environments, and an override that stops Tycho compiling test folders (see §6) |
| `<project>/pom.xml` | ~10 lines. Tycho is manifest-first: `MANIFEST.MF` and `build.properties` stay the source of truth. The pom version mirrors `Bundle-Version` (`1.2.3.qualifier` ↔ `1.2.3-SNAPSHOT`) |
| `<groupId>.target/<groupId>.target` | Maven and p2 locations verbatim; directory jars found on Maven Central (matched by SHA-1) as one Maven location; the rest in `vendor/plugins/`, referenced as `${project_loc:/<groupId>.target}/vendor/plugins` |
| `distribution/category.xml` | selected features, plus workspace bundles no feature covers, as `<bundle>` entries |
| `distribution/<productId>.product` | bundle-based, one `<plugin>` per launch bundle, start levels and auto-start from the launch, no native launcher |
| `distribution/pom.xml` | materializes the product, then rewrites `config.ini` so `java -jar plugins/org.eclipse.osgi_*.jar` works, copies `configuration/` and the run scripts in, and archives each platform |
| `distribution/scripts/run.sh`, `run.bat` | find the built product for the current OS, pass the launch's VM arguments before `-jar` and program arguments after it |
| `MIGRATION-REPORT.md` | every decision: what was vendored and why, which arguments changed, scan warnings, what was not migrated |

## 5. Doing it by hand

1. Create the parent pom from §4 and list every module.
2. Add a ~10-line pom to each plug-in and feature (`eclipse-plugin` / `eclipse-feature`).
3. Create the target module. Copy Maven and p2 locations. For each jar of a Directory
   location, search `https://search.maven.org/solrsearch/select?q=1:<sha1>`; use the
   coordinates when it is found, otherwise copy the jar into `vendor/plugins/`.
4. Create `distribution/` with `category.xml`, a `.product` listing the launch's bundles
   and their start levels, and the `config.ini` fix from the generated `distribution/pom.xml`.
5. Write run scripts that call `java <vm args> -jar plugins/org.eclipse.osgi_*.jar
   -configuration configuration -console`.

## 6. Things that behave differently than you would expect

- **A wrong Directory path is not an error.** Tycho 4.0.13 silently treats `vendor/plugins`,
  `${project_loc}/vendor/plugins` and `${basedir}/vendor/plugins` as empty. Only
  `${project_loc:/<module>}/vendor/plugins` works, and it is also what PDE understands.
- **`-Dmaven.test.skip=true` does not stop Tycho compiling test folders.** Folders marked
  `test="true"` in `.classpath` are compiled by `tycho-compiler-plugin:testCompile`. The
  generated parent pom unbinds `default-testCompile`; delete that when you migrate tests
  (`docs/adding-tests-to-a-tycho-project.md`).
- **A manifest without a final newline loses its last header** when read with
  `java.util.jar.Manifest`. The tool compensates, but other tools may not; end the file
  with a newline.
- **`-XstartOnFirstThread` is SWT-only.** It stays in the product's macOS arguments but is
  left out of `run.sh`, because it blocks AWT/Swing started with `java -jar`.
- **Bundles PDE added automatically** ("include required bundles") are not in the
  `.launch`. The product includes bundles that are *required* by imports; a bundle that
  is only needed at run time (for example a logging backend) must be added to the launch's
  bundles in `migration.json`. `scan` warns about each one it sees in `config.ini`.

## 7. Troubleshooting by error message

| Error (first `[ERROR]` line) | Cause | Fix |
|------------------------------|-------|-----|
| `Missing requirement: X requires 'java.package; p' but it could not be found` | the bundle providing `p` is neither a selected project nor in the target | select its project (or `scan --add-project`), or add it to the target (a Maven location or `vendor/plugins`) |
| `…requires 'osgi.bundle; B' but it could not be found` | same, for `Require-Bundle` | same |
| the error names a jar in `vendor/plugins` as missing | the Directory path form is wrong | the location must be `${project_loc:/<target module>}/vendor/plugins` |
| `Could not resolve target platform … Maven` / `artifact not found` | a Maven location entry does not exist in the configured repositories | correct the coordinates in `target.locations`, or move the jar to `target.vendor` |
| `Unresolved requirement … osgi.ee; JavaSE-N` | a bundle's BREE is newer than the build JDK | build with a newer JDK (`JAVA_HOME`) |
| `tycho-compiler-plugin …:testCompile … package org.junit… does not exist` | the `default-testCompile` override was removed without adding test dependencies | restore it, or add the test dependencies (testing how-to) |
| `Feature F includes X, which neither a selected project nor the target provides` (from `generate`) | a selected feature lists a missing plug-in | deselect the feature, or add the plug-in's project |
| `Launch bundle X is a workspace bundle but no selected project provides it` (from `generate`) | the launch uses a project that is deselected or not in the workspace | select it, or `scan --add-project <dir>` |
| product starts, but a component never activates | a bundle PDE auto-added is missing from the product, or a bundle is not auto-started | check the scan's `config.ini` warnings; add the bundle to `launches[].bundles` with `"autoStart": true` |
| `ERROR: Could not find org.eclipse.osgi jar` from `run.sh` | no product was built for this OS/CPU | add the environment (e.g. `macosx/cocoa/aarch64`) to `environments` and rebuild |
