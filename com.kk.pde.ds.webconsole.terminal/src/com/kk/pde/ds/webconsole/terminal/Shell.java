package com.kk.pde.ds.webconsole.terminal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Converter;

/**
 * Runs Gogo commands in one shared session. One daemon worker thread runs the queued lines in
 * order. Output goes to the {@link OutputHub}. The worker lives as long as the shell, so it
 * survives an unbind and a new bind of the command processor.
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

    /** Creates the one session. An old session closes first. */
    public synchronized void bind(CommandProcessor newProcessor) {
        closeSession();
        processor = newProcessor;
        session = newProcessor.createSession(new ByteArrayInputStream(new byte[0]),
                stream(outAssembler), stream(errAssembler));
    }

    /** Closes the session. A processor that is not the bound one changes nothing. */
    public synchronized void unbind(CommandProcessor oldProcessor) {
        if (oldProcessor != processor) {
            return;
        }
        closeSession();
    }

    public boolean available() {
        return session != null;
    }

    /** Echoes the line and queues it. It does not wait for the command. */
    public boolean submit(String line) {
        if (closed || session == null) {
            return false;
        }
        hub.append("cmd", prompt + line);
        queue.add(line);
        return true;
    }

    /**
     * Closes the session and stops the worker. Lines still in the queue are dropped and get no
     * done line, because the component is deactivating.
     */
    public synchronized void close() {
        closed = true;
        queue.clear();
        closeSession();
        worker.interrupt();
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
            hub.append("done", "");
        }
        if (fatal instanceof Error) {
            throw (Error) fatal;
        }
    }

    private void emitLines(String type, String text) {
        for (String l : text.split("\r?\n", -1)) {
            hub.append(type, TextSanitizer.colorByLevel(TextSanitizer.clean(l)));
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

    private static PrintStream stream(final LineAssembler assembler) {
        return new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[] { (byte) b }, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                assembler.write(b, off, len);
            }
        }, true);
    }
}
