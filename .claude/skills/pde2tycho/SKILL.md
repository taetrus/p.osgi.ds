---
name: pde2tycho
description: Use when migrating an Eclipse PDE workspace (plug-in projects, a target platform, OSGi launch configurations) to a Maven Tycho build — scans the workspace, confirms project and launch selection with the user, generates the Tycho build with pde2tycho, then builds, fixes and smoke-runs it.
---

# Migrate a PDE workspace to Tycho

The conversion is done by the deterministic tool in `tools/pde2tycho/`. Your job is the
judgement around it: selection, reading warnings, and the fix loop. Background and
troubleshooting: `docs/pde-to-tycho-migration.md`.

**Rule: never hand-edit generated files.** Every fix goes into `migration.json` (or, if the
tool is wrong, into the tool with a regression test), followed by `generate --force`.
Otherwise the next regeneration silently undoes the fix.

## Checklist

1. **Ask** for the workspace path (the folder that contains `.metadata/`) and an output
   directory that is empty or does not exist yet.

2. **Scan**
   ```bash
   tools/pde2tycho/pde2tycho.sh scan <workspace> -o <outdir>.migration.json
   ```
   Add `--offline` if the machine has no internet (all directory jars are then vendored).
   Add `--eclipse-home <Eclipse install>` if a warning says `${eclipse_home}` could not be
   resolved.

3. **Summarize** `migration.json` for the user in a few lines:
   - projects by kind, and which are selected
   - target: verbatim locations, resolved jars, vendored jars (with their reasons)
   - launches, and which one is selected
   - every warning, grouped

   Then ask, one question at a time:
   - which projects to include (default: every plug-in, fragment and complete feature)
   - which launch becomes the product (only when there is more than one)
   - whether projects mentioned in warnings as missing should be added
     (`--add-project <dir>`, then rescan)

   Write the answers into `migration.json` (`projects[].selected`, `launches[].selected`,
   `launches[].bundles`).

4. **Generate**
   ```bash
   tools/pde2tycho/pde2tycho.sh generate <outdir>.migration.json <outdir>
   ```
   Show the user the "Changes to arguments and files" section of
   `<outdir>/MIGRATION-REPORT.md`, and copy in any file it says belongs in
   `distribution/configuration/` (ask where the file is if it is unclear).

5. **Build** with an explicit JDK, because Homebrew `mvn` may fork a different one:
   ```bash
   cd <outdir> && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B clean verify
   ```

6. **Fix loop, at most 3 rounds.** Take the first `[ERROR]`, look it up in the
   troubleshooting table of `docs/pde-to-tycho-migration.md`, apply the fix to
   `migration.json`, run `generate --force`, and rebuild. After 3 failed rounds, stop and
   report what you tried and the current error. Do not keep going.

7. **Smoke-run**
   ```bash
   <outdir>/distribution/scripts/run.sh
   ```
   (`run.bat` on Windows). Confirm `osgi>` appears; `ss` at the prompt shows no bundle
   stuck in `INSTALLED`. Stop it with `exit` and `y`.

8. **Report**: build result, anything vendored (those jars must be committed), launcher
   argument changes, and the "Not migrated" section of the report.
