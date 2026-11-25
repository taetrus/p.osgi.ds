package com.kk.pde.ds.webconsole.terminal;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Starts the capture of System.out and System.err at bundle start, before the DS component.
 * Thus the buffer also has the output from before the WebConsole is ready.
 */
public class Activator implements BundleActivator {

    static final String SCROLLBACK_PROPERTY = "terminal.scrollback.lines";
    static final int DEFAULT_SCROLLBACK = 50_000;

    private StdCapture capture;

    @Override
    public void start(BundleContext context) {
        OutputHub hub = new OutputHub(scrollback(context.getProperty(SCROLLBACK_PROPERTY)));
        capture = new StdCapture(hub, 200);
        capture.install();
        TerminalRuntime.set(new TerminalRuntime(context, hub, capture));
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
