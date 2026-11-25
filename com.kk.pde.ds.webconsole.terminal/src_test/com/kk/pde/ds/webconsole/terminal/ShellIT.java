package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>
 * The second test shows why a mock is not enough for one case. Real Gogo runs a command on a
 * thread of the session, not on the worker. A command such as {@code update <id>} of this bundle
 * makes SCR call {@link Shell#close()} on that thread. The test command calls {@code close()}
 * itself and then sleeps. An interrupt would end the sleep early.
 * </p>
 */
public class ShellIT {

    /** The command service. Gogo calls the method {@code hello}. */
    public static class Hello {
        public String hello() {
            return "hi-from-gogo";
        }
    }

    /** The command service. It closes the shell, as SCR does when a command stops this bundle. */
    public static class Closer {
        static volatile Shell shell;
        static final AtomicReference<String> outcome = new AtomicReference<String>();

        public String closeshell() {
            shell.close();
            try {
                Thread.sleep(200);
                outcome.set("not interrupted");
            } catch (InterruptedException e) {
                outcome.set("interrupted");
            }
            return "closed";
        }
    }

    @Test
    void closeFromTheRunningCommandDoesNotInterruptIt() throws Exception {
        BundleContext context = FrameworkUtil.getBundle(Shell.class).getBundleContext();
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("osgi.command.scope", "termtest");
        props.put("osgi.command.function", new String[] { "closeshell" });
        ServiceRegistration<Object> reg = context.registerService(Object.class, new Closer(), props);
        ServiceReference<CommandProcessor> ref = context.getServiceReference(CommandProcessor.class);
        OutputHub hub = new OutputHub(100);
        Shell shell = new Shell(hub, "osgi> ");
        Closer.shell = shell;
        try {
            shell.bind(context.getService(ref));
            assertTrue(shell.submit("termtest:closeshell"));
            long end = System.nanoTime() + 5_000_000_000L;
            boolean done = false;
            while (System.nanoTime() < end && !done) {
                for (OutputHub.Line l : hub.since(0).lines) {
                    done |= l.type.equals("done");
                }
                Thread.sleep(20);
            }
            assertTrue(done);
            assertEquals("not interrupted", Closer.outcome.get());
            assertFalse(shell.available(), "the worker closes the session after the command");
        } finally {
            shell.close();
            context.ungetService(ref);
            reg.unregister();
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
