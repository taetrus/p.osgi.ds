package com.kk.pde.ds.webconsole.terminal;

import java.io.PrintStream;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Starts the capture of System.out and System.err at bundle start, before the DS component.
 * Thus the buffer also has the output from before the WebConsole is ready.
 */
public class Activator implements BundleActivator {

    static final String SCROLLBACK_PROPERTY = "terminal.scrollback.lines";
    static final int DEFAULT_SCROLLBACK = 50_000;

    static final String THREAD_IO = "org.apache.felix.service.threadio.ThreadIO";
    static final String GOGO_FIRST_WARNING = "Terminal: this bundle started after the Gogo runtime."
            + " Printed output stops showing after the first command."
            + " Give com.kk.pde.ds.webconsole.terminal a lower start level than"
            + " org.apache.felix.gogo.runtime, for example 1.";

    private StdCapture capture;

    @Override
    public void start(BundleContext context) {
        OutputHub hub = new OutputHub(scrollback(context.getProperty(SCROLLBACK_PROPERTY)));
        capture = new StdCapture(hub, 200);
        capture.install();
        warnIfGogoFirst(context, hub, capture.originalErr());
        TerminalRuntime.set(new TerminalRuntime(context, hub, capture));
    }

    /**
     * Warns when the Gogo runtime started first. The check uses the service name as a string, so
     * the bundle needs no import. The text goes to the saved original err and to the hub.
     */
    static void warnIfGogoFirst(BundleContext context, OutputHub hub, PrintStream originalErr) {
        if (context.getServiceReference(THREAD_IO) == null) {
            return;
        }
        originalErr.println(GOGO_FIRST_WARNING);
        hub.append("err", "\u001b[31m" + GOGO_FIRST_WARNING + "\u001b[0m");
    }

    @Override
    public void stop(BundleContext context) {
        capture.uninstall();
        TerminalRuntime.set(null);
        capture = null;
    }

    // An absent, invalid or non-positive value gives the default.
    private static int scrollback(String value) {
        if (value == null) {
            return DEFAULT_SCROLLBACK;
        }
        try {
            int lines = Integer.parseInt(value.trim());
            return lines > 0 ? lines : DEFAULT_SCROLLBACK;
        } catch (NumberFormatException e) {
            return DEFAULT_SCROLLBACK;
        }
    }
}
