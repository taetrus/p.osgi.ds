package com.kk.pde.ds.webconsole.terminal;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replaces {@code System.out} and {@code System.err} with streams that write to the original
 * stream and to the {@link OutputHub}. It never writes to the live {@code System.out} or
 * {@code System.err}, so it cannot call itself.
 */
public final class StdCapture {

    private final OutputHub hub;
    private final long partialFlushMillis;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private Tee teeOut;
    private Tee teeErr;
    private PrintStream streamOut;
    private PrintStream streamErr;
    private Thread flusher;

    public StdCapture(OutputHub hub, long partialFlushMillis) {
        this.hub = hub;
        this.partialFlushMillis = partialFlushMillis;
    }

    /** Replaces System.out and System.err. */
    public synchronized void install() {
        if (flusher != null) {
            return;
        }
        originalOut = System.out;
        originalErr = System.err;
        AtomicBoolean reported = new AtomicBoolean();
        teeOut = new Tee(originalOut, assembler("out"), originalErr, reported);
        teeErr = new Tee(originalErr, assembler("err"), originalErr, reported);
        streamOut = printStream(teeOut);
        streamErr = printStream(teeErr);
        System.setOut(streamOut);
        System.setErr(streamErr);
        flusher = new Thread(this::flushLoop, "terminal-flush");
        flusher.setDaemon(true);
        flusher.start();
    }

    /**
     * Puts the originals back if System.out and System.err are still its own streams. In every
     * case the tee then writes only to the original stream and no longer feeds the hub.
     */
    public synchronized void uninstall() {
        if (flusher == null) {
            return;
        }
        if (System.out == streamOut) {
            System.setOut(originalOut);
        }
        if (System.err == streamErr) {
            System.setErr(originalErr);
        }
        teeOut.detach();
        teeErr.detach();
        flusher.interrupt();
        try {
            flusher.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flusher = null;
    }

    /** The System.err from before {@link #install()}. */
    public PrintStream originalErr() {
        return originalErr;
    }

    private LineAssembler assembler(final String type) {
        return new LineAssembler(StandardCharsets.UTF_8,
                text -> hub.append(type, TextSanitizer.colorByLevel(TextSanitizer.clean(text))));
    }

    private static PrintStream printStream(OutputStream out) {
        try {
            return new PrintStream(out, true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // every JVM has UTF-8
        }
    }

    // Gives out a partial line (for example a prompt) when no byte came for one interval.
    // Thus a line that comes in two writes (text, then the line end) is not cut.
    private void flushLoop() {
        long lastOut = -1;
        long lastErr = -1;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(partialFlushMillis);
            } catch (InterruptedException e) {
                return;
            }
            lastOut = teeOut.flushIfIdle(lastOut);
            lastErr = teeErr.flushIfIdle(lastErr);
        }
    }

    /**
     * Writes to the original stream first, then to the assembler while it is attached. It is
     * static, so a detached tee that a different stream still wraps keeps no reference to the
     * hub.
     */
    private static final class Tee extends OutputStream {
        private final PrintStream original;
        private final PrintStream reportTo;
        private final AtomicBoolean reported;
        private volatile LineAssembler assembler;
        private volatile long writes;

        Tee(PrintStream original, LineAssembler assembler, PrintStream reportTo, AtomicBoolean reported) {
            this.original = original;
            this.assembler = assembler;
            this.reportTo = reportTo;
            this.reported = reported;
        }

        @Override
        public void write(int b) {
            write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            original.write(b, off, len);
            LineAssembler a = assembler;
            if (a == null) {
                return;
            }
            writes++;
            try {
                a.write(b, off, len);
            } catch (RuntimeException e) {
                reportOnce(e);
            }
        }

        @Override
        public void flush() {
            original.flush();
        }

        void detach() {
            assembler = null;
        }

        private void reportOnce(RuntimeException e) {
            if (reported.compareAndSet(false, true)) {
                reportTo.println("terminal: the capture of the output failed: " + e);
            }
        }

        // Flushes the partial line if no write came since the previous call. Returns the
        // write count to give to the next call.
        long flushIfIdle(long previousWrites) {
            long now = writes;
            LineAssembler a = assembler;
            if (now == previousWrites && a != null) {
                try {
                    a.flushPartial();
                } catch (RuntimeException e) {
                    reportOnce(e);
                }
            }
            return now;
        }
    }
}
