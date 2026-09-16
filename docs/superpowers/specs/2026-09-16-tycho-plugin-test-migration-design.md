# Tests inside the bundles: migrating from `eclipse-test-plugin` fragments to Tycho's recommended layout — design

**Date:** 2026-09-16
**Status:** Implemented on the same day (branch `feat/tycho-plugin-test`). Spike findings are recorded below.
**Supersedes the layout in:** `2026-07-28-mockito-osgi-test-mocking-design.md` (its Mockito teaching content is unchanged).

---

## Goal

This repository is the **reference implementation** for adding tests to a much larger Tycho
project. Its tests must therefore use the layout Tycho recommends today, and the layout
must be easy to copy bundle by bundle into a large PDE-style codebase.

Until now the 48 tests lived in three hand-written `eclipse-test-plugin` fragments
(`com.kk.pde.ds.imp.tests`, `com.kk.pde.ds.mcp.api.tests`, `com.kk.pde.ds.spike.tests`).
The Tycho documentation ([Testing bundles](https://tycho.eclipseprojects.io/doc/latest/TestingBundles.html))
now calls that packaging *legacy* and recommends keeping tests inside the `eclipse-plugin`
module itself, run by `tycho-surefire:plugin-test`, which generates the fragment on the fly.

---

## The four ways Tycho can run tests

| # | Approach | Packaging | Test sources | Runs inside OSGi | Phase |
|---|----------|-----------|--------------|------------------|-------|
| 1 | `maven-surefire-plugin` | `eclipse-plugin` | in the bundle module | no, plain JVM | `test` |
| 2 | `eclipse-test-plugin` fragment (**before**) | separate module, `Fragment-Host` | own module | yes | `integration-test` |
| 3 | `tycho-surefire:plugin-test` (**after**) | `eclipse-plugin` | in the bundle module | yes, Tycho generates the fragment | `integration-test` |
| 4 | `tycho-surefire:bnd-test` | `eclipse-plugin` | in the bundle module | yes, bnd launcher + OSGi-JUnit5 | `integration-test` |

Approaches 2 and 3 are the same idea (a fragment shares the host's classloader); 3 hides the
fragment. The real fork is plain JVM (1) versus in-framework (2/3/4).

---

## Decisions

| Fork | Decision | Why |
|------|----------|-----|
| Test-only dependencies | pom `<scope>test</scope>` dependencies, managed in the parent, admitted by `pomDependencies=consider` | The official Tycho demo (`demo/testing/tycho/samemodule`) does exactly this; it is what any Maven developer expects; the `.target` file no longer carries test libraries |
| Test source folder | `src_test/` per bundle, declared in `.classpath` with `test="true"`; `build.properties` untouched | The hosts use the PDE layout `source.. = src/`, so `src/test/java` would sit *inside* the production root — the spike proved Tycho then compiles tests into the bundle. `src_test` + `test="true"` is the Tycho `osgitest` demo's layout and costs a large PDE project one folder and one `.classpath` line per bundle |
| Tiers | Two: `*Test` → `maven-surefire-plugin` (plain JVM, phase `test`); `*IT` → `plugin-test` (Equinox, phase `integration-test`) | Mirrors Maven's own surefire/failsafe convention; readable from the class name; needs no JUnit tags (the `plugin-test` docs for `groups` still carry JUnit 4 wording on 4.0.13) |
| Which tests go where | **All 48 existing tests are tier 1.** Tier 2 got two new classes (`GreetServiceIT`, `CatalogServiceIT`, 6 tests) | None of the existing tests touches OSGi at run time; the fragment only ever gave them classloader access. Tier 2 must earn its framework boot by asserting what only a framework shows: SCR activated the components and registered the services with the right properties |
| Where the executions live | Parent `pluginManagement` carries both plugins' executions and shared configuration; a bundle opts in by listing the plugin in `<build><plugins>` | A reader of any bundle pom sees which tiers it runs, without copying execution blocks |
| Not migrated to | `bnd-test` | Better OSGi-JUnit5 ergonomics, but a different launcher and a bnd dependency; not the "recommended default" the bigger project will start from |

---

## Layout (after)

```
com.kk.pde.ds.imp/
  src/                      production (build.properties: source.. = src/)
  src_test/                 tests, .classpath: <classpathentry kind="src" path="src_test"> with test="true"
    …/imp/GreetTest.java, GreetHealthCheckTest.java     tier 1 (moved)
    …/imp/GreetServiceIT.java                           tier 2 (new)
  pom.xml                   test deps + surefire + tycho-surefire + target-platform-configuration (tier-2 block)
com.kk.pde.ds.mcp.api/      JsonTest (tier 1); surefire only — an API bundle has nothing to wire
com.kk.pde.ds.spike.master/ DockLayoutTest, SpikeValueObjectsTest, CatalogServiceImplTest, MasterAppTest (tier 1)
                            CatalogServiceIT (tier 2); both tiers run with -Djava.awt.headless=true
com.kk.pde.ds.*.tests/      deleted
```

---

## Spike findings (`com.kk.pde.ds.imp` first, then the other two)

Each of these cost one failed build and is now encoded in a pom comment.

1. **`src/test/java` nests inside `source.. = src/`.** Tycho compiled the IT into the
   bundle (`tycho-compiler:compile`, not `testCompile`). Fixed by the `src_test` layout.
2. **Declaring `maven-surefire-plugin` binds nothing in an `eclipse-plugin` module.** The
   Tycho lifecycle does not bind surefire; an explicit `test` execution is required
   (parent `pluginManagement`).
3. **`plugin-test` boots through `org.eclipse.equinox.launcher`**, which the old fragment
   runtime never needed. Added `org.eclipse.equinox.launcher` 1.6.1000 to the target
   platform's test-harness location.
4. **`plugin-test` resolves the runtime under the host's own BREE (`JavaSE-1.8`)**, and the
   harness closure (`org.eclipse.core.jobs`) requires `JavaSE-17`. This is why the old
   fragments declared `JavaSE-17`. Fixed per tier-2 module with
   `resolveWithExecutionEnvironmentConstraints=false`; the bundle still compiles against
   the Java 8 profile.
5. **`junit-jupiter` (aggregate) does not bring `junit-platform-launcher`**, but the Jupiter
   engine's OSGi manifest requires the launcher capability. Added as a managed test
   dependency.
6. **Felix SCR is not provisioned automatically.** Tycho's packaging adds
   `Require-Capability: osgi.extender=osgi.component` to the built manifest, and the
   Tycho docs say implicitly required bundles like SCR must be added via
   `extraRequirements`. Added per tier-2 module, with `bundleStartLevel` (level 1,
   auto-start) in the managed `tycho-surefire` configuration. The old fragment runtime
   had this for free.
7. **ecj at Java 8 compliance reads Mockito's Java 11 class files without complaint**, so no
   `testCompile` override was needed. The test sources use no Java 9+ syntax.
8. **ByteBuddy resolves to the pinned 1.18.13** on the tier-1 classpath (explicit managed
   dependency wins over Mockito's transitive 1.15.x), so `spy()` keeps working when the
   local Maven forks JDK 26.
9. **Both plugins read the same `-Dtest`.** `mvn verify -Dtest=GreetHealthCheckTest` made
   `plugin-test` run that `*Test` inside Equinox (where Mockito is absent) and fail. Fixed
   by binding Tycho's `<test>` parameter to a separate `it.test` property (Maven's failsafe
   convention) that defaults to `*IT`. It must stay non-empty: an empty value makes Maven
   fall back to the plugin's own `-Dtest` property again. Tier-1 selection is
   `mvn test -Dtest=...`; tier-2 selection is `mvn verify -Dit.test=...`.

Tier-2 reports land in `target/failsafe-reports/` (Tycho reuses the failsafe layout);
tier-1 reports in `target/surefire-reports/`.

---

## Verification

- `mvn clean verify` → BUILD SUCCESS on Temurin 21 (CI) and on the local Homebrew JDK 26.
- Tier 1: 48 tests (imp 7, mcp.api 13, spike.master 28). Tier 2: 6 (GreetServiceIT 3, CatalogServiceIT 3).
- `unzip -l` of the three bundle jars shows no `*Test.class` / `*IT.class`.
- `distribution/target/products/**` and the p2 repository contain no `org.mockito.*`,
  `net.bytebuddy.*`, `org.objenesis` or JUnit bundle.
- `mvn test` runs tier 1 only and boots no Equinox.

---

## Out of scope

- `bnd-test` / OSGi-JUnit5 annotations (`@InjectService`, `@InjectBundleContext`).
- A `rag` test tier (still the strongest mocking subject in the repo; needs no new module now —
  just a `src_test/` folder and a pom opt-in — so it is cheaper than before).
- Rewriting the Mockito tutorial content; only its "how this runs inside OSGi" paragraphs changed.
