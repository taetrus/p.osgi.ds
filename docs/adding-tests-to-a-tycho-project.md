# How to Add Tests to a Tycho or PDE Project

This guide shows the test layout of this repository. Apply it to a different (larger) Tycho
project, one bundle at a time. We verified the layout on these versions:

- Tycho 4.0.13
- JUnit 5.12 and Mockito 5.14
- Equinox 3.23 (the target pins `org.eclipse.osgi` 3.23.0)
- JDK 17 or later for the build (CI uses Temurin 21)

Copy from these two example bundles:

- `com.kk.pde.ds.imp` has the two tiers.
- `com.kk.pde.ds.mcp.api` has tier 1 only.

## Summary of the layout

- The tests are in the bundle that they examine, in the folder `src_test/`.
- The `.classpath` file of the bundle marks `src_test/` with `test="true"`.
- The class name selects the tier.
- A `*Test` class operates in a plain JVM. `maven-surefire-plugin` runs it in the phase
  `test`. It is fast and has no OSGi framework.
- A `*IT` class operates in a live Equinox framework. `tycho-surefire:plugin-test` runs it
  in the phase `integration-test`.
- Use `*IT` only for behavior that only a framework can show. Examples: the bundle
  resolves, Declarative Services activates the components, and the services have the
  correct properties.
- The test libraries are usual Maven dependencies with the scope `test`. The parent pom
  manages them.
- No test item goes into a shipped jar, feature or product.

---

## 0. Before you start: three questions about the target project

| Question | Why it is important | Answer for this repository |
|----------|---------------------|----------------------------|
| Do the bundles use the PDE layout (`build.properties`: `source.. = src/`) or the Maven layout (`src/main/java`)? | With `source.. = src/`, **`src/test/java` is in the production root, and Tycho compiles the tests into the bundle**. Use a sibling folder (`src_test/`) that has the mark `test="true"`. With `src/main/java`, you can use `src/test/java`. | PDE layout → `src_test/` |
| What is the `Bundle-RequiredExecutionEnvironment` (BREE) of the bundles? | `plugin-test` resolves its runtime with the BREE of the bundle. The Tycho test harness (`org.eclipse.core.runtime` and related bundles) needs `JavaSE-17`. If the bundles declare `JavaSE-1.8` or `JavaSE-11`, tier 2 needs an EE override (section 3). If they declare 17 or later, do not add the override. | `JavaSE-1.8` → override is necessary |
| Is the target platform a `.target` file with Maven locations, or p2 update sites? | The test libraries come from Maven in the two conditions (section 1b). Tier 2 must resolve the **Equinox launcher**. Add it to a Maven location. Or, make sure that the p2 site contains `org.eclipse.equinox.launcher`. Each Eclipse release repository contains it. | Maven locations |

Also examine the JDK that Maven forks. Use `mvn -v`. Homebrew Maven ships its own JDK.
Thus the Maven JDK can be different from the `java -version` result. In that condition,
test libraries that change bytecode, for example ByteBuddy, must support the newer
class-file version. Pin the ByteBuddy version explicitly (section 1b).

---

## 1. Parent pom: do this one time

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

**Note:** With this setting, Tycho writes a `.tycho-consumer-pom.xml` file into each module
folder. Add the file name to `.gitignore`.

### 1b. Manage the versions of the test libraries

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

### 1c. Manage the two plugins

The parent declares the executions too. Thus each bundle only lists the plugin to opt in.

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

`plugin-test` starts its runtime through `org.eclipse.equinox.launcher`. In a target with
Maven locations, add the launcher next to the test harness:

```xml
<location includeDependencyDepth="infinite" includeDependencyScopes="compile" missingManifest="error" type="Maven">
  <dependencies>
    <dependency><groupId>org.eclipse.platform</groupId><artifactId>org.eclipse.core.runtime</artifactId><version>3.33.0</version><type>jar</type></dependency>
    <dependency><groupId>org.eclipse.platform</groupId><artifactId>org.eclipse.equinox.launcher</artifactId><version>1.6.1000</version><type>jar</type></dependency>
  </dependencies>
</location>
```

