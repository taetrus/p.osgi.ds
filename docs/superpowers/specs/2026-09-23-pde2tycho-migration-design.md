# Design: pde2tycho — migrate an Eclipse PDE workspace to a Maven Tycho build

**Date:** 2026-09-23
**Status:** Approved (design); pending implementation plan
**Goal:** A reusable tool, a Claude skill and a guide that turn the PDE projects, active
target platform and an OSGi launch configuration of an Eclipse workspace into a
self-contained Tycho 4 build: parent pom, `eclipse-plugin`/`eclipse-feature` modules, an
`eclipse-target-definition` module, and an `eclipse-repository` distribution with a
runnable product plus macOS/Windows run scripts.

## Summary

`pde2tycho` is a zero-dependency Java 17 command-line tool with two commands:

1. `scan <workspace>` reads the workspace `.metadata` and writes an editable inventory,
   `migration.json`.
2. `generate migration.json <outdir>` writes a fresh Tycho build into `<outdir>`,
   copying the selected projects. The workspace and the original projects are never
   modified.

A Claude skill drives the two commands interactively (select projects, pick the launch,
run `mvn clean verify`, fix by editing the inventory and regenerating). A guide documents
the manual path and troubleshooting. The generated build follows the shapes already
proven in this repository (parent pom, Maven-location target, `p2.product`,
`run.sh`/`run.bat`).

