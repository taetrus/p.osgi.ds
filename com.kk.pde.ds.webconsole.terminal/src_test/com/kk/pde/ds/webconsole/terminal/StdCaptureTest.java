package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * <p>
 * {@link StdCapture} replaces {@code System.out} and {@code System.err} with a tee. The tee
 * writes each byte to the original stream first. Then it gives the bytes to a
 * {@link LineAssembler}, which adds each complete line to the {@link OutputHub} with the
 * type {@code out} or {@code err}. A daemon thread gives out a partial line (a prompt) after
 * the flush interval. {@code uninstall} puts the originals back when they are still the
 * tee, and always disconnects the tee from the hub.
 * </p>
 * <p>
 * Each test sets {@code System.out} and {@code System.err} to byte streams, so the test
 * can read what the original stream got. {@code @AfterEach} puts the real streams back.
 * </p>
 */
class StdCaptureTest {

    private PrintStream savedOut;
    private PrintStream savedErr;
    private ByteArrayOutputStream outBytes;
    private ByteArrayOutputStream errBytes;
    private PrintStream fakeOut;
    private PrintStream fakeErr;
    private OutputHub hub;
    private StdCapture capture;

    @BeforeEach
    void setUp() throws Exception {
        savedOut = System.out;
        savedErr = System.err;
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        fakeOut = new PrintStream(outBytes, true, "UTF-8");
        fakeErr = new PrintStream(errBytes, true, "UTF-8");
        System.setOut(fakeOut);
        System.setErr(fakeErr);
        hub = new OutputHub(100);
    }

    @AfterEach
    void tearDown() {
        if (capture != null) {
            capture.uninstall();
        }
        System.setOut(savedOut);
        System.setErr(savedErr);
    }

    private static String text(ByteArrayOutputStream b) {
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void installSendsOutputToOriginalAndToHub() {
        capture = new StdCapture(hub, 1000);
        capture.install();
        System.out.println("hello");
        assertTrue(text(outBytes).contains("hello"));
        List<OutputHub.Line> lines = hub.since(0).lines;
        assertEquals(1, lines.size());
        assertEquals("out", lines.get(0).type);
        assertEquals("hello", lines.get(0).text);
    }

    @Test
    void errGoesToHubWithTypeErr() {
        capture = new StdCapture(hub, 1000);
        capture.install();
        System.err.println("oops");
        assertTrue(text(errBytes).contains("oops"));
        List<OutputHub.Line> lines = hub.since(0).lines;
        assertEquals(1, lines.size());
        assertEquals("err", lines.get(0).type);
        assertEquals("oops", lines.get(0).text);
    }

    @Test
    void partialLineShowsAfterFlushInterval() throws InterruptedException {
        capture = new StdCapture(hub, 50);
        capture.install();
        System.out.print("osgi> ");
        List<OutputHub.Line> lines = hub.await(0, 1000).lines;
        assertEquals(1, lines.size());
        assertEquals("osgi> ", lines.get(0).text);
    }

    @Test
    void uninstallRestoresOriginalStreams() {
        capture = new StdCapture(hub, 1000);
        capture.install();
        capture.uninstall();
        assertSame(fakeOut, System.out);
        assertSame(fakeErr, System.err);
    }

    @Test
    void uninstallLeavesAForeignStream() {
        capture = new StdCapture(hub, 1000);
        capture.install();
        PrintStream tee = System.out;
        PrintStream foreign = new PrintStream(new ByteArrayOutputStream());
        System.setOut(foreign);
        capture.uninstall();
        assertSame(foreign, System.out);

        // The tee stays in use below the foreign stream, but it no longer feeds the hub.
        tee.println("after");
        assertTrue(text(outBytes).contains("after"));
        assertEquals(0, hub.since(0).lines.size());
    }

    @Test
    void controlSequencesAreCleaned() {
        capture = new StdCapture(hub, 1000);
        capture.install();
        System.out.println("a\u001b[2Jb");
        List<OutputHub.Line> lines = hub.since(0).lines;
        assertEquals(1, lines.size());
        assertEquals("ab", lines.get(0).text);
    }
}
