package com.kk.pde.ds.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import com.kk.pde.ds.api.IGreet;

/**
 * <h2>Tier 2: tests that need a running OSGi framework</h2>
 *
 * <p>
 * Every {@code *Test} class in this module runs in a plain JVM: it creates the object
 * under test with {@code new} and never touches OSGi. That covers the <em>logic</em>. What
 * it cannot cover is the <em>wiring</em> — whether this bundle resolves, whether Felix SCR
 * reads the {@code OSGI-INF/*.xml} descriptors, activates the components and registers
 * the services with the properties the rest of the system expects. A typo in a manifest
 * or a wrong service property compiles fine and passes every unit test.
 * </p>
 * <p>
 * Classes named {@code *IT} run under {@code tycho-surefire:plugin-test}: Tycho assembles a
 * throwaway Equinox from the target platform (this bundle, everything it requires, JUnit,
 * and Felix SCR because the bundle declares {@code Service-Component}), generates a test
 * fragment for this bundle from the compiled test classes, boots the framework and runs
 * these methods inside it. So {@link FrameworkUtil#getBundle(Class)} works, and the
 * services below were registered by SCR, not by the test.
 * </p>
 * <p>
 * Keep this tier small. Each class here costs a framework boot; put everything that can
 * be tested with {@code new} into a {@code *Test} instead.
 * </p>
 */
public class GreetServiceIT {

    private final BundleContext context = FrameworkUtil.getBundle(Greet.class).getBundleContext();

    /** The most basic in-framework fact: the bundle under test resolved and is running. */
    @Test
    void bundleIsActive() {
        Bundle bundle = FrameworkUtil.getBundle(Greet.class);

        assertEquals("com.kk.pde.ds.imp", bundle.getSymbolicName());
        assertEquals(Bundle.ACTIVE, bundle.getState());
    }

    /**
     * SCR registered {@link IGreet}, and the registered object is this bundle's
     * {@link Greet}. Nothing in this test created a {@code Greet}; if it is there, the
     * component descriptor and the service annotation are correct.
     */
    @Test
    void scrRegistersIGreet() {
        ServiceReference<IGreet> ref = context.getServiceReference(IGreet.class);
        assertNotNull(ref, "IGreet was not registered — check OSGI-INF/*.xml and @Component");

        IGreet service = context.getService(ref);
        try {
            assertTrue(service instanceof Greet, "expected Greet, got " + service.getClass());
        } finally {
            context.ungetService(ref);
        }
    }

    /**
     * The health check's {@code @Reference private IGreet greetService} was satisfied by
     * SCR — the real injection that {@code GreetHealthCheckTest} imitates with reflection
     * and mocks. If the reference were unsatisfied, SCR would not have registered the
     * component at all, so finding it and getting {@code OK} proves the wiring end to end.
     */
    @Test
    void scrWiresGreetHealthCheck() throws Exception {
        ServiceReference<HealthCheck> ref = context.getServiceReferences(HealthCheck.class,
                "(" + HealthCheck.NAME + "=Greet Service Health Check)").iterator().next();
        assertNotNull(ref);

        Result result = context.getService(ref).execute();
        context.ungetService(ref);

        assertTrue(result.isOk(), "health check reported " + result.getStatus());
    }
}
