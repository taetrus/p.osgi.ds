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

    /** The bound of a deferred close: a command that runs longer is interrupted. */
    static final long DEFERRED_CLOSE_MILLIS = 30_000;

    private CommandProcessor processor;
    private volatile CommandSession session;
    private volatile boolean closed;
    private CommandSession retiring; // closes after its running command, guarded by this
    private Thread closer; // the bounded close of retiring, guarded by this
    private long deferredCloseMillis = DEFERRED_CLOSE_MILLIS; // guarded by this

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

    /**
     * Closes the session now, or after the running command when this method runs inside the
     * session's own command (see {@link #close()}). A processor that is not the bound one changes
     * nothing.
     *
     * <p>
     * A page command such as {@code stop <Gogo runtime>} makes SCR call this method on the Gogo
     * thread of that command. Thus this method uses the same rule as {@link #close()}, and the
     * close of this shell does not interrupt the command. {@code ShellIT} checks this. On the
     * product, that command still ends with an {@code InterruptedException} (product run,
     * 2026-11-25). A probable cause, from the Gogo 1.1.6 bytecode and not tested: Gogo closes all
     * its sessions at its stop.
     * </p>
     */
    public synchronized void unbind(Object oldProcessor) {
        if (oldProcessor != processor) {
            return;
        }
        if (!deferClose()) {
            closeSession();
        }
    }

    /** Sets the bound of a deferred close. For the tests. */
    synchronized void deferredCloseMillis(long millis) {
        deferredCloseMillis = millis;
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
     * Called from outside the session, it closes the session and stops the worker now. Called
     * from the session's own command, it closes the session and stops the worker after that
     * command ends, or after {@link #DEFERRED_CLOSE_MILLIS}, whichever comes first. Lines still in
     * the queue are dropped and get no done line, because the component is deactivating.
     *
     * <p>
     * A command of this session can stop this bundle, for example {@code update <id>} of this
     * bundle. Then SCR calls this method on the Gogo thread of that command. A session close
     * interrupts the threads of the session ({@code shutdownNow}). The interrupted update stops
     * the bundle but does not start it again: the bundle stays INSTALLED (product run,
     * 2026-11-25). Thus, in that case, the worker closes the session after the command returns,
     * and nothing interrupts the command. The bound stops a command that blocks after the close
     * from keeping the old worker, session and class loader alive. {@code ShellIT} checks both.
     * </p>
     */
    public synchronized void close() {
        closed = true;
        queue.clear();
        if (!deferClose()) {
            closeSession();
            closeRetiring(); // an unbind from a running command can still wait for it
            worker.interrupt();
        }
    }

    /**
     * When the current thread runs a command of this session, moves the session to
     * {@code retiring} and starts the bounded close. The worker closes it after the command, or
     * the close thread closes it after the bound. Returns false when the close can happen now.
     */
    private boolean deferClose() {
        if (session == null && retiring == null) {
            return false;
        }
        CommandSession own = ownSession(); // Gogo is present, because a session exists
        if (own == null || (own != session && own != retiring)) {
            return false;
        }
        if (own == session) {
            retiring = session;
            session = null;
            processor = null;
        } else {
            closeSession(); // own is already retiring: close the session bound after that, if any
        }
        if (closer == null) {
            final long millis = deferredCloseMillis;
            closer = new Thread(new Runnable() {
                @Override
                public void run() {
                    forceClose(millis);
                }
            }, "terminal-shell-close");
            closer.setDaemon(true);
            closer.start();
        }
        return true;
    }

    // Gogo types only. The caller makes sure that a session exists, thus that Gogo is present.
    private static CommandSession ownSession() {
        Job job = Job.Utils.current();
        return job != null ? job.session() : null;
    }

    // The close thread. It does nothing when the command ended first (closer is then null or new).
    private void forceClose(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            return; // closeRetiring() ran: the session is closed already
        }
        synchronized (this) {
            if (closer != Thread.currentThread()) {
                return;
            }
            closeRetiring();
            if (closed) {
                worker.interrupt();
            }
        }
    }

    // The worker, after a command of the given session.
    private synchronized void endDeferredClose(CommandSession current) {
        if (retiring != current) {
            return;
        }
        closeRetiring();
    }

    // Closes the retiring session now and ends the close thread. Guarded by this.
    private void closeRetiring() {
        CommandSession old = retiring;
        retiring = null;
        if (old != null) {
            old.close();
        }
        if (closer != null && closer != Thread.currentThread()) {
            closer.interrupt();
        }
        closer = null;
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
            endDeferredClose(current); // close() or unbind() was called from this command
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
