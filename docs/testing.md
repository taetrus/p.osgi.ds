# How to Add Tests and Do Tests

This document has three parts:

- Part A tells you how to make a decision about which tests to add.
- Part B tells you how to add the tests to a bundle.
- Part C tells you how to do the tests and read the results.

The text obeys ASD-STE100 (Simplified Technical English). Names of tools, files, commands,
classes and build phases are technical names. The verbs *compile*, *resolve*, *build* and
*mock* are technical verbs.

## Basic Data

A bundle keeps its tests in the folder `src_test/`. The class name selects the tier.

| Tier | Class name | Where the tests operate | Speed |
|------|-----------|-------------------------|-------|
| Tier 1 | `*Test` | A plain JVM. There is no OSGi framework. | Fast |
| Tier 2 | `*IT` | A live Equinox framework that Tycho assembles. | Slow |

---

## Part A: Make a Decision About Which Tests to Add

### A.1 When to add a test

Add a test in each of these conditions:

- You add new behavior to a bundle.
- You change behavior that has no test.
- You correct a defect. Write the test first. Make sure that the test is unsuccessful
  before the correction and successful after the correction.

### A.2 Select the tier

Answer these questions in sequence. Stop at the first answer "yes".

1. Can you make the object with `new` and call its methods?
   If yes, write a tier 1 test.
2. Is the object a DS component, and do you examine only its state?
   If yes, write a tier 1 test. Call the `@Activate` method directly in the test.
3. Does the object use a different service through a `@Reference` field?
   If yes, write a tier 1 test. Use Mockito to mock the service.
4. Must the test show bundle resolution, component activation, DS injection, or service
   properties?
   If yes, write a tier 2 test.

If the answer to each question is "no", a test is not necessary.

**Note:** Most tests are tier 1 tests. A tier 2 test starts a framework and uses more time.
Do not write a tier 2 test for behavior that a tier 1 test can show.

### A.3 Select the type of test

Find the type of your code in this table. Then read the model class before you write the
test.

| Your code | Tests to write | Tier | Model class |
|-----------|----------------|------|-------------|
| A method that calculates a result | The usual input first. Then the limits: zero, one, the maximum, incorrect input. | 1 | `DockLayoutTest` |
| A parser or a formatter | A round trip. Special characters. Incorrect input. Input that an attacker makes. | 1 | `JsonTest` |
| A value object that goes to a different JVM | `equals` and `hashCode`. A `Serializable` round trip. | 1 | `SpikeValueObjectsTest` |
| A DS component that has state | Call `@Activate` in `@BeforeEach`. Then examine the state. | 1 | `CatalogServiceImplTest` |
| A component that calls a different service | Mock the service. Examine the result and the calls. Make the service throw an exception. | 1 | `GreetHealthCheckTest` |
| A component that calls services in a sequence | Use `ArgumentCaptor`, `InOrder` or `spy()`. | 1 | `MasterAppTest` |
| Swing code | Examine the components without a display. Do not examine how the window looks. | 1 | `MasterAppTest` |
| A component that SCR must register as a service | The bundle is active. SCR registers the service. DS supplies the reference. | 2 | `GreetServiceIT` |
| A service that must have specified properties | SCR calls `@Activate`. The service has the properties. | 2 | `CatalogServiceIT` |

The smallest test in the project is `GreetTest`. Read it first if JUnit 5 is new to you.

### A.4 Tests that are not necessary

Do not write these tests:

- A test for a method that only gets or sets a field.
- A test for the behavior of a library or of the OSGi framework.
- A tier 2 test for a bundle that contains only an API. Such a bundle registers no service.
- A tier 2 test that uses only `new`. Write a tier 1 test.
- A second test that examines the same behavior as a test that you have.

### A.5 How many tests to write

For each behavior, write these tests:

1. One test for the usual condition.
2. One test for each limit of the input.
3. One test for each error that the code must find.

Write one behavior in each test method. Give the method a name that tells the behavior, for
example `scrRegistersIGreet`.

---

## Part B: Add the Tests

### B.1 Add a tier 1 test to a bundle

Do steps 1 thru 4 only one time for each bundle.

1. Make the folder `src_test/` in the bundle.
2. Add this entry to the file `.classpath` of the bundle, before the entry
   `kind="output"`:

   ```xml
   <classpathentry kind="src" output="bin_test" path="src_test">
       <attributes>
           <attribute name="test" value="true"/>
       </attributes>
   </classpathentry>
   ```

