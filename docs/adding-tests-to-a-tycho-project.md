# Adding tests to a Tycho / PDE project — a copy-and-adapt guide

This is the layout used in this repository, written up so it can be applied to another
(larger) Tycho project bundle by bundle. Everything here was verified on Tycho 4.0.13,
JUnit 5.12, Mockito 5.14, Equinox 3.23, building on JDK 17+ (CI: Temurin 21). The
worked example to copy from is `com.kk.pde.ds.imp` (both tiers) and `com.kk.pde.ds.mcp.api`
(tier 1 only).

**The shape in one paragraph.** Tests live inside the bundle they test, in a `src_test/`
folder marked `test="true"` in the bundle's `.classpath`. There are two kinds, told apart
by the class name: `*Test` runs in a plain JVM under `maven-surefire-plugin` (phase `test`,
fast, no OSGi); `*IT` runs inside a live Equinox under `tycho-surefire:plugin-test` (phase
`integration-test`) and is reserved for what only a framework can show — the bundle
resolves, Declarative Services activated the components, services carry the right
properties. Test libraries are ordinary Maven test-scope dependencies managed in the
parent pom. Nothing test-related ever reaches a shipped jar, feature or product.

---

## 0. Before you start: three questions about the target project

| Question | Why it matters | This repo's answer |
|----------|----------------|--------------------|
| Do the bundles use the PDE layout (`build.properties`: `source.. = src/`) or the Maven layout (`src/main/java`)? | With `source.. = src/`, **`src/test/java` nests inside the production root and Tycho compiles the tests into the bundle**. Use a sibling folder (`src_test/`) marked `test="true"`. With `src/main/java` you can use `src/test/java` directly. | PDE layout → `src_test/` |
| What is the bundles' `Bundle-RequiredExecutionEnvironment`? | `plugin-test` resolves its runtime under the bundle's own BREE. The Tycho test harness (`org.eclipse.core.runtime` and friends) needs `JavaSE-17`. If bundles declare `JavaSE-1.8` or `JavaSE-11`, tier 2 needs an EE override (step 4). If they are already 17+, skip it. | `JavaSE-1.8` → override needed |
| Is the target platform a `.target` file with Maven locations, or p2 update sites? | Test libraries come from Maven either way (step 1). The **Equinox launcher** must be resolvable for tier 2: add it to a Maven location, or make sure the p2 site contains `org.eclipse.equinox.launcher` (every Eclipse release repo does). | Maven locations |

Also check the JDK Maven actually forks: `mvn -v`. If it differs from `java -version`
(Homebrew Maven ships its own JDK), bytecode-manipulating test libraries such as
ByteBuddy must support that newer class-file version. Pin ByteBuddy explicitly (step 1).

---

## 1. Parent pom: do this once

### 1a. Let pom dependencies join the target platform

```xml
<plugin>
  <groupId>org.eclipse.tycho</groupId>
  <artifactId>target-platform-configuration</artifactId>
  <version>${tycho.version}</version>
  <configuration>
    <target>…your existing target…</target>
    <!-- Let a module's own pom <dependencies> (test scope) join its target platform.
         Production bundles keep coming from the .target file / p2 sites. -->
    <pomDependencies>consider</pomDependencies>
    …
  </configuration>
</plugin>
```

Side effect to know about: Tycho then writes a `.tycho-consumer-pom.xml` into every module
directory. Add it to `.gitignore`.

### 1b. Manage the test library versions

```xml
<properties>
  <junit.version>5.12.2</junit.version>
  <junit-platform.version>1.12.2</junit-platform.version>
  <mockito.version>5.14.2</mockito.version>
  <bytebuddy.version>1.18.13-jdk5</bytebuddy.version>   <!-- see step 0: pin above Mockito's default -->
  <maven-surefire.version>3.5.6</maven-surefire.version>
  <it.test>*IT</it.test>                                   <!-- see 1c -->
</properties>

<dependencyManagement>
  <dependencies>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>${junit.version}</version><scope>test</scope></dependency>
    <!-- Not a Maven-transitive dep of junit-jupiter, but the Jupiter engine's OSGi manifest
         requires the launcher capability; tier 2 cannot resolve without it. -->
    <dependency><groupId>org.junit.platform</groupId><artifactId>junit-platform-launcher</artifactId><version>${junit-platform.version}</version><scope>test</scope></dependency>
    <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><version>${mockito.version}</version><scope>test</scope></dependency>
    <dependency><groupId>org.mockito</groupId><artifactId>mockito-junit-jupiter</artifactId><version>${mockito.version}</version><scope>test</scope></dependency>
    <dependency><groupId>net.bytebuddy</groupId><artifactId>byte-buddy</artifactId><version>${bytebuddy.version}</version><scope>test</scope></dependency>
    <dependency><groupId>net.bytebuddy</groupId><artifactId>byte-buddy-agent</artifactId><version>${bytebuddy.version}</version><scope>test</scope></dependency>
    <!-- Compile-only for *IT classes (BundleContext, FrameworkUtil). At run time the package
         comes from Equinox itself. -->
    <dependency><groupId>org.osgi</groupId><artifactId>org.osgi.framework</artifactId><version>1.10.0</version><scope>test</scope></dependency>
  </dependencies>
</dependencyManagement>
```

