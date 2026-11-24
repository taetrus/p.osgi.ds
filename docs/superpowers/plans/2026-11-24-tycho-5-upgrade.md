# Tycho 5.0.4 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build this repository, and the builds that `tools/pde2tycho` generates, with Tycho 5.0.4 in place of the end-of-life Tycho 4.0.13. The shipped output does not change.

**Architecture:** One property (`tycho.version`) moves the reactor to Tycho 5. Tycho 5 changes how `plugin-test` builds the tier-2 test runtime, so the parent pom gets two small corrections. The generator constant in `pde2tycho` moves to the same version. The documents then state the Tycho 5 facts (JDK 21, Maven 3.9.9 or later).

**Tech Stack:** Tycho 5.0.4, Maven Wrapper 3.9.16 (no change), JDK 21 for the build, Java 8 bytecode for the output, JUnit 5.12.2, Mockito 5.14.2.

**Spec:** None. The evidence is the probe record below. A probe ran on 2026-11-24 in a scratch copy of commit `b9dd341`.

## Probe record (facts, not assumptions)

| # | Probe | Result |
|---|-------|--------|
| P1 | `./mvnw -Dtycho.version=5.0.4 clean verify`, JDK 21, no other change | FAIL in `com.kk.pde.ds.imp`, goal `plugin-test`: `You requested to install 'java.package; org.junit.platform.suite.api [1.0.0,2.0.0)' but it could not be found` |
| P2 | P1 + test dependencies `junit-platform-suite-api` and `junit-platform-suite-engine` 1.12.2 | Resolves. Then FAIL: `No tests found`. Equinox starts, but the test fragment stays `INSTALLED`. |
| P3 | P2 + `<importAdditionalPackagesOptional>true</importAdditionalPackagesOptional>` on `tycho-surefire-plugin` | BUILD SUCCESS. Tier 1: 7 + 13 + 6 + 28 = 54. Tier 2: 3 + 3 = 6. |
| P4 | Compare the P3 products with a Tycho 4.0.13 build | Same 42 plugin jars in the main product. Same `config.ini`. Same files in each `com.kk.pde.ds.*` jar. All 105 classes have class-file major version 52 (Java 8). The only manifest difference is a new header `Java-Version: 8`. No JUnit, Mockito or ByteBuddy jar in `distribution/target/products` or `distribution/target/repository`. |
| P5 | P3 on JDK 17 | FAIL at Maven start: `UnsupportedClassVersionError: org/eclipse/tycho/p2maven/repository/EclipseTargetDefinitionArtifactHandler ... (class file version 65.0)` |
| P6 | P3 on Homebrew JDK 27.0.1, modules `target`, `api`, `imp` | BUILD SUCCESS |
| P7 | P3 on Maven 3.10.2 with JDK 21, modules `target`, `api`, `imp` | BUILD SUCCESS. Tycho 5.0.4 starts on Maven 3.10. |
| P8 | `pde2tycho` output for the sample workspace (17 projects), built with `-Dtycho.version=4.0.13` and `-Dtycho.version=5.0.4` | BUILD SUCCESS on both. Same 38 plugin jars. The sample has 0 vendored jars, so the `Directory` location was not exercised. |

**Root cause of P1:** the Tycho 5 provider fragment `org.eclipse.tycho.surefire.junit5` imports
`org.junit.platform.suite.api` and `org.junit.platform.suite.engine`. The Tycho 4 fragment
`org.eclipse.tycho.surefire.junit512` did not.

**Root cause of P2:** Tycho generates a test fragment from all classes in `src_test/`. Tycho 4
marked each `Import-Package` entry of this fragment `resolution:=optional`. Tycho 5 makes the
imports mandatory unless `importAdditionalPackagesOptional` is `true`. The tier-1 classes import
`org.mockito`, and Mockito is not in the tier-2 runtime. Thus the fragment cannot resolve.

**New log noise on Tycho 5 (expected):** one line for each target bundle:
`The digest algorithms (sha-1) used to verify osgi.bundle,<name>,<version> have severely compromised security`.

