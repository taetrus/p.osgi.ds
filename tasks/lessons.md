# Lessons

## OSGi / Tycho dependency work (from the RAG feature)

- **Before you import a third-party bundle, check what it EXPORTS.** The Apache Tika
  uber-bundle `tika-bundle-standard` exports almost nothing (only `…parser.internal`). It
  imports the API of `tika-core` and registers parsers as OSGi *services*. "Self-contained
  uber-bundle" did not mean "API that you can import". Read the `MANIFEST.MF` of the jar
  (`Export-Package`, `Import-Package`, `Bundle-Activator`) before you connect it.

- **Check the transitive version constraints of foundational packages EARLY** (slf4j,
  commons-*). All Apache Tika versions require `org.slf4j [2.0,3)`. This project used slf4j 1.7.
  The upgrade caused a chain of changes: Logback 1.3, then Aries SPI Fly. It then broke Felix
  Health Check (`org.slf4j.helpers [1.7,2.0)`). One `grep` for `org.slf4j` in the manifest of
  the candidate would have shown this before any code.

- **Imports with an upper bound start the chain of changes.** Our own bundles used open minimums
  (`version="1.7.32"` = `[1.7.32,∞)`), and they continued to work with slf4j 2.0. The `[1.7,2.0)`
  range of Felix Health Check did not. When a dependency forces a major version change, the
  bundles with strict upper bounds break.

- **When a dependency tries to change the full platform, question the requirement.** We did not
  migrate the logging to get Tika. We parsed the formats directly: PDFBox (which logs through JCL)
  for PDF, and raw OOXML-zip reading for Office. The result is 3 bundles, not a platform-wide
  slf4j migration. When you see a chain of changes, stop and make a new plan.

- **The hand-written `OSGI-INF/*.xml` controls SCR, not the annotations.** Tycho does not generate
  the DS XML here. SCR ignores `@Activate` and `@Deactivate`. It uses only the XML.
  - The descriptors use namespace `scr/v1.1.0` (`GreetHealthCheck.xml` uses `scr/v1.3.0`). In
    these namespaces, an XML with no `activate` attribute makes SCR call a method named
    `activate`. An XML with no `deactivate` attribute makes SCR call a method named `deactivate`.
  - Add the attribute only when the method has a different name, for example `activate="start"`.
  - Example from this repository: `App.java` has `@Deactivate` on `stop()`. `App.xml` did not
    have `deactivate="stop"`, so SCR never called `stop()`. When the app bundle stopped, the Clock
    and Chat windows stayed open and the clock timer continued. Commit 7470d2c fixed this.
  - When you change a lifecycle method in Java, change the XML too.

- **`java [JVM-opts] -jar app.jar [program-args]`: the JVM ignores `-D` flags AFTER `-jar`.**
  `run.sh` added `"$@"` after `-jar`. Thus `-Drag.docs.dir` (and `-Dopenrouter.api.key`) never
  became system properties. Put the JVM arguments before `-jar`.

- **Check at the lowest-cost gate first, then go up.** The order is: resolver error → build →
  unit logic (offline) → runtime activation → end-to-end test against a mock endpoint. Each gate
  found a different class of problem. A successful build proved nothing about the runtime wiring.
