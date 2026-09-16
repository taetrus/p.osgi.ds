package com.kk.pde.ds.imp;

// A "static import" pulls a single method into scope so it can be called by its bare
// name. Without this line the assertion below would have to read
// `Assertions.assertEquals(...)`. Test code is mostly assertions, so importing them
// statically is the near-universal convention — it keeps the signal-to-noise high.
import static org.junit.jupiter.api.Assertions.assertEquals;

// @Test is an *annotation*: metadata attached to a method that the test framework
// reads by reflection at run time. It has no effect on the method itself.
import org.junit.jupiter.api.Test;

/**
 * <h2>Start here if you have never written a unit test</h2>
 *
 * <p>
 * A <strong>unit test</strong> is an ordinary Java method that calls a small piece of
 * your code ("the unit") with known inputs and then states what the answer must be. If
 * the answer differs, the test <em>fails</em> and the build stops. That is the whole
 * idea. There is no magic — the framework simply finds these methods and calls them.
 * </p>
 *
 * <h3>The three parts of every test (Arrange - Act - Assert)</h3>
 * <ol>
 * <li><strong>Arrange</strong> — build the objects the test needs.</li>
 * <li><strong>Act</strong> — call the one method you are actually testing.</li>
 * <li><strong>Assert</strong> — state what the result must be.</li>
 * </ol>
 * <p>
 * The single test below is so small that all three steps collapse into one line, but
 * they are still there. The other test classes in this project keep them separate, and
 * once you can see this shape you can read any test in any codebase.
 * </p>
 *
 * <h3>How the framework finds this class</h3>
 * <p>
 * This project uses <strong>JUnit 5</strong> (the packages are called
 * {@code org.junit.jupiter.*} — "Jupiter" is JUnit 5's codename). JUnit scans the
 * compiled classes, collects every method annotated {@code @Test}, and runs each one.
 * Note what is <em>not</em> required: the class needs no {@code main} method, no
 * interface, no registration list, and the test methods need not be {@code public}
 * (JUnit 5 dropped that requirement — package-private, as below, is now the norm).
 * </p>
 *
 * <h3>Each test gets a brand-new object</h3>
 * <p>
 * JUnit creates a <em>fresh instance of this class for every single {@code @Test}
 * method</em>. That is deliberate: it means one test can never leave state behind that
 * corrupts another, and tests can therefore run in any order. Never write a test that
 * depends on another test having run first — the framework gives you no such promise.
 * </p>
 *
 * <h3>Naming</h3>
 * <p>
 * A test method name is documentation, not an identifier you will ever type. Prefer a
 * sentence describing the guaranteed behaviour ({@code greetingReturnsHelloWorld})
 * over a restatement of the method under test ({@code testGreeting}). When a test
 * fails months from now, its name is the first thing you read.
 * </p>
 *
 * <h3>Where this file lives, and where it runs</h3>
 * <p>
 * Tests sit next to the code they test, in the same bundle: this file is in
 * {@code com.kk.pde.ds.imp/src_test/}, and the same package as {@link Greet}. The folder
 * is marked {@code test="true"} in the bundle's {@code .classpath}, which is how Tycho
 * knows to compile it in the test-compile phase and keep it out of the shipped jar
 * ({@code build.properties} never mentions it). Being in the same package is why this
 * test can see package-private members without any special access.
 * </p>
 * <p>
 * This project has <strong>two kinds of test, told apart by the class name</strong>:
 * </p>
 * <ul>
 * <li>{@code *Test} (this file, and most tests) runs in a plain JVM under
 * {@code maven-surefire-plugin} in the {@code test} phase. No OSGi is involved; the
 * object under test is created with {@code new}. Fast, simple, and enough for logic.</li>
 * <li>{@code *IT} (see {@code GreetServiceIT} next to this file) runs
 * <em>inside a live Equinox</em> under {@code tycho-surefire:plugin-test} in the
 * {@code integration-test} phase. Use it only for what a plain JVM cannot show: that
 * the bundle resolves and that Declarative Services really wired the components.</li>
 * </ul>
 * <p>
 * Run everything with {@code mvn clean verify} from the repository root; {@code mvn test}
 * runs only the plain-JVM tier. For just this bundle, list its OSGi upstream modules
 * explicitly — Maven cannot see them —
 * {@code mvn verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp}.
 * </p>
 *
 * <h3>Where to read next</h3>
 * <ul>
 * <li>{@code CatalogServiceImplTest} (in {@code com.kk.pde.ds.spike.master}) — shared
 * setup with {@code @BeforeEach}, and testing an object that holds state.</li>
 * <li>{@code DockLayoutTest} — testing pure calculations, and the edge cases worth
 * covering once the happy path passes.</li>
 * <li>{@code JsonTest} (in {@code com.kk.pde.ds.mcp.api}) — asserting that code
 * <em>throws</em>, and writing regression tests that pin down fixed bugs.</li>
 * <li>{@code GreetHealthCheckTest} — test doubles and Mockito; then
 * {@code GreetServiceIT} — the in-framework tier.</li>
 * </ul>
 */
public class GreetTest {

    /**
     * The simplest possible test: one input, one expected output.
     *
     * <p>
     * {@code assertEquals} takes the arguments in the order
     * <strong>(expected, actual)</strong> — the value you demand first, the value your
     * code produced second. Getting this backwards does not break the test, but it
     * inverts every failure message you will ever read from it ("expected: &lt;Hello
     * world!&gt; but was: &lt;...&gt;" would name the wrong side). Every JUnit
     * assertion that takes two values follows this same order.
     * </p>
     *
     * <p>
     * Note there is no {@code if} and no {@code return}. A test method reports failure
     * by <em>throwing</em>: {@code assertEquals} throws an {@code AssertionFailedError}
     * when the values differ, JUnit catches it and marks the test red. A method that
     * returns normally passed. This is why a test needs no return value.
     * </p>
     *
     * <p>
     * Comparison uses {@code equals()}, not {@code ==}. For {@code String} that
     * distinction matters enormously: {@code ==} asks "are these the same object in
     * memory", {@code equals} asks "do these hold the same characters". A test using
     * {@code ==} on strings can pass by luck (via the JVM's string-literal pool) and
     * then fail the moment the value arrives from a file or a network socket.
     * </p>
     */
    @Test
    void greetingReturnsHelloWorld() {
        // Arrange + Act + Assert, all on one line:
        //   new Greet()          -> arrange
        //   .greeting()          -> act
        //   assertEquals(...)    -> assert
        assertEquals("Hello world!", new Greet().greeting());
    }
}
