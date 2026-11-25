package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.UUID;

import javax.servlet.Servlet;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * <h2>Tier 2: the plugin and the capture in a running framework</h2>
 *
 * <p>
 * {@code TerminalPluginTest} calls the servlet methods with mocks. This test runs in Equinox
 * with Felix SCR. It checks two facts that a mock cannot show:
 * </p>
 * <ol>
 * <li>SCR reads the hand-written XML and registers the plugin as a {@link Servlet} service with
 * the property {@code felix.webconsole.label=terminal}. The WebConsole finds plugins by this
 * property.</li>
 * <li>The bundle activator captures {@code System.out}. A line that the test prints arrives in
 * the {@link OutputHub} of {@link TerminalRuntime}.</li>
 * </ol>
 * <p>
 * SCR activates components asynchronously, so the tests wait with a short timeout.
 * </p>
 */
public class TerminalPluginIT {

    @Test
    void pluginIsRegisteredWithLabel() throws Exception {
        BundleContext context = FrameworkUtil.getBundle(TerminalPlugin.class).getBundleContext();
        long end = System.nanoTime() + 5_000_000_000L;
        Collection<ServiceReference<Servlet>> refs;
        do {
            refs = context.getServiceReferences(Servlet.class, "(felix.webconsole.label=terminal)");
            if (!refs.isEmpty()) {
                break;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < end);
        assertTrue(!refs.isEmpty(), "no Servlet service with felix.webconsole.label=terminal");
    }

    @Test
    void printFromTheTestReachesTheHub() throws Exception {
        TerminalRuntime runtime = TerminalRuntime.get();
        assertNotNull(runtime);
        String probe = "probe-" + UUID.randomUUID();
        System.out.println(probe);
        long end = System.nanoTime() + 2_000_000_000L;
        boolean found = false;
        while (!found && System.nanoTime() < end) {
            for (OutputHub.Line l : runtime.hub().since(0).lines) {
                if (l.text.contains(probe)) {
                    found = true;
                }
            }
            if (!found) {
                Thread.sleep(20);
            }
        }
        assertTrue(found, "the line " + probe + " is not in the hub");
    }
}
