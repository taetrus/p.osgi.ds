package com.kk.pde.ds.webconsole.terminal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Converter;
import org.apache.felix.service.command.Job;

/**
 * Runs Gogo commands in one shared session. One daemon worker thread runs the queued lines in
 * order. Output goes to the {@link OutputHub}. The worker lives as long as the shell, so it
 * survives an unbind and a new bind of the command processor.
 *
 * <p>
 * {@link #bind} and {@link #unbind} take {@code Object}, so {@link TerminalPlugin} names no Gogo
 * type. This class can load in an application with no Gogo: its Gogo-typed code runs only after
 * a bind.
 * </p>
 */
public final class Shell {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final OutputHub hub;
    private final String prompt;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
    private final Thread worker;
    private final LineAssembler outAssembler;
    private final LineAssembler errAssembler;

    private CommandProcessor processor;
    private volatile CommandSession session;
    private volatile boolean closed;

    public Shell(OutputHub hub, String prompt) {
        this.hub = hub;
        this.prompt = prompt;
        this.outAssembler = assembler("out");
        this.errAssembler = assembler("err");
        this.worker = new Thread(new Runnable() {
            @Override
            public void run() {
                work();
            }
        }, "terminal-shell");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Creates the one session from a Gogo {@code CommandProcessor}. An old session closes first. */
    public synchronized void bind(Object newProcessor) {
        closeSession();
        CommandProcessor gogo = (CommandProcessor) newProcessor;
        processor = gogo;
        session = gogo.createSession(new ByteArrayInputStream(new byte[0]),
                stream(outAssembler), stream(errAssembler));
    }

    /** Closes the session. A processor that is not the bound one changes nothing. */
    public synchronized void unbind(Object oldProcessor) {
        if (oldProcessor != processor) {
            return;
        }
        closeSession();
    }

    public boolean available() {
        return session != null;
    }

    /** Queues the line. The worker echoes it when it runs. It does not wait for the command. */
    public boolean submit(String line) {
        if (closed || session == null) {
            return false;
        }
        queue.add(line);
        return true;
    }

    /**
     * Closes the session and stops the worker. Lines still in the queue are dropped and get no
     * done line, because the component is deactivating.
     *
     * <p>
     * A command of this session can stop this bundle, for example {@code update <id>} of this
     * bundle. Then SCR calls this method on the Gogo thread of that command. A session close
     * interrupts the threads of the session ({@code shutdownNow}). The interrupted update stops
     * the bundle but does not start it again: the bundle stays INSTALLED (product run,
     * 2026-11-25). Thus, in that case, the worker closes the session after the command returns,
     * and nothing interrupts the command. {@code ShellIT} checks this.
     * </p>
     */
    public synchronized void close() {
        closed = true;
        queue.clear();
        if (session != null && calledFromOwnCommand()) {
            return; // run() closes the session when the command returns
        }
        closeSession();
        worker.interrupt();
    }

    // Gogo types only. The caller makes sure that a session exists, thus that Gogo is present.
    private boolean calledFromOwnCommand() {
        Job job = Job.Utils.current();
        return job != null && job.session() == session;
    }

    private void closeSession() {
        CommandSession old = session;
        session = null;
        processor = null;
        if (old != null) {
            old.close();
        }
    }

    private void work() {
        while (!closed) {
            String line;
            try {
                line = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            run(line);
        }
    }

    private void run(String line) {
        hub.append("cmd", TextSanitizer.clean(prompt + line));
        CommandSession current = session;
        if (current == null) {
            hub.append("err", "Shell not available");
            hub.append("done", "");
            return;
        }
        Throwable fatal = null;
        try {
            Object result = current.execute(line);
            if (result != null) {
                emitLines("out", String.valueOf(current.format(result, Converter.INSPECT)));
            }
        } catch (Throwable t) {
            String message = t.getClass().getName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            hub.append("err", "\u001b[31m" + TextSanitizer.clean(message) + "\u001b[0m");
            if (t instanceof OutOfMemoryError || t instanceof InternalError) {
                fatal = t;
            }
        } finally {
            outAssembler.flushPartial();
            errAssembler.flushPartial();
            if (closed) {
                synchronized (this) {
                    closeSession(); // close() was called from this command
                }
            }
            hub.append("done", "");
        }
        if (fatal instanceof Error) {
            throw (Error) fatal;
        }
    }

    // A text that ends with a line break gives no empty line after it.
    private void emitLines(String type, String text) {
        String[] lines = text.split("\r?\n", -1);
        int count = lines.length > 1 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        for (int i = 0; i < count; i++) {
            hub.append(type, TextSanitizer.colorByLevel(TextSanitizer.clean(lines[i])));
        }
    }

    private LineAssembler assembler(final String type) {
        return new LineAssembler(UTF8, new LineAssembler.Sink() {
            @Override
            public void line(String text) {
                hub.append(type, TextSanitizer.colorByLevel(TextSanitizer.clean(text)));
            }
        });
    }

    // The stream encodes with UTF-8 and the assembler decodes with UTF-8. The bytes go only from
    // this stream to the assembler, so the platform charset has no effect, and all characters
    // survive.
    private static PrintStream stream(final LineAssembler assembler) {
        OutputStream bytes = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[] { (byte) b }, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                assembler.write(b, off, len);
            }
        };
        try {
            return new PrintStream(bytes, true, UTF8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // each JVM supports UTF-8
        }
    }
}