### 1c. Manage the two plugins (executions included, so bundles only opt in)

```xml
<pluginManagement>
  <plugins>
    <!-- Tier 1. Tycho's eclipse-plugin lifecycle does NOT bind surefire by itself, so the
         execution must be explicit. Surefire's defaults include **/*Test.java and leave
         **/*IT.java alone. -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>${maven-surefire.version}</version>
      <executions>
        <execution>
          <id>unit-tests</id>
          <goals><goal>test</goal></goals>
        </execution>
      </executions>
    </plugin>
    <!-- Tier 2. Tycho generates a test fragment for the bundle and runs *IT classes inside
         Equinox; the verify goal fails the build on test failures. -->
    <plugin>
      <groupId>org.eclipse.tycho</groupId>
      <artifactId>tycho-surefire-plugin</artifactId>
      <version>${tycho.version}</version>
      <configuration>
        <!-- Selection is driven by ${it.test}: **/*IT.java by default, narrowed with
             -Dit.test=SomeIT. Keep the property NON-EMPTY: an empty value makes Maven fall
             back to the plugin's own -Dtest, and then a *Test selected for tier 1 is also
             dragged into Equinox (where Mockito is absent). -->
        <test>${it.test}</test>
        <failIfNoTests>true</failIfNoTests>
        <!-- Felix SCR must be started early or no DS component activates. Bundles pull SCR
             into the runtime with an extraRequirement (step 4); this makes it auto-start.
             Use org.eclipse.equinox.ds / org.apache.felix.scr as appropriate. -->
        <bundleStartLevel>
          <bundle><id>org.apache.felix.scr</id><level>1</level><autoStart>true</autoStart></bundle>
        </bundleStartLevel>
      </configuration>
      <executions>
        <execution>
          <id>osgi-integration-tests</id>
          <goals><goal>plugin-test</goal><goal>verify</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</pluginManagement>
```

### 1d. Target platform: the Equinox launcher

`plugin-test` boots its runtime through `org.eclipse.equinox.launcher`. In a Maven-location
target, add it next to the test harness:

```xml
<location includeDependencyDepth="infinite" includeDependencyScopes="compile" missingManifest="error" type="Maven">
  <dependencies>
    <dependency><groupId>org.eclipse.platform</groupId><artifactId>org.eclipse.core.runtime</artifactId><version>3.33.0</version><type>jar</type></dependency>
    <dependency><groupId>org.eclipse.platform</groupId><artifactId>org.eclipse.equinox.launcher</artifactId><version>1.6.1000</version><type>jar</type></dependency>
  </dependencies>
</location>
```

Pick the launcher version from the same Eclipse release as your Equinox. With a p2-site
target this is already there.

If the old project had JUnit/Mockito as target-platform locations for
`eclipse-test-plugin` fragments, delete those locations: they are pom dependencies now.

---

## 2. Per bundle, tier 1 (every bundle you test)

1. **Create `src_test/`** with the same package structure as `src/`. Same package → tests
   see package-private members without any tricks.
2. **Declare it in `.classpath`** (create the file if the bundle has none):

   ```xml
   <classpathentry kind="src" output="bin_test" path="src_test">
     <attributes>
       <attribute name="test" value="true"/>
     </attributes>
   </classpathentry>
   ```

   `test="true"` is what Tycho reads to compile the folder in `test-compile` (into
   `target/test-classes`) instead of with the bundle. **Do not** add `src_test` to
   `build.properties` — that would put it in the jar. Add `bin_test/` to `.gitignore`.
3. **In the bundle's `pom.xml`**, add the test dependencies (no versions) and opt in:

   ```xml
   <dependencies>
     <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId></dependency>
     <!-- only if the tests use Mockito: -->
     <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId></dependency>
     <dependency><groupId>org.mockito</groupId><artifactId>mockito-junit-jupiter</artifactId></dependency>
     <dependency><groupId>net.bytebuddy</groupId><artifactId>byte-buddy</artifactId></dependency>
     <dependency><groupId>net.bytebuddy</groupId><artifactId>byte-buddy-agent</artifactId></dependency>
   </dependencies>
   <build>
     <plugins>
       <plugin>
         <groupId>org.apache.maven.plugins</groupId>
         <artifactId>maven-surefire-plugin</artifactId>
       </plugin>
     </plugins>
   </build>
   ```
