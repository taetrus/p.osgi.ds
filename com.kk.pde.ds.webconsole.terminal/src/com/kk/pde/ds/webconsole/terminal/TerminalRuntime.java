package com.kk.pde.ds.webconsole.terminal;

import java.io.PrintStream;

import org.osgi.framework.BundleContext;

/**
 * What the activator owns and the DS component reads: the output buffer, the framework
 * properties and the original System.err.
 */
public final class TerminalRuntime {

    private static volatile TerminalRuntime current;

    private final BundleContext context;
    private final OutputHub hub;
    private final StdCapture capture;

    TerminalRuntime(BundleContext context, OutputHub hub, StdCapture capture) {
        this.context = context;
        this.hub = hub;
        this.capture = capture;
    }

    /** Null before the bundle start and after the bundle stop. */
    public static TerminalRuntime get() {
        return current;
    }

    static void set(TerminalRuntime runtime) {
        current = runtime;
    }

    public OutputHub hub() {
        return hub;
    }

    /** Reads a framework property. */
    public String property(String key, String defaultValue) {
        String value = context.getProperty(key);
        return value != null ? value : defaultValue;
    }

    public PrintStream originalErr() {
        return capture.originalErr();
    }
}
