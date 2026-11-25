package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * <h2>Tier 1: the Gogo shell worker, with a mock CommandSession</h2>
 *
 * <p>
 * {@link Shell} owns one command session and one worker thread. These tests mock
 * {@link CommandProcessor} and {@link CommandSession}, so no Gogo runtime runs. They check the
 * lines that go to the {@link OutputHub}: the echo of the command, the output, the error and the
 * {@code done} line. {@code ShellIT} runs a real Gogo command.
 * </p>
 * <p>
 * The worker is a separate thread, so the tests wait for a line with a short timeout instead of
 * a fixed sleep.
 * </p>
 */
class ShellTest {

    private OutputHub hub;
    private CommandProcessor processor;
    private CommandSession session;
    private Shell shell;

    @BeforeEach
    void setUp() {
        hub = new OutputHub(100);
        processor = mock(CommandProcessor.class);
        session = mock(CommandSession.class);
        when(processor.createSession(any(InputStream.class), any(PrintStream.class), any(PrintStream.class)))
                .thenReturn(session);
        shell = new Shell(hub, "osgi> ");
    }

    @AfterEach
    void tearDown() {
        shell.close();
    }

    private boolean waitForType(String type) throws InterruptedException {
        long end = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < end) {
            for (OutputHub.Line l : hub.since(0).lines) {
                if (l.type.equals(type)) {
                    return true;
                }
            }
            Thread.sleep(10);
        }
        return false;
    }

    @Test
    void notAvailableBeforeBind() {
        assertFalse(shell.available());
        assertFalse(shell.submit("ss"));
        assertTrue(hub.since(0).lines.isEmpty());
    }

    @Test
    void submitEchoesThePromptAndTheCommand() throws Exception {
        shell.bind(processor);
        assertTrue(shell.submit("ss"));
        assertTrue(waitForType("done"));
        OutputHub.Line first = hub.since(0).lines.get(0);
        assertEquals("cmd", first.type);
        assertEquals("osgi> ss", first.text);
    }

    @Test
    void echoShowsWhenTheCommandRuns() throws Exception {
        final java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(session.execute("a")).thenAnswer(new org.mockito.stubbing.Answer<Object>() {
            @Override
            public Object answer(org.mockito.invocation.InvocationOnMock inv) throws Exception {
                started.countDown();
                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return null;
            }
        });
        shell.bind(processor);
        shell.submit("a");
        shell.submit("b");
        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
        for (OutputHub.Line l : hub.since(0).lines) {
            assertFalse(l.text.equals("osgi> b"), "echo of b before a finished");
        }
        release.countDown();
        long end = System.nanoTime() + 2_000_000_000L;
        List<OutputHub.Line> lines = hub.since(0).lines;
        while (System.nanoTime() < end && lines.size() < 4) {
            Thread.sleep(10);
            lines = hub.since(0).lines;
        }
        assertEquals(4, lines.size());
        assertEquals("osgi> a", lines.get(0).text);
        assertEquals("done", lines.get(1).type);
        assertEquals("osgi> b", lines.get(2).text);
        assertEquals("done", lines.get(3).type);
    }

    @Test
    void runsCommandsInOrder() throws Exception {
        shell.bind(processor);
        shell.submit("a");
        shell.submit("b");
        shell.submit("c");
        long end = System.nanoTime() + 2_000_000_000L;
        int done = 0;
        while (System.nanoTime() < end && done < 3) {
            done = 0;
            for (OutputHub.Line l : hub.since(0).lines) {
                if (l.type.equals("done")) {
                    done++;
                }
            }
            Thread.sleep(10);
        }
        InOrder order = inOrder(session);
        order.verify(session).execute("a");
        order.verify(session).execute("b");
        order.verify(session).execute("c");
    }

    @Test
    void everyCommandEndsWithDone() throws Exception {
        shell.bind(processor);
        shell.submit("a");
        assertTrue(waitForType("done"));
    }

    @Test
    void exceptionBecomesRedErrLineAndSessionStaysOpen() throws Exception {
        when(session.execute("bad")).thenThrow(new IllegalArgumentException("nope"));
        shell.bind(processor);
        shell.submit("bad");
        assertTrue(waitForType("done"));
        boolean found = false;
        for (OutputHub.Line l : hub.since(0).lines) {
            if (l.type.equals("err") && l.text.contains("nope") && l.text.contains("\u001b[31m")) {
                found = true;
            }
        }
        assertTrue(found);
        shell.submit("a");
        Thread.sleep(200);
        verify(session).execute("a");
    }

    @Test
    void errorBecomesRedErrLineAndWorkerSurvives() throws Exception {
        when(session.execute("bad")).thenThrow(new NoClassDefFoundError("x/Y"));
        shell.bind(processor);
        shell.submit("bad");
        assertTrue(waitForType("done"));
        List<OutputHub.Line> lines = hub.since(0).lines;
        int err = -1;
        int done = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).type.equals("err") && lines.get(i).text.contains("NoClassDefFoundError")) {
                err = i;
            }
            if (lines.get(i).type.equals("done")) {
                done = i;
            }
        }
        assertTrue(err >= 0 && err < done);
        shell.submit("a");
        verify(session, timeout(2000)).execute("a");
    }

    @Test
    void unbindMakesShellNotAvailable() {
        shell.bind(processor);
        assertTrue(shell.available());
        shell.unbind(processor);
        assertFalse(shell.available());
        verify(session).close();
    }

    @Test
    void closeStopsWorker() throws Exception {
        shell.bind(processor);
        shell.close();
        long end = System.nanoTime() + 2_000_000_000L;
        boolean alive = true;
        while (System.nanoTime() < end && alive) {
            alive = false;
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.isAlive() && "terminal-shell".equals(t.getName())) {
                    alive = true;
                }
            }
            Thread.sleep(10);
        }
        assertFalse(alive);
    }

    /** Output with no line end shows before the done line. */
    @Test
    void partialOutputShowsBeforeDone() throws Exception {
        ArgumentCaptor<PrintStream> out = ArgumentCaptor.forClass(PrintStream.class);
        shell.bind(processor);
        verify(processor).createSession(any(InputStream.class), out.capture(), any(PrintStream.class));
        when(session.execute("p")).thenAnswer(inv -> {
            out.getValue().print("no newline");
            return null;
        });
        shell.submit("p");
        assertTrue(waitForType("done"));
        List<OutputHub.Line> lines = hub.since(0).lines;
        int partial = -1;
        int done = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).type.equals("out") && lines.get(i).text.equals("no newline")) {
                partial = i;
            }
            if (lines.get(i).type.equals("done")) {
                done = i;
            }
        }
        assertTrue(partial >= 0 && partial < done);
    }
}
