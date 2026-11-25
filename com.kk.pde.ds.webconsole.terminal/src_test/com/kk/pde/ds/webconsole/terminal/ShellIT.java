package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
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
 * <p>
 * The same rule applies to {@link Shell#unbind}: a command that stops the Gogo runtime makes SCR
 * call it on the thread of that command. A deferred close has a bound
 * ({@code Shell.DEFERRED_CLOSE_MILLIS}). A command that blocks after the close is interrupted
 * after the bound. The test sets a short bound through {@code deferredCloseMillis}.
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

    /**
     * The command service. It closes the shell and then blocks, as {@code stop <this bundle>}
     * followed by a long command does. The test releases the latch at the end.
     */
    public static class Blocker {
        static volatile Shell shell;
        static final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        static final java.util.concurrent.CountDownLatch interrupted = new java.util.concurrent.CountDownLatch(1);

        public String closeandblock() {
            shell.close();
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return "blocked";
        }
    }

    /** The command service. It unbinds the processor, as SCR does when a command stops Gogo. */
    public static class Unbinder {
        static volatile Shell shell;
        static volatile Object processor;
        static final AtomicReference<String> outcome = new AtomicReference<String>();

        public String unbindgogo() {
            shell.unbind(processor);
            try {
                Thread.sleep(200);
                outcome.set("not interrupted");
            } catch (InterruptedException e) {
                outcome.set("interrupted");
            }
            return "unbound";
        }
    }

    /** The command service. It unbinds the processor and then blocks. The test closes the shell. */
    public static class UnbindBlocker {
        static volatile Shell shell;
        static volatile Object processor;
        static final java.util.concurrent.CountDownLatch unbound = new java.util.concurrent.CountDownLatch(1);
        static final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        static final java.util.concurrent.CountDownLatch interrupted = new java.util.concurrent.CountDownLatch(1);

        // Ignores an interrupt and blocks until the test releases it (at most 10 s).
        public String unbindandblock() {
            shell.unbind(processor);
            unbound.countDown();
            long end = System.nanoTime() + 10_000_000_000L;
            while (System.nanoTime() < end && release.getCount() > 0) {
                try {
                    release.await(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            }
            return "blocked";
        }
    }

    private static Set<Thread> threads(String name) {
        Set<Thread> found = new HashSet<Thread>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && name.equals(t.getName())) {
                found.add(t);
            }
        }
        return found;
    }

    /**
     * A deferred close has a bound. The command closes the shell and then blocks. With a bound of
     * 500 ms, the forced close interrupts the command, and the worker of this shell and the close
     * thread end. Other shells of the test runtime (the plugin component) also have a worker
     * called {@code terminal-shell}, so the test finds the worker that its own shell started.
     */
    @Test
    void deferredCloseIsBounded() throws Exception {
        BundleContext context = FrameworkUtil.getBundle(Shell.class).getBundleContext();
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("osgi.command.scope", "termtest");
        props.put("osgi.command.function", new String[] { "closeandblock" });
        ServiceRegistration<Object> reg = context.registerService(Object.class, new Blocker(), props);
        ServiceReference<CommandProcessor> ref = context.getServiceReference(CommandProcessor.class);
        OutputHub hub = new OutputHub(100);
        Set<Thread> before = threads("terminal-shell");
        Shell shell = new Shell(hub, "osgi> ");
        Set<Thread> workers = threads("terminal-shell");
        workers.removeAll(before);
        assertEquals(1, workers.size(), "the new worker");
        Thread worker = workers.iterator().next();
        shell.deferredCloseMillis(500);
        Blocker.shell = shell;
        try {
            shell.bind(context.getService(ref));
            assertTrue(shell.submit("termtest:closeandblock"));
            assertTrue(Blocker.interrupted.await(3, java.util.concurrent.TimeUnit.SECONDS),
                    "the forced close interrupts the command");
            worker.join(3000);
            assertFalse(worker.isAlive(), "the worker ends");
            long end = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < end && !threads("terminal-shell-close").isEmpty()) {
                Thread.sleep(20);
            }
            assertTrue(threads("terminal-shell-close").isEmpty(), "the close thread ends");
            assertFalse(shell.available());
        } finally {
            Blocker.release.countDown();
            shell.close();
            context.ungetService(ref);
            reg.unregister();
        }
    }

    /**
     * {@code stop <Gogo runtime>} from the page makes SCR call {@link Shell#unbind} on the thread
     * of that command. The session closes after the command, and nothing interrupts the command.
     */
    @Test
    void unbindFromTheRunningCommandDoesNotInterruptIt() throws Exception {
        BundleContext context = FrameworkUtil.getBundle(Shell.class).getBundleContext();
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("osgi.command.scope", "termtest");
        props.put("osgi.command.function", new String[] { "unbindgogo" });
        ServiceRegistration<Object> reg = context.registerService(Object.class, new Unbinder(), props);
        ServiceReference<CommandProcessor> ref = context.getServiceReference(CommandProcessor.class);
        OutputHub hub = new OutputHub(100);
        Shell shell = new Shell(hub, "osgi> ");
        Unbinder.shell = shell;
        try {
            Unbinder.processor = context.getService(ref);
            shell.bind(Unbinder.processor);
            assertTrue(shell.submit("termtest:unbindgogo"));
            long end = System.nanoTime() + 5_000_000_000L;
            boolean done = false;
            while (System.nanoTime() < end && !done) {
                for (OutputHub.Line l : hub.since(0).lines) {
                    done |= l.type.equals("done");
                }
                Thread.sleep(20);
            }
            assertTrue(done);
            assertEquals("not interrupted", Unbinder.outcome.get());
            assertFalse(shell.available(), "the worker closes the session after the command");
            shell.bind(Unbinder.processor);
            assertTrue(shell.available(), "a new bind gives a new session");
        } finally {
            shell.close();
            context.ungetService(ref);
            reg.unregister();
        }
    }

    /**
     * An unbind from the running command waits for the command. A close from outside (the
     * component deactivates) does not wait: it closes that session at once and ends the close
     * thread. The command ignores the interrupt and blocks on, so only the close itself can end
     * the close thread. The test keeps the default bound of 30 s, longer than its 3 s deadline.
     */
    @Test
    void closeFromOutsideEndsAPendingDeferredClose() throws Exception {
        BundleContext context = FrameworkUtil.getBundle(Shell.class).getBundleContext();
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("osgi.command.scope", "termtest");
        props.put("osgi.command.function", new String[] { "unbindandblock" });
        ServiceRegistration<Object> reg = context.registerService(Object.class, new UnbindBlocker(), props);
        ServiceReference<CommandProcessor> ref = context.getServiceReference(CommandProcessor.class);
        OutputHub hub = new OutputHub(100);
        Shell shell = new Shell(hub, "osgi> ");
        UnbindBlocker.shell = shell;
        try {
            UnbindBlocker.processor = context.getService(ref);
            shell.bind(UnbindBlocker.processor);
            assertTrue(shell.submit("termtest:unbindandblock"));
            assertTrue(UnbindBlocker.unbound.await(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, threads("terminal-shell-close").size(), "the deferral is pending");
            shell.close();
            assertTrue(UnbindBlocker.interrupted.await(3, java.util.concurrent.TimeUnit.SECONDS),
                    "the close from outside closes the pending session");
            long end = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < end && !threads("terminal-shell-close").isEmpty()) {
                Thread.sleep(20);
            }
            assertTrue(threads("terminal-shell-close").isEmpty(), "the close thread ends");
        } finally {
            UnbindBlocker.release.countDown();
            shell.close();
            context.ungetService(ref);
            reg.unregister();
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