1. Select the launcher version from the same Eclipse release as your Equinox.
2. With a p2-site target, do not add the launcher. The p2 site already contains it.
3. The old project can have JUnit or Mockito as target-platform locations for
   `eclipse-test-plugin` fragments. In that condition, delete those locations. They are
   pom dependencies now.

---

## 2. Tier 1 for each bundle (each bundle that you test)

1. **Make the folder `src_test/`** with the same package structure as `src/`.
   With the same package, the tests can use package-private members directly.
2. **Declare the folder in `.classpath`.** If the bundle has no `.classpath` file, make one.

   ```xml
   <classpathentry kind="src" output="bin_test" path="src_test">
     <attributes>
       <attribute name="test" value="true"/>
     </attributes>
   </classpathentry>
   ```

   Tycho reads `test="true"`. It then compiles the folder in the phase `test-compile`, into
   `target/test-classes`. It does not compile the folder with the bundle.
   **Do not** add `src_test` to `build.properties`. If you add it, the tests go into the jar.
   Add `bin_test/` to `.gitignore`.
3. **In the `pom.xml` of the bundle**, add the test dependencies without versions. Then
   list the plugin to opt in:

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
4. **Write a `*Test` class.** Use usual JUnit 5. Make the class under test with `new`.
   - A DS component is only a class. Call its `@Activate` method yourself in
     `@BeforeEach`. Refer to `CatalogServiceImplTest`.
   - Inject the collaborators through the bind method.
   - For field injection, use reflection or `@InjectMocks`. Refer to
     `GreetHealthCheckTest`.
5. Enter `mvn test -pl <target-module>,<upstream bundles…>,<this bundle>`. Refer to
   section 4.

**Note:** The test compilation uses the compiler settings of the bundle. Here, they are
Java 8 source and target. ecj with Java 8 compliance reads the Java 11 class files of
Mockito correctly. Only the *test source* must stay in the language level of the bundle.
If that limit is a problem, give the `testCompile` execution of `tycho-compiler-plugin` its
own `<release>`.

---

## 3. Tier 2 for each bundle

Add tier 2 only to bundles that have DS components or services to examine.

Add this to the same `pom.xml`:

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

The bundle can make Swing or AWT components. Or SCR can activate a component that opens
windows. In these conditions, do these two steps:

1. Add `<configuration><argLine>-Djava.awt.headless=true</argLine></configuration>` to
   **both** plugins.
2. Give the component a headless guard. Refer to `MasterApp.start()`.

Then write a `*IT` class. This template comes from `GreetServiceIT`:

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

Keep this tier small. Each bundle with a `*IT` class adds one Equinox start to each build.
If a `*IT` class only calls `new`, change it to a `*Test` class.

---

## 4. Run the tests

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

**Note:** This repository runs these goals through the Maven Wrapper `./mvnw` (Maven
3.9.16). Tycho 4.0.13 does not start on Maven 3.10.

Reports:

- Tier 1 writes its reports to `<bundle>/target/surefire-reports/`.
- Tier 2 writes its reports to `<bundle>/target/failsafe-reports/`. Tycho uses the failsafe
  layout.

Tycho assembles the tier-2 runtime in `<bundle>/target/work/`:

- `configuration/config.ini` lists each bundle that Tycho installed.
- `configuration/*.log` contains the Equinox error log. Read it when the start fails with
  "process returned error code 13".

---

## 5. Troubleshooting: the errors in the sequence that you get them

