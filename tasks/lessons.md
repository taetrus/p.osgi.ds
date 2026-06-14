# Lessons

## OSGi / Tycho dependency work (from the RAG feature)

- **Verify what a third-party bundle actually EXPORTS before importing it.** Apache
  Tika's `tika-bundle-standard` uber-bundle exports almost nothing (only
  `…parser.internal`); it imports `tika-core`'s API and registers parsers as OSGi
  *services*. "Self-contained uber-bundle" did not mean "importable API." Inspect the
  jar's `MANIFEST.MF` (`Export-Package` / `Import-Package` / `Bundle-Activator`)
  before wiring.

- **Check transitive version constraints of foundational packages EARLY** (slf4j,
  commons-*). Apache Tika (every version) hard-requires `org.slf4j [2.0,3)`. This
  project ran slf4j 1.7; the upgrade cascaded into Logback 1.3 + Aries SPI Fly and
  then broke Felix Health Check (`org.slf4j.helpers [1.7,2.0)`). One `grep` of the
  candidate's manifest for `org.slf4j` would have surfaced this before any code.

- **Upper-bounded imports are the cascade trigger.** Our own bundles used open
  minimums (`version="1.7.32"` = `[1.7.32,∞)`) and survived slf4j 2.0; Felix Health
  Check's `[1.7,2.0)` did not. When a dependency forces a major bump, the bundles
  that break are the ones with strict upper bounds.

- **When a dependency tries to renegotiate the whole platform, question the
  requirement.** Instead of migrating logging to host Tika, we parsed formats
  directly (PDFBox-via-JCL for PDF, raw OOXML-zip reading for Office) → 3 bundles
  instead of a platform-wide slf4j migration. Stop and re-plan on a cascade.

- **Manifest-first Tycho: the hand-written `OSGI-INF/*.xml` wins, not `@Component`.**
  SCR never calls an `@Activate` method unless the XML carries `activate="activate"`.
  Existing components in this repo omit it (their activate() is dead logging) — don't
  copy that when activation logic matters.

- **`java [JVM-opts] -jar app.jar [program-args]`: `-D` flags AFTER `-jar` are
  ignored by the JVM.** `run.sh` appended `"$@"` after `-jar`, so `-Drag.docs.dir`
  (and `-Dopenrouter.api.key`) never became system properties. JVM args must precede
  `-jar`.

- **Verify at the cheapest gate first, then upward.** Resolver error → build → unit
  logic (offline) → runtime activation → end-to-end against a mock endpoint. Each
  caught a distinct class of problem; a green build proved none of the runtime wiring.