## Key decisions (locked)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Deliverable | **Java tool + skill + guide** | Deterministic conversion in code; judgement calls (selection, fixing the first build) in the skill; guide for users without Claude. |
| Language / runtime | **Java 17, no dependencies** | Tycho 4 already requires JDK 17+ and Maven; JDK DOM, `java.net.http`, `MessageDigest` and hand-rolled JSON cover everything. Matches the repo's dependency-minimalism. |
| Packaging of the tool | **Plain Maven jar project `tools/pde2tycho/`, outside the Tycho reactor** (like `fatjar/`) | Multi-file and unit-tested; never affects this project's build or product. Wrapper scripts build the jar on first use. |
| Core flow | **scan → `migration.json` → generate** | Reviewable, diffable, reproducible; natural checkpoint for the skill; `generate` runs offline. |
| Output location | **New output directory, projects copied** | Workspace projects may live anywhere (`.location` files); original untouched; safe to rerun. |
| Target platform input | **Maven + Directory locations** (the user's real mix) | Maven locations copied verbatim; Directory jars resolved to Central by SHA-1. |
| Unmatched jars | **Vendored into the target module** (`vendor/plugins/`) | Self-contained, offline, no infrastructure. Tycho 4.0.13 parses `Directory` locations (`TargetDefinitionFile$DirectoryTargetLocation` in `tycho-targetplatform-4.0.13.jar`); a real p2 repo is the fallback (see Open risk). |
| Launch types | **Any launch carrying `selected_target_bundles`/`selected_workspace_bundles`** | The real workspace's `p2.product.launch` is `RuntimeWorkbench` typed yet bundle-list based; keying on the attributes covers it and `EquinoxLauncher` alike. |
| Products per migration | **Exactly one** (v1) | One launch → one product. Multiple products is future work. |
| Product style | **Bundle-based (`useFeatures=false`)** | Only form that carries per-bundle start levels one-to-one from the launch. |
| Target OSes | **macOS (aarch64, x86_64) + Windows x86_64**; Linux only with `--linux` | What the user runs on. |
| Tests | **Not migrated in v1** | Report points to `docs/adding-tests-to-a-tycho-project.md`. |

## Where Eclipse keeps things (scanner inputs)

Verified against `/Users/avalon/dev/workspaces/ws.p.osgi.ds`:

| Data | Location |
|------|----------|
| Project locations | `.metadata/.plugins/org.eclipse.core.resources/.projects/<name>/.location` (binary; contains `URI//file:<path>`). No `.location` → project is inside the workspace folder. |
| Active target | `.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.pde.core.prefs` → `workspace_target_handle` (`resource:/<project>/<file>.target`, `file:`, or a local handle). |
| Unsaved targets | `.metadata/.plugins/org.eclipse.pde.core/.local_targets/<timestamp>.target` |
| Launch configs | `.metadata/.plugins/org.eclipse.debug.core/.launches/*.launch`, plus `*.launch` files inside projects |
| Launch config area | `.metadata/.plugins/org.eclipse.pde.core/<launchName>/config.ini` (resolved `osgi.bundles=` from the last run) |

Launch bundle entries have the form `bundle@startLevel:autoStart`
(e.g. `org.apache.felix.scr@2:true`, `org.apache.commons.commons-io@default:default`).

## Architecture

```
tools/pde2tycho/
  pom.xml                           JDK 17, jar packaging, JUnit 5 test scope only
  pde2tycho.sh / pde2tycho.bat      build jar on first use (mvn -q package), then java -jar
  src/main/java/<pkg>/
    Main.java                       CLI: scan | generate
    scan/WorkspaceScanner.java      .metadata → project locations, active target handle, launches
    scan/ProjectReader.java         .project natures, MANIFEST.MF, build.properties, feature.xml
    scan/TargetReader.java          .target → Maven locations + Directory/Profile jar lists
    scan/LaunchReader.java          .launch bundle sets, VM/program args, JRE; config.ini fallback
    resolve/ArtifactResolver.java   interface: sha1 → Optional<GAV>
    resolve/CentralResolver.java    search.maven.org checksum query; results cached in inventory
    model/Inventory.java            migration.json model + hand-rolled JSON read/write
    gen/ParentPomGenerator.java
    gen/ModulePomGenerator.java     eclipse-plugin / eclipse-feature poms
    gen/TargetGenerator.java        target module + vendor/plugins
    gen/DistributionGenerator.java  eclipse-repository pom, category.xml, .product, optional feature
    gen/ScriptGenerator.java        run.sh / run.bat
    gen/ReportGenerator.java        MIGRATION-REPORT.md
  src/main/resources/templates/     derived from this repo's working pom.xml, p2.product, run.sh, run.bat
  src/test/java/…                   unit tests over fixtures
  src/test/resources/fixtures/      trimmed copy of ws.p.osgi.ds metadata, synthetic targets and projects
.claude/skills/pde2tycho/SKILL.md
docs/pde-to-tycho-migration.md
```

Each reader and generator is independent and communicates only through `Inventory`.

## `scan`

`pde2tycho scan <workspace> [-o migration.json] [--offline]`

1. **Projects.** For each `.projects/<name>` resolve the location, then classify:
   `plugin` (`META-INF/MANIFEST.MF` + PDE plugin nature), `fragment` (`Fragment-Host`),
   `feature` (`feature.xml`), `target` (holds only `.target` files), `other`.
   Record `Bundle-SymbolicName`, `Bundle-Version`, `Bundle-RequiredExecutionEnvironment`,
   source folders from `build.properties`. `selected` defaults to true for
   plugin/fragment/feature, false otherwise.
2. **Targets.** All `*.target` files in projects plus `.local_targets/`; flag the one
   matching `workspace_target_handle` as `active`.
   - `type="Maven"` and `type="InstallableUnit"` (p2 site) locations: copied verbatim
     (Tycho consumes both natively).
   - `type="Directory"` / `type="Profile"` locations: expanded to their jars (a
     Profile/installation's `plugins/` folder). Each jar is SHA-1 hashed and looked up on
     Central (`https://search.maven.org/solrsearch/select?q=1:<sha1>&wt=json`).
     Hit → `resolved` (GAV). Miss → `vendor`. Hit whose Central jar has no OSGi
     manifest → flagged; generated either as a Maven dep with
     `missingManifest="generate"` or vendored (default: vendored, since the local jar
     is already an OSGi bundle).
   - `--offline` or Central unreachable → everything goes to `vendor`, with a warning.
3. **Launches.** Every launch carrying `selected_target_bundles` or
   `selected_workspace_bundles`, regardless of `type`. Record bundles
   `{id, startLevel|default, autoStart|default}`, `VM_ARGUMENTS`, `PROGRAM_ARGUMENTS`,
   JRE container. If the config area's `config.ini` exists, use its `osgi.bundles` to
   fill gaps and warn about drift between it and the `.launch`.
4. **Warnings** (never fatal): launch workspace bundles not selected; launch target
   bundles not found in the active target; `build.properties` with a test folder nested
   under `source..`; unparsable files.

`selected` on exactly one launch is required before `generate`; `scan` preselects the
launch if there is only one.

### Inventory (`migration.json`)

```json
{
  "workspace": "/Users/avalon/dev/workspaces/ws.p.osgi.ds",
  "groupId": "com.kk.pde.ds",
  "version": "1.0.0-SNAPSHOT",
  "tychoVersion": "4.0.13",
  "environments": ["macosx/cocoa/aarch64", "macosx/cocoa/x86_64", "win32/win32/x86_64"],
  "projects": [
    { "name": "com.kk.pde.ds.api", "kind": "plugin", "path": "/Users/avalon/dev/p.osgi.ds/com.kk.pde.ds.api",
      "bsn": "com.kk.pde.ds.api", "version": "1.0.0.qualifier", "bree": "JavaSE-1.8", "selected": true }
  ],
  "target": {
    "source": "com.kk.pde.ds.target/com.kk.pde.ds.target.target",
    "mavenLocations": ["<verbatim XML>"],
    "resolved": [ { "jar": "/abs/path/x.jar", "sha1": "…", "gav": "g:a:v" } ],
    "vendor":   [ { "jar": "/abs/path/y.jar", "sha1": "…", "reason": "not on Central" } ]
  },
  "launches": [
    { "name": "p2.product", "selected": true, "productId": "com.kk.pde.ds.product",
      "bundles": [ { "id": "org.apache.felix.scr", "level": 2, "auto": true } ],
      "vmArgs": "…", "programArgs": "…", "jre": "JavaSE-21" }
  ],
  "warnings": [ "…" ]
}
```

Defaults: `groupId` = longest common dot-prefix of selected bundle names;
`version` = `1.0.0-SNAPSHOT`; `productId` = launch `productId` attribute if present,
else `<groupId>.product`.

## `generate`

`pde2tycho generate migration.json <outdir> [--force] [--linux]`

Validates first: exactly one selected launch; every launch bundle is either a selected
project or present in the target (Maven, resolved, or vendored). Otherwise it fails and
lists the missing IDs. Refuses a non-empty `outdir` without `--force`.

```
<outdir>/
  pom.xml                        parent: packaging pom; tycho-maven-plugin (extensions);
                                 target-platform-configuration → target artifact; environments;
                                 <modules> in dependency-agnostic order (Tycho sorts)
  <project>/                     copied project (skips bin/, target/, .metadata; keeps .settings)
    pom.xml                      parent ref; artifactId = BSN; version from Bundle-Version
                                 (.qualifier → -SNAPSHOT); packaging eclipse-plugin | eclipse-feature
  <groupId>.target/              packaging eclipse-target-definition
    <groupId>.target             Maven locations verbatim + resolved jars as Maven dependencies
                                 + Directory location → vendor/plugins (only if non-empty)
    vendor/plugins/*.jar
  <groupId>.product.feature/     only if the launch's workspace bundles are not all covered by a
                                 selected feature (category.xml needs a feature)
  distribution/                  packaging eclipse-repository
    pom.xml                      tycho-p2-director-plugin: materialize-products, archive-products
    category.xml
    <launch>.product             useFeatures=false; <plugins> = all launch bundles;
                                 <configurations> = start level / autoStart per bundle
                                 (default:default omitted); launcher args rewritten (below)
    scripts/run.sh, run.bat
  MIGRATION-REPORT.md
```

**Launcher argument rewriting.** Strip PDE-only arguments and variables
(`-os ${target.os} -ws ${target.ws} -arch ${target.arch} -nl ${target.nl}`, any
`${workspace_loc…}`/`${project_loc…}`). Move `-XstartOnFirstThread` to `vmArgsMac`. Keep
everything else and list each change in the report.

**Run scripts.** Templated from this repo's `distribution/scripts/run.sh` and `run.bat`:
OS detection, product path discovery (macOS `Eclipse.app/Contents/Eclipse`, Windows
`win32/win32/x86_64`), launcher VM args, forwarding of user `-D` flags before `-jar`,
`-configuration configuration -console -consoleLog`.

## Skill: `.claude/skills/pde2tycho/SKILL.md`

1. Ask for the workspace path and output directory; run `scan`.
2. Summarize the inventory: projects by kind, active target (Maven / resolved / vendored
   counts), launches, warnings. Ask which projects to include and which launch becomes
   the product; write answers into `migration.json`.
3. Run `generate`, then `mvn clean verify` in `<outdir>` with `JAVA_HOME` set explicitly
   (Homebrew `mvn` may fork a different JDK).
4. Fix loop, at most 3 rounds: match the build error against the guide's troubleshooting
   table; apply the fix **to the inventory** (never hand-edit generated files); regenerate;
   rebuild. After 3 failed rounds, stop and report.
5. Smoke-run with `distribution/scripts/run.sh`; confirm `osgi>` appears and `ss` shows no
   `INSTALLED` (unresolved) bundles.

## Guide: `docs/pde-to-tycho-migration.md`

Same style as `docs/adding-tests-to-a-tycho-project.md`: where Eclipse stores each piece
of metadata, the manual steps the tool automates, the `migration.json` fields, running
the tool with and without Claude, and troubleshooting by error message
(`requires osgi.bundle … could not be found`, BREE/EE mismatches, missing manifest on a
Maven dependency, Directory path not found, product fails to start).

## Error handling

- Malformed project, target or launch → warning in the inventory; scan continues.
- Central unreachable → vendor everything, warn, note in report.
- `generate` validation failures → non-zero exit with an explicit list; nothing written.
- All paths handled with `java.nio.file`; `.bat` output uses CRLF line endings.

## Testing

- **Unit tests** (JUnit 5, `tools/pde2tycho`, run with `mvn -f tools/pde2tycho verify`):
  - `WorkspaceScanner` over a fixture copy of `ws.p.osgi.ds/.metadata` (`.location`
    files, prefs, `p2.product.launch`, `config.ini`).
  - `LaunchReader` bundle-entry parsing incl. `@default:default`.
  - `TargetReader` over a synthetic target with Maven + Directory locations.
  - `ArtifactResolver` stubbed; `CentralResolver` JSON parsing tested on a recorded response.
  - `Inventory` JSON round-trip.
  - Each generator checked against expected output files.
- **Golden acceptance test (manual, documented):** migrate the real `ws.p.osgi.ds`
  workspace to a scratch dir; `mvn clean verify` passes; `run.sh` starts the product;
  `Hello world!` is logged and `curl localhost:8080/api/greet` answers. (That workspace
  knows only the original bundles and the `p2.product` launch, so the run uses
  `--add-project` for `chatbot`/`mcp.llm` and adds them to the launch in
  `migration.json` — exercising the inventory-edit fix loop.)

## Open risk — resolved (2026-09-28 spike)

How Tycho 4.0.13 resolves a `Directory` location path. Probed with a throwaway build
(a bundle importing a package only the vendored jar provides):

| `path=` | Result |
|---------|--------|
| `vendor/plugins` (relative) | silently empty → `requires 'java.package; …' but it could not be found` |
| `${project_loc}/vendor/plugins` | silently empty (same error) |
| `${basedir}/vendor/plugins` | silently empty (same error) |
| **`${project_loc:/<target-module>}/vendor/plugins`** | **resolves** — also PDE's own syntax, so the target still works in the IDE |

No p2-repository fallback is needed.

## Refinements made while planning (2026-09-28)

Each was verified or forced by the real workspace; they narrow, not widen, the design.

1. **No generated feature.** Tycho 4 `category.xml` accepts `<bundle id="…" version="0.0.0">`
   entries (verified), so workspace bundles not covered by a selected feature are listed
   directly.
2. **Features whose plug-ins are not all workspace projects start deselected** (with a
   warning) — the real workspace's `com.kk.pde.ds.feature` lists 16 plug-ins, only 7 of
   which the workspace has.
3. **`scan --add-project <dir>` (repeatable)** adds projects the workspace never imported.
   The real workspace needs it: today's `com.kk.pde.ds.app` imports
   `com.kk.pde.ds.chatbot`, which imports `com.kk.pde.ds.mcp.llm`; neither is registered.
4. **Central hits need no manifest check.** A SHA-1 match means identical bytes, so a
   resolved jar is exactly as much a bundle as the local one. Local jars without a
   `Bundle-SymbolicName` are skipped with a warning (PDE ignores them too).
5. **Central lookups:** measured latency varies 0.2 s–30 s with occasional timeouts, so
   each request has a 30 s timeout, a failed lookup vendors that jar, and 3 consecutive
   failures switch the rest of the scan to offline. Progress goes to stderr.
6. **`config.ini` is a warning source only** (bundles PDE auto-added at the last run),
   not a gap filler.
7. **Launch-bundle validation against the target** is only possible when the target
   has no verbatim Maven/p2 locations (their bundle names are unknown until Tycho
   resolves them); otherwise Tycho reports missing bundles itself.
8. **Target artifact version is a literal** in the parent pom, not `${project.version}`,
   so bundles whose `Bundle-Version` differs from the parent version still find it.
9. **Test sources are copied but not compiled.** Tycho 4 compiles `.classpath`
   `test="true"` folders (e.g. `src_test/`) and ignores `-Dmaven.test.skip` for that
   (verified); the parent pom unbinds `tycho-compiler-plugin`'s `default-testCompile`
   (verified to work). Removing that override is step one of migrating tests.
10. **Launch VM arguments go on the run scripts' `java` command line.** The product has no
    native launcher (`includeLaunchers=false`), so the product's `<vmArgs>` are
    documentation only. `-XstartOnFirstThread` is kept in `<vmArgsMac>` but left out of
    `run.sh` (SWT-only; it blocks AWT/Swing under `java -jar`).
11. **`EquinoxLauncher` launches honour `default_auto_start`** (default true) for
    `default:default` entries; `RuntimeWorkbench` launches treat them as not started.
12. **Project kind comes from files, not natures:** `feature.xml` → feature; a manifest
    with `Bundle-SymbolicName` → plug-in or fragment; only `*.target` → target.
13. **Every generated file comes from a template resource** (`src/main/resources/templates/`)
    with `@TOKEN@` placeholders; rendering fails if a placeholder is left unfilled.
14. **Manifests are read tolerantly:** `java.util.jar.Manifest` silently drops a last
    header that lacks a trailing newline (verified), so a newline is appended first.

## Out of scope (v1)

Multiple products per migration; migrating tests; `Eclipse Application` launches with
`useProduct=true` / feature-based launches; p2 `InstallableUnit` locations beyond
verbatim copy; Linux run script; in-place conversion; JUnit Plug-in Test launches.

## Verification record (acceptance run, 2026-09-28)

Migrated `/Users/avalon/dev/workspaces/ws.p.osgi.ds` with `--add-project` for chatbot and
mcp.llm plus the two launch entries added in migration.json; `mvn clean verify` on JDK 21:
`BUILD SUCCESS`, `Total time:  5.991 s` (12-module reactor; products for
macosx/cocoa/aarch64, macosx/cocoa/x86_64 and win32/win32/x86_64); `run.sh` served
`/api/greet` (`{"message":"Hello from OSGi HTTP Whiteboard!",...}`) and logged
`Hello world!`.
Fix-loop rounds needed: 0 (the inventory edit for the two launch bundles was the only fix).