**Design decision for P2:** set `importAdditionalPackagesOptional` to `true`. This gives the
Tycho 4 behavior again with one line. Two alternatives were rejected. To add Mockito and
ByteBuddy to the Equinox runtime makes tier 2 larger and changes a documented rule. To divide
`src_test/` into two source folders changes the reference layout of all bundles.

## Global Constraints

- Tycho version: exactly `5.0.4`.
- The Maven Wrapper stays on Maven `3.9.16`. Do not change `mvnw`, `mvnw.cmd` or `.mvn/wrapper/`.
- `.github/workflows/build.yml` does not change. CI already uses Temurin 21.
- Shipped classes stay Java 8 bytecode (class-file major version 52). `Bundle-RequiredExecutionEnvironment` stays `JavaSE-1.8`.
- Test libraries stay pom test dependencies. Do not add them to `com.kk.pde.ds.target/*.target`. They must not be in `distribution/target/products/**` or `distribution/target/repository/**`.
- Do not change the hand-written `OSGI-INF/*.xml` files.
- Run all builds with `JAVA_HOME=$(/usr/libexec/java_home -v 21)` and `./mvnw`. The Homebrew `mvn` is Maven 3.10.0 on Java 27.
- Test totals must be 54 tier-1 tests and 6 tier-2 tests, with 0 failures and 0 errors.
- Documents in `README.md`, `CLAUDE.md` and `docs/*.md` follow ASD-STE100. Use the `anthropic-skills:asd-ste100` skill. `tools/doccheck/check-docs.sh` must exit 0.
- Do not edit the historical records: `docs/superpowers/specs/**`, the other files in `docs/superpowers/plans/**`, and `docs/*.html`.
- Branch: `feat/tycho-5-upgrade` from `main`. Commit subjects use a scope, for example `chore(tycho): ...`.
- A document must state only what a probe or a step of this plan verified.

## Review Focus

- **A build on JDK 17.** It fails at Maven start with a Guice error and `class file version 65.0`. No pom rule can catch it, because the Tycho extension loads before any plugin runs. Task 3 pins it with a troubleshooting entry.
- **An `*IT` class that uses a package that is not in the tier-2 runtime.** With optional imports, the build resolves and the test fails at run time with `NoClassDefFoundError`. This is the Tycho 4 behavior. Task 3 documents it.
- **A bundle that gets tier-2 tests later.** Without the two suite dependencies, it fails with the P1 error. Task 3 adds the dependencies to the "add tier-2 tests" steps and to the troubleshooting tables.
- **A `pde2tycho` build with vendored jars.** P8 did not exercise the `Directory` location. Task 2 Step 7 builds one.
- **A full build on Maven 3.10.** P7 covered 3 modules only. Task 1 Step 8 runs the full reactor. Its result selects the document wording in Task 3.

---

### Task 1: Move the reactor to Tycho 5.0.4

**Files:**
- Modify: `pom.xml:12` (version), `pom.xml:54-59` (dependencyManagement), `pom.xml:185` (surefire configuration)
- Modify: `com.kk.pde.ds.imp/pom.xml:22-25` (dependencies), `com.kk.pde.ds.imp/pom.xml:64-67` (comment)
- Modify: `com.kk.pde.ds.spike.master/pom.xml` (dependencies, after `junit-platform-launcher`)

**Interfaces:**
- Produces: a parent pom that manages `org.junit.platform:junit-platform-suite-api` and `org.junit.platform:junit-platform-suite-engine`. Task 3 documents these two names.

- [ ] **Step 1: Create the branch and record the Tycho 4 baseline**

```bash
git switch -c feat/tycho-5-upgrade
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw --batch-mode --no-transfer-progress clean verify
B=<scratchpad>/baseline; mkdir -p $B
P=distribution/target/products/com.kk.pde.ds.product/linux/gtk/x86_64
ls $P/plugins | sed -E 's/_1\.0\.0\.[0-9]+\.jar/_Q.jar/' > $B/plugins.txt
sed -E 's/_1\.0\.0\.[0-9]+\.jar/_Q.jar/g' $P/configuration/config.ini | grep -v '^#' > $B/config.ini
for p in ecf.host ecf.consumer spike.master spike.detail; do
  ls distribution/target/products/com.kk.pde.ds.$p.product/linux/gtk/x86_64/plugins \
    | sed -E 's/_1\.0\.0\.[0-9]+\.jar/_Q.jar/' > $B/plugins-$p.txt
done
```