4. **Write a `*Test`.** Ordinary JUnit 5; construct the class under test with `new`.
   A DS component is just a class — call its `@Activate` method yourself in
   `@BeforeEach` (see `CatalogServiceImplTest`), and inject collaborators through the
   bind method or, for field injection, reflection or `@InjectMocks`
   (see `GreetHealthCheckTest`).
5. `mvn test -pl <target-module>,<upstream bundles…>,<this bundle>` — see "Running" below.

Compilation runs with the bundle's compiler settings (here: Java 8 source/target). ecj at
Java 8 compliance reads Mockito's Java 11 class files fine; only the *test source* must
stay within the bundle's language level. If that is too restrictive, give the
`tycho-compiler-plugin` `testCompile` execution its own `<release>`.

---

## 3. Per bundle, tier 2 (only bundles with DS components or services worth asserting)

Add to the same `pom.xml`:

```xml
<dependencies>
  …tier-1 deps…
  <dependency><groupId>org.junit.platform</groupId><artifactId>junit-platform-launcher</artifactId></dependency>
  <dependency><groupId>org.osgi</groupId><artifactId>org.osgi.framework</artifactId></dependency>
</dependencies>
<build>
  <plugins>
    …maven-surefire-plugin…
    <plugin>
      <groupId>org.eclipse.tycho</groupId>
      <artifactId>tycho-surefire-plugin</artifactId>
    </plugin>
    <plugin>
      <groupId>org.eclipse.tycho</groupId>
      <artifactId>target-platform-configuration</artifactId>
      <configuration>
        <!-- 1. Only if the bundle's BREE is below JavaSE-17: the harness needs 17; the
                bundle still compiles against its own profile, only the resolver relaxes. -->
        <resolveWithExecutionEnvironmentConstraints>false</resolveWithExecutionEnvironmentConstraints>
        <!-- 2. Tycho's packaging adds Require-Capability osgi.extender=osgi.component to a
                bundle with DS components; nothing in the runtime is *required* to provide
                it, so the resolver must be told to bring the DS implementation. -->
        <dependency-resolution>
          <extraRequirements>
            <requirement>
              <type>eclipse-plugin</type>
              <id>org.apache.felix.scr</id>
              <versionRange>0.0.0</versionRange>
            </requirement>
          </extraRequirements>
        </dependency-resolution>
      </configuration>
    </plugin>
  </plugins>
</build>
```

If the bundle builds Swing/AWT components, or SCR will activate a component that opens
windows, add `<configuration><argLine>-Djava.awt.headless=true</argLine></configuration>` to
**both** plugins and give the component a headless guard (see `MasterApp.start()`).

Then write a `*IT`. Template (from `GreetServiceIT`):

```java
public class MyBundleIT {
    private final BundleContext context =
            FrameworkUtil.getBundle(SomeClassInThisBundle.class).getBundleContext();

    @Test
    void bundleIsActive() {
        Bundle b = FrameworkUtil.getBundle(SomeClassInThisBundle.class);
        assertEquals("my.bundle.symbolic.name", b.getSymbolicName());
        assertEquals(Bundle.ACTIVE, b.getState());
    }

    @Test
    void scrRegistersMyService() {
        ServiceReference<IMyService> ref = context.getServiceReference(IMyService.class);
        assertNotNull(ref, "IMyService not registered — check OSGI-INF/*.xml and @Component");
        IMyService s = context.getService(ref);
        try {
            assertTrue(s instanceof MyServiceImpl);
            assertEquals("expected", ref.getProperty("some.service.property"));
        } finally {
            context.ungetService(ref);
        }
    }
}
```

Keep this tier small. Each bundle with an `*IT` costs one Equinox boot per build. An `*IT`
that only ever calls `new` should be a `*Test`.

---

## 4. Running

```bash
mvn clean verify                 # both tiers (CI)
mvn test                         # tier 1 only; boots no Equinox

# One bundle: list the target module and the bundle's OSGi upstream bundles explicitly.
# -pl <bundle> -am does NOT work: Maven cannot see Import-Package / Require-Bundle edges.
mvn verify -pl <target-module>,<upstream…>,<bundle>

# One class or method
mvn test   -pl … -Dtest=SomeTest                 # tier 1 (surefire's property)
mvn test   -pl … -Dtest='SomeTest#method'
mvn verify -pl … -Dit.test=SomeIT                # tier 2 (the property from step 1c)
```

