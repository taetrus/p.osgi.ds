package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.service.command.CommandProcessor;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * <h2>Tier 2: a real Gogo command in a running framework</h2>
 *
 * <p>
 * {@code ShellTest} mocks Gogo. This test uses the real Gogo runtime. It registers a service
 * with {@code osgi.command.*} properties, runs the command through {@link Shell} and reads the
 * result from the {@link OutputHub}. The Gogo runtime is in the test runtime through an
 * {@code extraRequirements} entry in the pom, because the bundle imports Gogo as optional.
 * </p>
 */
public class ShellIT {

    /** The command service. Gogo calls the method {@code hello}. */
    public static class Hello {
        public String hello() {
            return "hi-from-gogo";
        }
    }

    @Test
    void runsARealGogoCommand() throws Exception {
        BundleContext context = FrameworkUtil.getBundle(Shell.class).getBundleContext();
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("osgi.command.scope", "termtest");
        props.put("osgi.command.function", new String[] { "hello" });
        ServiceRegistration<Object> reg = context.registerService(Object.class, new Hello(), props);
        ServiceReference<CommandProcessor> ref = context.getServiceReference(CommandProcessor.class);
        assertNotNull(ref);
        OutputHub hub = new OutputHub(100);
        Shell shell = new Shell(hub, "osgi> ");
        try {
            shell.bind(context.getService(ref));
            assertTrue(shell.submit("termtest:hello"));
            long end = System.nanoTime() + 5_000_000_000L;
            boolean hi = false;
            boolean done = false;
            while (System.nanoTime() < end && !done) {
                for (OutputHub.Line l : hub.since(0).lines) {
                    if (l.type.equals("out") && l.text.contains("hi-from-gogo")) {
                        hi = true;
                    }
                    if (l.type.equals("done") && hi) {
                        done = true;
                    }
                }
                Thread.sleep(20);
            }
            assertTrue(hi);
            assertTrue(done);
        } finally {
            shell.close();
            context.ungetService(ref);
            reg.unregister();
        }
    }
}