Expected: BUILD SUCCESS. `wc -l $B/plugins.txt` gives 42.

- [ ] **Step 2: Change the version and run the build to see the first failure**

Set `<tycho.version>5.0.4</tycho.version>` in `pom.xml:12`. Run `./mvnw --batch-mode --no-transfer-progress clean verify`.

Expected: FAIL in `com.kk.pde.ds.imp` with `org.junit.platform.suite.api [1.0.0,2.0.0)' but it could not be found`.

- [ ] **Step 3: Add the JUnit suite dependencies**

In `pom.xml`, after the `junit-platform-launcher` entry of `<dependencyManagement>`, add:

```xml
			<!-- Tycho 5 only: its JUnit 5 provider fragment (org.eclipse.tycho.surefire.junit5)
			     imports org.junit.platform.suite.api and .suite.engine, so the in-framework
			     tier cannot resolve without them. No test here uses @Suite. -->
			<dependency>
				<groupId>org.junit.platform</groupId>
				<artifactId>junit-platform-suite-api</artifactId>
				<version>${junit-platform.version}</version>
				<scope>test</scope>
			</dependency>
			<dependency>
				<groupId>org.junit.platform</groupId>
				<artifactId>junit-platform-suite-engine</artifactId>
				<version>${junit-platform.version}</version>
				<scope>test</scope>
			</dependency>
```

In `com.kk.pde.ds.imp/pom.xml` and `com.kk.pde.ds.spike.master/pom.xml`, after the `junit-platform-launcher` dependency, add the same two artifacts with no `<version>` and no `<scope>`. Do not add them to `mcp.api` or `chatbot`. Those bundles have tier 1 only.

- [ ] **Step 4: Run the build to see the second failure**

Run: `./mvnw --batch-mode --no-transfer-progress clean verify`

Expected: FAIL in `com.kk.pde.ds.imp` with `No tests found`, after a `Tests run: 0` line.

- [ ] **Step 5: Make the imports of the test fragment optional**

In `pom.xml`, in the `tycho-surefire-plugin` `<configuration>` of `<pluginManagement>`, after `<failIfNoTests>true</failIfNoTests>`, add:

```xml
						<!-- Tycho builds one test fragment from all of src_test/, tier-1 classes
						     included, and those import Mockito, which is not in the Equinox
						     runtime. Tycho 4 made every import of the fragment optional; Tycho 5
						     does so only on request. Without this the fragment stays INSTALLED
						     and the run ends with "No tests found". -->
						<importAdditionalPackagesOptional>true</importAdditionalPackagesOptional>
```

In `com.kk.pde.ds.imp/pom.xml:66-67`, change the comment text `The test JVM is 17+ anyway.` to `The test JVM is 21+ anyway.`

- [ ] **Step 6: Run the full build and check the test totals**

```bash
./mvnw --batch-mode --no-transfer-progress clean verify 2>&1 | tee <scratchpad>/tycho5.log
grep -E "Tests run:.*Fail" <scratchpad>/tycho5.log | grep -v "Time elapsed"
grep "Tycho Version" <scratchpad>/tycho5.log
```

Expected: BUILD SUCCESS. `Tycho Version:  5.0.4`. Six totals lines: 7, 3, 13, 6, 28, 3. All have `Failures: 0, Errors: 0`. The sum is 54 for tier 1 and 6 for tier 2.

- [ ] **Step 7: Prove that the output did not change**