Reports: tier 1 in `<bundle>/target/surefire-reports/`, tier 2 in
`<bundle>/target/failsafe-reports/` (Tycho reuses the failsafe layout). The tier-2 runtime
is materialised under `<bundle>/target/work/`; its `configuration/config.ini` lists every
bundle that was provisioned, and `configuration/*.log` holds the Equinox error log when the
launch fails with "process returned error code 13".

---

## 5. Troubleshooting: the errors you will meet, in the order you will meet them

| Symptom | Cause | Fix |
|---------|-------|-----|
| `tycho-compiler:compile` fails on a test class ("import org.junit cannot be resolved"), i.e. the **main** compile sees the tests | Test folder is inside the `build.properties` source root (`src/test/java` under `source.. = src/`) | Sibling folder `src_test/` + `.classpath` `test="true"` |
| Tests compile but never run; no `surefire:test` line in the log | Surefire declared without an execution | The `unit-tests` execution in `pluginManagement` (1c) |
| `The import org.osgi.framework cannot be resolved` in a `*IT` | Test code has no manifest to import from | `org.osgi.framework` test dependency |
| `You requested to install 'osgi.bundle; org.eclipse.equinox.launcher 0.0.0' but it could not be found` | `plugin-test` boots via the launcher | Add the launcher to the target platform (1d) |
| `org.eclipse.core.jobs … requires Execution Environment … (version=17) but the current resolution context uses [a.jre.javase 1.8.0]` | Runtime resolved under the bundle's Java 8 BREE | `resolveWithExecutionEnvironmentConstraints=false` (step 3) |
| `junit-jupiter-engine … requires 'org.junit.platform.launcher…' but it could not be found` | The aggregate `junit-jupiter` does not carry the launcher | `junit-platform-launcher` test dependency |
| Process exits with code 13; Equinox log says `Could not resolve module: <bundle> … Unresolved requirement: Require-Capability: osgi.extender; … osgi.component` | SCR not provisioned | `extraRequirements` for `org.apache.felix.scr` (step 3) + `bundleStartLevel` (1c) |
| A `*IT` passes but service lookups return `null` | SCR provisioned but not started | `bundleStartLevel` with `autoStart=true` (1c) |
| `mvn verify -Dtest=SomeTest` runs `SomeTest` inside Equinox too and it errors (Mockito missing) | Both plugins read `-Dtest` | Bind Tycho's `<test>` to `${it.test}` with a non-empty default (1c) |
| `Java 26 is not supported` (or similar) from ByteBuddy during `spy()`/`mock()` | Local Maven forks a newer JDK than the pinned ByteBuddy knows | Pin ByteBuddy ≥ 1.18 explicitly (1b); or `JAVA_HOME=<CI JDK> mvn …` |
| Windows open during the build | SCR activates a UI component in the tier-2 runtime | `-Djava.awt.headless=true` on both plugins + a headless guard in the component |
| `Tests run: 0` for a class that has `@Nested` tests | Surefire reporting quirk: the outer class's tests are credited to the nested report | Trust the per-bundle total |

---

## 6. Guard rails to keep

- **Nothing test-related ships.** After a full build: `unzip -l <bundle jar>` shows no
  `*Test.class`/`*IT.class`; `find <products> <p2 repo> -iname '*mockito*' -o -iname '*junit*' -o -iname '*byte-buddy*'` prints nothing. If it does, a test folder leaked into `build.properties` or a dependency lost its `test` scope.
- **Names carry meaning.** `*Test` never touches OSGi; `*IT` never just calls `new`.
- **One decision per place.** Versions and executions in the parent; each bundle's pom
  says only which tiers it runs and what it needs.

## 7. Eclipse IDE notes

- PDE recognises `src_test` as a source folder from `.classpath` (mark it as a test folder in
  the Java Build Path properties, which writes exactly the `test="true"` attribute).
- With m2e installed, "Update Project" may regenerate `.classpath` from the pom. Check the
  `src_test` entry back in if it disappears, or keep the entry out of m2e's hands by not
  enabling m2e for that project.
- "Run as JUnit Test" works for `*Test` classes. `*IT` classes need "Run as JUnit
  Plug-in Test" (PDE), which launches an Equinox from the workspace target — the same
  thing `plugin-test` does headlessly.

## 8. Alternatives considered, in case the target project wants them

- **`tycho-surefire:bnd-test`** — same "in the bundle" layout, but the bnd launcher and the
  OSGi-JUnit5 annotations (`@InjectService`, `@InjectBundleContext`). Nicer ergonomics for
  service tests; a different launcher to learn.
- **`eclipse-test-plugin` fragments** — the previous layout here; still supported, called
  "legacy" by the Tycho docs. One extra module per tested bundle.
- **JUnit tags instead of name suffixes** — cleaner in principle, but the `plugin-test`
  `groups` parameter still documents JUnit 4 semantics on Tycho 4.0.13, so it was not relied on.