3. Add the test dependencies to the `pom.xml` of the bundle. Do not write versions. The
   parent `pom.xml` controls the versions.

   ```xml
   <dependencies>
       <dependency>
           <groupId>org.junit.jupiter</groupId>
           <artifactId>junit-jupiter</artifactId>
       </dependency>
   </dependencies>
   ```

4. Add `maven-surefire-plugin` to the `pom.xml` of the bundle:

   ```xml
   <build>
       <plugins>
           <plugin>
               <groupId>org.apache.maven.plugins</groupId>
               <artifactId>maven-surefire-plugin</artifactId>
           </plugin>
       </plugins>
   </build>
   ```

5. Make a class with the suffix `Test` in `src_test/`. Use the same package as the class
   that you examine.

   ```java
   package com.kk.pde.ds.imp;

   import static org.junit.jupiter.api.Assertions.assertEquals;

   import org.junit.jupiter.api.Test;

   public class GreetTest {

       @Test
       void greetingReturnsHelloWorld() {
           assertEquals("Hello world!", new Greet().greeting());
       }
   }
   ```

6. Do the tests of the bundle (refer to paragraph C.3).

**Note:** The file `com.kk.pde.ds.mcp.api/pom.xml` is a complete example of a tier 1
bundle.

### B.2 Add Mockito to a tier 1 test

1. Add these dependencies to the `pom.xml` of the bundle, without versions:

   - `org.junit.platform:junit-platform-launcher`
   - `org.mockito:mockito-core`
   - `org.mockito:mockito-junit-jupiter`
   - `net.bytebuddy:byte-buddy`
   - `net.bytebuddy:byte-buddy-agent`

2. Put `@ExtendWith(MockitoExtension.class)` on the test class.
3. Put `@Mock` on a field for each service that you mock.
4. Put `@InjectMocks` on a field for the object that you examine.

**Note:** `@InjectMocks` also sets a private `@Reference` field. Refer to
`GreetHealthCheckTest`.

### B.3 Add a tier 2 test to a bundle

Do this procedure only if paragraph A.2 gives the answer "tier 2".

1. Do steps 1 thru 4 of paragraph B.1.
2. Add the dependency `org.osgi:org.osgi.framework` to the `pom.xml`, without a version.
3. Add `tycho-surefire-plugin` to the `<build><plugins>` section of the `pom.xml`:

   ```xml
   <plugin>
       <groupId>org.eclipse.tycho</groupId>
       <artifactId>tycho-surefire-plugin</artifactId>
   </plugin>
   ```

4. Copy the `target-platform-configuration` block from `com.kk.pde.ds.imp/pom.xml` into
   the same section.
5. Make a class with the suffix `IT` in `src_test/`:

   ```java
   package com.kk.pde.ds.imp;

   import static org.junit.jupiter.api.Assertions.assertEquals;
   import static org.junit.jupiter.api.Assertions.assertNotNull;

   import org.junit.jupiter.api.Test;
   import org.osgi.framework.Bundle;
   import org.osgi.framework.BundleContext;
   import org.osgi.framework.FrameworkUtil;
   import org.osgi.framework.ServiceReference;

   import com.kk.pde.ds.api.IGreet;

   public class GreetServiceIT {

       private final BundleContext context = FrameworkUtil.getBundle(Greet.class).getBundleContext();

       @Test
       void bundleIsActive() {
           assertEquals(Bundle.ACTIVE, FrameworkUtil.getBundle(Greet.class).getState());
       }

       @Test
       void scrRegistersIGreet() {
           ServiceReference<IGreet> ref = context.getServiceReference(IGreet.class);
           assertNotNull(ref, "IGreet was not registered");
       }
   }
   ```

6. Do the tests of the bundle (refer to paragraph C.3).

**Note:** The block in step 4 does two things. It stops the check of the Java version of
the bundle. It also adds Felix SCR to the framework. If SCR is not there, no DS component
starts.

### B.4 Rules for all tests

- Do not put tests in `src/test/java`. The folder is in the source root `src/`, and Tycho
  compiles the tests into the bundle.
- Do not add the folder `src_test/` to `build.properties`.
- Do not add a test library to the target platform or to a manifest.
- Do not add a test library to `feature.xml` or to a product file.
- Do not use Mockito in a tier 2 class. Mockito is not in the Equinox framework.
- If the test makes Swing components, add `<argLine>-Djava.awt.headless=true</argLine>` to
  the configuration of the two plugins. Refer to `com.kk.pde.ds.spike.master/pom.xml`.