```bash
B=<scratchpad>/baseline   # the folder from Step 1
P=distribution/target/products/com.kk.pde.ds.product/linux/gtk/x86_64
diff $B/plugins.txt <(ls $P/plugins | sed -E 's/_1\.0\.0\.[0-9]+\.jar/_Q.jar/')
diff $B/config.ini <(sed -E 's/_1\.0\.0\.[0-9]+\.jar/_Q.jar/g' $P/configuration/config.ini | grep -v '^#')
for p in ecf.host ecf.consumer spike.master spike.detail; do
  diff $B/plugins-$p.txt <(ls distribution/target/products/com.kk.pde.ds.$p.product/linux/gtk/x86_64/plugins \
    | sed -E 's/_1\.0\.0\.[0-9]+\.jar/_Q.jar/')
done
ls distribution/target/products/*.tar.gz distribution/target/products/*.zip
find distribution/target/products distribution/target/repository \
  -iname '*junit*' -o -iname '*mockito*' -o -iname '*byte-buddy*' -o -iname '*opentest4j*'
T=<scratchpad>/cls; rm -rf $T; mkdir -p $T
for j in $P/plugins/com.kk.pde.ds.*.jar; do unzip -q -o $j '*.class' -d $T; done
find $T -name '*.class' | while read c; do od -An -j6 -N2 -tu1 "$c" | awk 'NF{print $1*256+$2}'; done | sort | uniq -c
```

Expected: each `diff` prints nothing. `ls` shows the 3 product archives. `find` prints nothing. The last command prints one line: `105 52`.

- [ ] **Step 8: Run the full build on Maven 3.10 and record the result**

```bash
MVN310=$(ls -d ~/.m2/wrapper/dists/apache-maven-3.10.2/*/bin/mvn | head -1)
$MVN310 --batch-mode --no-transfer-progress clean verify 2>&1 | tail -30
```

Expected: BUILD SUCCESS. Write the result (`SUCCESS` or the first `[ERROR]` line) in the commit body of Step 11. Task 3 Step 2 uses it. If the `ls` finds no directory, download `apache-maven-3.10.2-bin.zip` from Maven Central to the scratchpad and use its `bin/mvn`.

- [ ] **Step 9: Start the products**

Run each command from the repository root. Stop each process after the check.

1. ECF pair: start `./distribution/scripts/run-ecf-host.sh`. Then start `./distribution/scripts/run-ecf-consumer.sh`. Expected in the consumer output: `Remote response: Hello, ECF! (served remotely by host)`.
2. Main product: start `./distribution/scripts/run.sh` (it opens two Swing windows). Run `curl http://localhost:8080/api/greet`. Expected: a JSON object with a `message` field and a `timestamp` field.
3. At the console prompt of the main product, type `ss`. Expected: no `com.kk.pde.ds.*` bundle is in state `INSTALLED`.

- [ ] **Step 10: Run the partial-build commands from `CLAUDE.md`**

```bash
./mvnw test   -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dtest=GreetHealthCheckTest
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dit.test=GreetServiceIT
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master
```

Expected: BUILD SUCCESS for each. The second command reports `Tests run: 3` for tier 2.

- [ ] **Step 11: Commit**

```bash
git add pom.xml com.kk.pde.ds.imp/pom.xml com.kk.pde.ds.spike.master/pom.xml
git commit -m "chore(tycho): upgrade the build to Tycho 5.0.4"
```

The commit body states the two tier-2 corrections and the Step 8 result.

---

### Task 2: Generate Tycho 5.0.4 builds with pde2tycho

**Files:**
- Modify: `tools/pde2tycho/src/main/java/com/kk/pde2tycho/scan/WorkspaceScanner.java:37`
- Modify: `tools/pde2tycho/src/main/java/com/kk/pde2tycho/gen/ReportGenerator.java:23`
- Modify (comments only): `gen/Generator.java:78`, `gen/TargetGenerator.java:48`, `gen/DistributionGenerator.java:64`, `tools/pde2tycho/pde2tycho.sh:2`, `tools/pde2tycho/pde2tycho.bat:2`
- Test: `tools/pde2tycho/src/test/java/com/kk/pde2tycho/gen/GeneratorTest.java`
- Modify (fixture literal `"4.0.13"` to `"5.0.4"`): `gen/GenFixtures.java:29`, `gen/GeneratorTest.java:113`, `gen/PomGeneratorTest.java:68`, `gen/DistributionGeneratorTest.java:94`, `model/InventoryJsonTest.java:28`
- Modify (Javadoc only): `gen/GeneratorTest.java:53`, `gen/TargetGeneratorTest.java:23`