| Symptom | Cause | Correction |
|---------|-------|------------|
| `tycho-compiler:compile` fails on a test class ("import org.junit cannot be resolved"). The **main** compilation sees the tests. | The test folder is in the source root of `build.properties` (`src/test/java` under `source.. = src/`). | Use the sibling folder `src_test/` and the `.classpath` attribute `test="true"`. |
| The tests compile but do not run. The log has no `surefire:test` line. | The pom declares surefire without an execution. | Add the `unit-tests` execution in `pluginManagement` (1c). |
| `The import org.osgi.framework cannot be resolved` in a `*IT` class | The test code has no manifest to import from. | Add the `org.osgi.framework` test dependency. |
| `You requested to install 'osgi.bundle; org.eclipse.equinox.launcher 0.0.0' but it could not be found` | `plugin-test` starts through the launcher. | Add the launcher to the target platform (1d). |
| `org.eclipse.core.jobs … requires Execution Environment … (version=17) but the current resolution context uses [a.jre.javase 1.8.0]` | Tycho resolved the runtime with the Java 8 BREE of the bundle. | Set `resolveWithExecutionEnvironmentConstraints=false` (section 3). |
| `junit-jupiter-engine … requires 'org.junit.platform.launcher…' but it could not be found` | The aggregate `junit-jupiter` does not contain the launcher. | Add the `junit-platform-launcher` test dependency. |
| The process stops with code 13. The Equinox log shows `Could not resolve module: <bundle> … Unresolved requirement: Require-Capability: osgi.extender; … osgi.component` | Tycho did not install SCR. | Add `extraRequirements` for `org.apache.felix.scr` (section 3). Add `bundleStartLevel` (1c). |
| A `*IT` class passes, but service lookups return `null`. | Tycho installed SCR but did not start it. | Add `bundleStartLevel` with `autoStart=true` (1c). |
| `mvn verify -Dtest=SomeTest` also runs `SomeTest` in Equinox. It fails because Mockito is not there. | The two plugins read `-Dtest`. | Bind the Tycho `<test>` to `${it.test}` with a default value that is not empty (1c). |
| ByteBuddy shows `Java 26 is not supported` (or a similar message) during `spy()` or `mock()`. | Local Maven forks a newer JDK than the pinned ByteBuddy knows. | Pin ByteBuddy 1.18 or later explicitly (1b). Or use `JAVA_HOME=<CI JDK> mvn …`. |
| Windows open during the build. | SCR activates a UI component in the tier-2 runtime. | Add `-Djava.awt.headless=true` to the two plugins. Add a headless guard to the component. |
| `Tests run: 0` for a class that has `@Nested` tests | Surefire reports the tests of the outer class in the nested report. | Use the total for the bundle. |

---

## 6. Rules to keep

- **No test item ships.** Do these checks after a full build:
  1. Enter `unzip -l <bundle jar>`. The list must not show `*Test.class` or `*IT.class`.
  2. Enter `find <products> <p2 repo> -iname '*mockito*' -o -iname '*junit*' -o -iname '*byte-buddy*'`.
     The command must show no result.
  3. If a check finds a test item, a test folder is in `build.properties`. Or a dependency
     does not have the scope `test`.
- **The class name tells the tier.** A `*Test` class never uses OSGi. A `*IT` class does
  more than call `new`.
- **Each decision has one location.** The parent declares the versions and the
  executions. The pom of each bundle tells only which tiers it runs and what it needs.

## 7. Eclipse IDE notes

- PDE reads `src_test` from `.classpath` as a source folder. In the Java Build Path
  properties, mark it as a test folder. This writes the attribute `test="true"`.
- With m2e installed, "Update Project" may make `.classpath` again from the pom. If the
  `src_test` entry disappears, add it again. As an alternative, do not enable m2e for that
  project.
- "Run as JUnit Test" works for `*Test` classes.
- `*IT` classes need "Run as JUnit Plug-in Test" (PDE). PDE then starts an Equinox from
  the workspace target. `plugin-test` does the same thing without a UI.

## 8. Alternatives that we examined

The target project can use one of these alternatives:

- **`tycho-surefire:bnd-test`.** It uses the same layout in the bundle. It adds the bnd
  launcher and the OSGi-JUnit5 annotations (`@InjectService`, `@InjectBundleContext`).
  These make service tests easier to write. But you must learn a different launcher.
- **`eclipse-test-plugin` fragments.** This repository used this layout before. Tycho
  still supports it, but the Tycho documentation calls it "legacy". It adds one module for
  each bundle that you test.
- **JUnit tags instead of name suffixes.** Tags are cleaner in principle. But on Tycho
  4.0.13, the documentation of the `plugin-test` parameter `groups` still describes JUnit 4
  behavior. Thus we did not use tags.