---

## Part C: Do the Tests

Do all commands in the project root.

**Note:** Tycho 4 must have JDK 17 or later. CI uses Temurin 21. To get the same result as
CI on macOS, put `JAVA_HOME=$(/usr/libexec/java_home -v 21)` before the command.

### C.1 Do all bundle tests

1. Enter this command:

   ```bash
   mvn clean verify
   ```

2. Make sure that the last lines show `BUILD SUCCESS`.

This command does tier 1 and tier 2. CI uses the same command.

### C.2 Do only tier 1

1. Enter this command:

   ```bash
   mvn test
   ```

This command does not start Equinox.

### C.3 Do the tests of one bundle

1. Find the bundles that the bundle uses through its manifest.
2. Enter the command with the target module, those bundles, and the bundle:

   ```bash
   mvn verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp
   mvn verify -pl com.kk.pde.ds.target,com.kk.pde.ds.mcp.api
   mvn verify -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master
   ```

**Caution:** Do not use `-pl <module> -am`. Maven cannot read OSGi manifest dependencies.
The option `-am` builds only the parent, and the build stops.

### C.4 Do one tier 1 class or one tier 1 method

1. Use the property `-Dtest` with the phase `test`:

   ```bash
   mvn test -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dtest=GreetHealthCheckTest
   mvn test -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master -Dtest='MasterAppTest#spyRunsTheRealServiceAndRecordsCalls'
   ```

2. If the value contains `#`, put the value between quotation marks.

### C.5 Do one tier 2 class

1. Use the property `-Dit.test` with the phase `verify`:

   ```bash
   mvn verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dit.test=GreetServiceIT
   ```

Tier 1 also operates during this command.

### C.6 Do the tests of the migration tool

The tool `tools/pde2tycho` is not a part of the Tycho reactor. The command in paragraph C.1
does not do its tests.

1. Enter this command:

   ```bash
   mvn -f tools/pde2tycho verify
   ```

### C.7 Read the results

The console shows one line for each class and one line for each tier:

```
Tests run: N, Failures: 0, Errors: 0, Skipped: 0
```

Use these lines to find the result. Do not look for the word `ERROR` in the log.

| Tier | Report folder |
|------|---------------|
| Tier 1 | `<bundle>/target/surefire-reports/` |
| Tier 2 | `<bundle>/target/failsafe-reports/` |

These lines in the log are normal:

- `Mockito is currently self-attaching`. Mockito attaches a Java agent.
- `ERROR ... Greet Service Health Check failed`. Three tests in `GreetHealthCheckTest`
  cause this error on purpose.

### C.8 Fault isolation

| Symptom | Cause | Correction |
|---------|-------|------------|
| `requires osgi.bundle ... but it could not be found` after `-pl <module> -am` | Maven cannot read OSGi manifest dependencies. | List the target module and the necessary bundles in `-pl` (refer to paragraph C.3). |
| The new tests do not start. | The class name has no suffix `Test` or `IT`, or the `pom.xml` does not list the plugin. | Correct the class name. Do steps 3 and 4 of paragraph B.1. |
| The compiler cannot find JUnit classes. | The `pom.xml` of the bundle has no test dependency. | Do step 3 of paragraph B.1. |
| A tier 1 test cannot get a bundle or a `BundleContext`. | Tier 1 has no OSGi framework. | Change the suffix of the class to `IT`. Do paragraph B.3. |
| A tier 2 test finds no service. | Felix SCR is not in the framework. | Do step 4 of paragraph B.3. |
| A tier 2 test cannot find Mockito classes. | Mockito is not in the Equinox framework. | Move the test to a `*Test` class. |
| A window opens during the tests. | The JVM has a display. | Add the `argLine` in paragraph B.4. |
| The local result is different from the CI result. | The command `mvn` of Homebrew uses its own JDK when `JAVA_HOME` is not set. | Set `JAVA_HOME` to JDK 21 before the command. |
| Test classes are in a bundle jar. | The tests are in `src/`, or `src_test/` is in `build.properties`. | Move the tests to `src_test/` and remove the entry. |

## Related Documents

| Document | Contents |
|----------|----------|
| `README.md`, section 13 | The tutorial for the test layout and the list of all test classes. |
| `docs/adding-tests-to-a-tycho-project.md` | How to apply the two tiers to a different Tycho project. |