**Interfaces:**
- Produces: `WorkspaceScanner.TYCHO_VERSION = "5.0.4"`. The text `JDK 21+` in `MIGRATION-REPORT.md`. Task 3 documents both.

- [ ] **Step 1: Write the failing test**

In `GeneratorTest`, in the test that reads `MIGRATION-REPORT.md` (near line 49), add:

```java
		String report = Files.readString(out.resolve("MIGRATION-REPORT.md"));
		assertTrue(report.contains("(JDK 21+;"), report);
		assertFalse(report.contains("JDK 17"), report);
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./mvnw -q -f tools/pde2tycho test -Dtest=GeneratorTest`
Expected: FAIL. The report contains `JDK 17+`.

- [ ] **Step 3: Change the constant and the report text**

- `WorkspaceScanner.TYCHO_VERSION` becomes `"5.0.4"`.
- In `ReportGenerator.java:23`, `JDK 17+` becomes `JDK 21+`.
- Replace the fixture literal `"4.0.13"` with `"5.0.4"` in the five test files listed above.

- [ ] **Step 4: Correct the comments**

| Location | New text |
|----------|----------|
| `Generator.java:78` and `GeneratorTest.java:53` | The wrapper pins the Maven version, as this repository does. Tycho 5 needs Maven 3.9.9 or later. |
| `TargetGenerator.java:48` and `TargetGeneratorTest.java:23` | `${project_loc:/<module>}` is the form verified on Tycho 5.0.4. On Tycho 4.0.13 the other forms gave nothing. Write this only after Step 7 passes. |
| `DistributionGenerator.java:64` | `Tycho 4` becomes `Tycho`. |
| `pde2tycho.sh:2` and `pde2tycho.bat:2` | The tool needs JDK 17+ and Maven. A generated Tycho 5 build needs JDK 21+. |

The test name `mavenWrapperPinsAMavenThatTychoStartsOn` and its assertion `/apache-maven-3.9.` stay.

- [ ] **Step 5: Run the tool tests**

Run: `./mvnw --batch-mode --no-transfer-progress -f tools/pde2tycho verify`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 6: Build a generated project end to end**

```bash
S=<scratchpad>; R=$(pwd); rm -rf $S/mig $S/mig.migration.json
./mvnw -q -f tools/pde2tycho -DskipTests package
A=(); for m in mcp.llm chatbot rag ecf.api ecf.host ecf.consumer spike.api spike.master spike.detail; do
  A+=(--add-project $R/com.kk.pde.ds.$m); done
tools/pde2tycho/pde2tycho.sh scan /Users/avalon/dev/workspaces/ws.p.osgi.ds -o $S/mig.migration.json "${A[@]}"
tools/pde2tycho/pde2tycho.sh generate $S/mig.migration.json $S/mig
grep tycho.version $S/mig/pom.xml
(cd $S/mig && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B clean verify | tail -5)
find $S/mig/distribution/target/products -path '*plugins/*.jar' | sed 's|.*/||; s/_1\.0\.0\.[0-9]*\.jar/_Q.jar/' | sort -u | wc -l
```

Expected: `<tycho.version>5.0.4</tycho.version>`. BUILD SUCCESS. The count is 38. `MIGRATION-REPORT.md` contains `JDK 21+`.

- [ ] **Step 7: Verify the `Directory` location on Tycho 5**

In `$S/mig/com.kk.pde.ds.target/`:

1. Make `vendor/plugins/`. Copy `~/.m2/repository/org/apache/felix/org.apache.felix.gogo.shell/1.1.4/org.apache.felix.gogo.shell-1.1.4.jar` into it.
2. In the `.target` file, delete the `<dependency>` element of `org.apache.felix.gogo.shell`.
3. Add `<location path="${project_loc:/com.kk.pde.ds.target}/vendor/plugins" type="Directory"/>` to `<locations>`.
4. Run `./mvnw -B clean verify` in `$S/mig`.

