package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * <h2>Tier 1: the start-order warning of the Activator</h2>
 *
 * <p>
 * Gogo puts its own stream back into {@code System.out} before each command. If this bundle
 * starts after the Gogo runtime, the capture is lost. {@link Activator#warnIfGogoFirst} checks
 * for the {@code ThreadIO} service by name and warns. The tests mock the {@link BundleContext}
 * and give a plain {@link OutputHub} and a byte buffer as the "original err" stream.
 * </p>
 */
class ActivatorTest {

    private static final String THREAD_IO = "org.apache.felix.service.threadio.ThreadIO";

    @Test
    void warnsWhenGogoStartedFirst() {
        BundleContext context = mock(BundleContext.class);
        when(context.getServiceReference(THREAD_IO)).thenReturn(mock(ServiceReference.class));
        OutputHub hub = new OutputHub(10);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Activator.warnIfGogoFirst(context, hub, new PrintStream(bytes, true));

        assertEquals(1, hub.since(0).lines.size());
        OutputHub.Line line = hub.since(0).lines.get(0);
        assertEquals("err", line.type);
        assertTrue(line.text.contains("started after the Gogo runtime"));
        assertTrue(new String(bytes.toByteArray()).contains("started after the Gogo runtime"));
    }

    @Test
    void noWarningWithoutGogo() {
        BundleContext context = mock(BundleContext.class);
        when(context.getServiceReference(THREAD_IO)).thenReturn(null);
        OutputHub hub = new OutputHub(10);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Activator.warnIfGogoFirst(context, hub, new PrintStream(bytes, true));

        assertTrue(hub.since(0).lines.isEmpty());
        assertEquals(0, bytes.size());
    }
}