Expected: BUILD SUCCESS, and `find $S/mig/distribution/target/products -name 'org.apache.felix.gogo.shell*'` finds the jar. If the build fails with a missing `org.apache.felix.gogo.shell`, STOP. Tycho 5 does not read the path form that `TargetGenerator` writes. Report the result and make a new plan for `TargetGenerator`.

- [ ] **Step 8: Commit**

```bash
git add tools/pde2tycho
git commit -m "feat(pde2tycho): generate Tycho 5.0.4 builds"
```

---

### Task 3: State the Tycho 5 facts in the documents

**Files:**
- Modify: `CLAUDE.md`, `README.md`, `docs/build.md`, `docs/testing.md`, `docs/adding-tests-to-a-tycho-project.md`, `docs/troubleshooting.md`, `docs/pde-to-tycho-migration.md`, `docs/running.md`, `docs/claude-automation-recommendations.md`
- Modify: `.claude/skills/pde2tycho/SKILL.md:52`, `fatjar/run.sh:3`, `pom.xml:25` (comment)

**Interfaces:**
- Consumes: the Step 8 result of Task 1, the two dependency names from Task 1, and `JDK 21+` from Task 2.

- [ ] **Step 1: Load the `anthropic-skills:asd-ste100` skill**

- [ ] **Step 2: Replace each stale fact**

Find the places with:

```bash
git grep -n -E "4\.0\.13|Tycho 4|JDK 17|17 or newer|17\+|Maven 3\.10|JDK 26|OpenJDK \(26\)" \
  -- . ':!docs/superpowers' ':!*.html' ':!tools/pde2tycho/src'
```

Apply these facts. Use the same words in each file.

| Old fact | New fact |
|----------|----------|
| Tycho 4.0.13 | Tycho 5.0.4 |
| The build needs JDK 17 or later, because Tycho 4 needs it | The build needs JDK 21 or later, because Tycho 5 runs on Java 21. CI uses Temurin 21. |
| The tier-2 test harness needs `JavaSE-17` (`docs/build.md:33`, `docs/testing.md:584`, `docs/adding-tests-to-a-tycho-project.md:39,253`) | No change. This is a fact about the Equinox test harness, not about Tycho. |
| Tycho 4.0.13 does not start on Maven 3.10 | If Task 1 Step 8 gave SUCCESS: "Tycho 5.0.4 needs Maven 3.9.9 or later. The wrapper pins Maven 3.9.16, the version that CI uses." If it failed: keep the sentence, with `5.0.4` and the new error line. |
| Homebrew `mvn` uses OpenJDK 26 (`CLAUDE.md:113`, `pom.xml:25`) | Homebrew `mvn` uses OpenJDK 27 (`mvn -v` on 2026-11-24: Maven 3.10.0, Java 27.0.1) |
| `docs/pde-to-tycho-migration.md:46`: the tool and Tycho need JDK 17 | The tool needs JDK 17 or later. The generated build needs JDK 21 or later. |
| `docs/pde-to-tycho-migration.md:111`: Tycho 4.0.13 reads the other `Directory` paths as empty | We verified `${project_loc:/<module>}/vendor/plugins` on Tycho 5.0.4. On Tycho 4.0.13, the other forms gave empty folders without a message. |
| `docs/adding-tests-to-a-tycho-project.md:402`: on Tycho 4.0.13 the `groups` documentation describes JUnit 4 | On Tycho 5.0.4 it still does ("JUnit 4.8+ only"). Change the version only. |

`docs/ecf-remote-services.md:131` and `distribution/.classpath` do not change.

- [ ] **Step 3: Add the tier-2 requirements of Tycho 5**

1. `CLAUDE.md`, "Add tier-2 tests to a bundle", step 3: add `org.junit.platform:junit-platform-suite-api` and `org.junit.platform:junit-platform-suite-engine` to the list. Add the reason: the Tycho 5 JUnit provider imports the suite packages.
2. `docs/testing.md` B.3 step 2 (line 263): add the same two dependencies. After the note at line 315, add a note: the parent pom sets `importAdditionalPackagesOptional` to `true`, and why (the "Root cause of P2" text of this plan).
3. `docs/adding-tests-to-a-tycho-project.md`:
   - Line 6: `Tycho 5.0.4`. Line 9: `JDK 21 or later`.
   - Parent pom XML (near lines 88 and 133): add the two managed dependencies and the `importAdditionalPackagesOptional` line.
   - Bundle pom XML (near line 239): add the two dependencies.
   - Troubleshooting table (near line 357): add the two rows below.
4. `docs/build.md:125`: the pom snippet shows `5.0.4`.

| Error message | Cause | Fix |
|---------------|-------|-----|
| `You requested to install 'java.package; org.junit.platform.suite.api [1.0.0,2.0.0)' but it could not be found` | The Tycho 5 JUnit provider imports the JUnit suite packages. | Add `junit-platform-suite-api` and `junit-platform-suite-engine` as test dependencies of the bundle. |
| `No tests found`, after `Tests run: 0` | The generated test fragment has a mandatory import that the runtime does not have (for example `org.mockito`). The fragment stays `INSTALLED`. | Set `<importAdditionalPackagesOptional>true</importAdditionalPackagesOptional>` on `tycho-surefire-plugin`. To see the state, add `-Dtycho.printBundles=true`. |

- [ ] **Step 4: Rewrite the Maven entry of `docs/troubleshooting.md`**

If Task 1 Step 8 gave SUCCESS, replace the section `### Maven 3.10 cannot start Tycho` (lines 27-39) with the section below. Change the Contents entry at line 8 to match. Change `docs/build.md:90` to: "The workflow uses the wrapper, so CI and local builds use the same Maven version."

```markdown
### An old JDK cannot start Tycho

**Symptom:** the build stops before the first module with `UnsupportedClassVersionError` and
`class file version 65.0`.

**Cause:** Tycho 5.0.4 runs on Java 21 or later. The JDK that runs Maven is older.

**Fix:**

1. Set `JAVA_HOME` to a JDK 21 or later.
   ```bash
   JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify
   ```
2. The product still runs on Java 8 or later. This requirement is for the build only.
```

- [ ] **Step 5: Add the new log noise**

In `CLAUDE.md` (Testing, "Expected log noise") and in the same list of `docs/testing.md`, add: Tycho 5 prints one `The digest algorithms (sha-1) ... have severely compromised security` warning for each target bundle.

- [ ] **Step 6: Check the documents**

```bash
tools/doccheck/check-docs.sh
git grep -n -E "4\.0\.13|Tycho 4" -- . ':!docs/superpowers' ':!*.html'
```

Expected: the script exits 0 (0 hard STE violations, no broken link or anchor). The `git grep` prints only sentences that describe Tycho 4 in the past tense.

- [ ] **Step 7: Commit**

```bash
git add CLAUDE.md README.md docs .claude/skills/pde2tycho/SKILL.md fatjar/run.sh pom.xml
git commit -m "docs(tycho): state the Tycho 5.0.4 requirements"
```

---

### Task 4: Final verification and pull request

**Files:** none.

- [ ] **Step 1: Run the two CI commands from a clean tree**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress -f tools/pde2tycho verify
tools/doccheck/check-docs.sh
```

Expected: BUILD SUCCESS two times, 54 + 6 tests, and exit 0 from the doc check.

- [ ] **Step 2: Build the fat JAR**

Run: `./mvnw -q -f fatjar/pom.xml clean package && ls -la fatjar/target/osgi-fatjar.jar`
Expected: the jar exists.

- [ ] **Step 3: Push and open the pull request**

Do this step only when the user asks. The PR body lists: the version change, the two tier-2 corrections with their root causes, the probe table P1-P8, and the Step 1 results. Then watch the 3 CI jobs (`build`, `docs`, `pde2tycho-windows`).

## Out of scope

- Compiling against a real Java 8 class library (`~/.m2/toolchains.xml` and `useJDK=BREE`). Tycho 4 and Tycho 5 both compile against the class library of the build JDK. Temurin 8 is installed on this machine, so this is a possible follow-up.
- Tycho 6. It is not released, and it needs Java 25 to run Maven.
- A newer Maven in the wrapper.
