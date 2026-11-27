package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.InOrder;

/**
 * <h2>Tier 1: the HTTP contract of the terminal plugin, with a mock request and response</h2>
 *
 * <p>
 * {@link TerminalPlugin} has a package-visible constructor that takes the {@link OutputHub}, the
 * {@link Shell}, the page token and a property lookup. Thus these tests need no OSGi framework
 * and no WebConsole. Mockito mocks {@link HttpServletRequest} and {@link HttpServletResponse}.
 * The tests call {@code doGet}, {@code doPost} and {@code renderContent} directly, with the
 * path info that the WebConsole gives ({@code /terminal/...}).
 * </p>
 * <p>
 * The stream loop runs until a write fails. A stream test gives a response writer that fails
 * on the second flush, and sets a short ping interval. {@code PrintWriter.checkError()} also
 * flushes. Thus the loop writes one batch and ends. Each stream test has {@code @Timeout(10)}, so
 * a loop that does not end fails the test and does not stop the build.
 * {@code TerminalPluginIT} checks the registration in a real framework.
 * </p>
 */
class TerminalPluginTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private OutputHub hub;
    private Shell shell;
    private Map<String, String> props;
    private HttpServletRequest req;
    private HttpServletResponse res;

    @BeforeEach
    void setUp() {
        hub = new OutputHub(100);
        shell = mock(Shell.class);
        when(shell.available()).thenReturn(true);
        when(shell.submit(anyString())).thenReturn(true);
        props = new HashMap<String, String>();
        req = mock(HttpServletRequest.class);
        res = mock(HttpServletResponse.class);
    }

    private TerminalPlugin plugin() {
        TerminalPlugin p = new TerminalPlugin(hub, shell, TOKEN, new TerminalPlugin.Config() {
            @Override
            public String get(String key, String defaultValue) {
                String v = props.get(key);
                return v != null ? v : defaultValue;
            }
        }) {
            private static final long serialVersionUID = 1L;

            // Tier 1 has no bundle jar on the class path. Read the module folder res/.
            @Override
            URL resource(String name) {
                File f = new File("res", name);
                try {
                    return f.isFile() ? f.toURI().toURL() : null;
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        p.pingMillis = 50;
        return p;
    }

    private void post(String token, String body) throws IOException {
        when(req.getPathInfo()).thenReturn("/terminal/exec");
        when(req.getHeader("X-Terminal-Token")).thenReturn(token);
        when(req.getInputStream()).thenReturn(new BodyStream(body.getBytes(StandardCharsets.UTF_8)));
        plugin().doPost(req, res);
    }

    @Test
    void execWithoutTokenIs403() throws Exception {
        post(null, "ss");
        verify(res).sendError(403);
        verify(shell, never()).submit(anyString());
    }

    @Test
    void execWithWrongTokenIs403() throws Exception {
        post("ffffffffffffffffffffffffffffffff", "ss");
        verify(res).sendError(403);
        verify(shell, never()).submit(anyString());
    }

    @Test
    void execWithTokenSubmitsTheLineAndAnswers202() throws Exception {
        post(TOKEN, "ss\n");
        verify(shell).submit("ss");
        verify(res).setStatus(202);
    }

    @Test
    void execWhenShellDisabledIs403() throws Exception {
        props.put("terminal.shell.enabled", "false");
        post(TOKEN, "ss");
        verify(res).sendError(403);
        verify(shell, never()).submit(anyString());
    }

    @Test
    void execWhenShellNotAvailableIs503() throws Exception {
        when(shell.available()).thenReturn(false);
        post(TOKEN, "ss");
        verify(res).sendError(503);
        verify(shell, never()).submit(anyString());
    }

    @Test
    void execWithLineLongerThanLimitIs413() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4097; i++) {
            sb.append('a');
        }
        post(TOKEN, sb.toString());
        verify(res).sendError(413);
        verify(shell, never()).submit(anyString());
    }

    @Test
    void execWithEmptyLineIs204() throws Exception {
        post(TOKEN, "\r\n");
        verify(res).setStatus(204);
        verify(shell, never()).submit(anyString());
    }

    /**
     * A control character in the line is refused with 400, after the empty check. A line break
     * would run a second Gogo statement under one echo. An escape sequence would reach each page.
     */
    @Test
    void execWithControlCharacterIs400() throws Exception {
        post(TOKEN, "ss\nlb");
        HttpServletResponse first = res;
        res = mock(HttpServletResponse.class);
        post(TOKEN, "a\u001b[2Jb");
        verify(first).sendError(400);
        verify(res).sendError(400);
        verify(shell, never()).submit(anyString());
    }

    /**
     * A tab is whitespace for Gogo, and the sanitizer keeps it. Thus a pasted tab is not refused.
     * All other control characters still give 400 (see the test above).
     */
    @Test
    void execAcceptsTab() throws Exception {
        post(TOKEN, "lb\tterminal");
        verify(shell).submit("lb\tterminal");
        verify(res).setStatus(202);
    }

    @Test
    void resServesOnlyKnownNames() throws Exception {
        TerminalPlugin p = plugin();
        CapturingOutput body = new CapturingOutput();
        when(res.getOutputStream()).thenReturn(body);
        when(req.getPathInfo()).thenReturn("/terminal/res/terminal.js");
        p.doGet(req, res);
        verify(res).setContentType(startsWith("application/javascript"));
        verify(res, never()).sendError(404);
        assertTrue(body.bytes.size() > 0);

        HttpServletResponse res2 = mock(HttpServletResponse.class);
        when(req.getPathInfo()).thenReturn("/terminal/res/../META-INF/MANIFEST.MF");
        p.doGet(req, res2);
        verify(res2).sendError(404);

        HttpServletResponse res3 = mock(HttpServletResponse.class);
        when(req.getPathInfo()).thenReturn("/terminal/res/unknown.js");
        p.doGet(req, res3);
        verify(res3).sendError(404);
    }

    private String stream(String lastEventId) throws Exception {
        FailingWriter out = new FailingWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(out));
        when(req.getPathInfo()).thenReturn("/terminal/stream");
        when(req.getHeader("Last-Event-ID")).thenReturn(lastEventId);
        plugin().doGet(req, res);
        verify(res).setHeader("Cache-Control", "no-cache");
        verify(res).setHeader("X-Accel-Buffering", "no");
        return out.text.toString();
    }

    @Test
    @Timeout(10)
    void streamStartsAfterLastEventId() throws Exception {
        hub.append("out", "one");
        hub.append("out", "two");
        hub.append("out", "three");
        String text = stream("2");
        verify(res).setContentType("text/event-stream; charset=UTF-8");
        assertTrue(text.contains("id: 3\n"), text);
        assertTrue(text.contains("data: three\n"), text);
        assertFalse(text.contains("id: 2\n"), text);
    }

    @Test
    @Timeout(10)
    void streamRestartsWhenLastEventIdIsAhead() throws Exception {
        hub.append("out", "one");
        hub.append("out", "two");
        hub.append("out", "three");
        String text = stream("99");
        assertTrue(text.contains("id: 1\n"), text);
    }

    @Test
    @Timeout(10)
    void streamReportsGoneLines() throws Exception {
        hub = new OutputHub(2);
        for (int i = 1; i <= 5; i++) {
            hub.append("out", "line" + i);
        }
        String text = stream(null);
        String first = text.substring(0, text.indexOf("\n\n"));
        assertTrue(first.contains("event: gone\n"), text);
        assertTrue(first.endsWith("data: 3"), text);
        assertTrue(text.contains("data: line4\n"), text);
    }

    private String export() throws Exception {
        StringWriter body = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(body));
        when(req.getPathInfo()).thenReturn("/terminal/export");
        plugin().doGet(req, res);
        return body.toString();
    }

    /** The file has the text of each line with no color code, the commands, and no done marker. */
    @Test
    void exportGivesPlainLinesWithoutDoneMarkers() throws Exception {
        hub.append("out", "\u001b[31mERROR\u001b[0m boom");
        hub.append("cmd", "osgi> ss");
        hub.append("err", "\u0130stanbul");
        hub.append("done", "");
        assertEquals("ERROR boom\nosgi> ss\n\u0130stanbul\n", export());
    }

    /** The charset must be set before getWriter(), or the container writes ISO-8859-1. */
    @Test
    void exportSetsDownloadHeadersBeforeTheWriter() throws Exception {
        export();
        InOrder order = inOrder(res);
        order.verify(res).setContentType("text/plain; charset=UTF-8");
        order.verify(res).getWriter();
        verify(res).setHeader("Cache-Control", "no-store");
        verify(res).setHeader(eq("Content-Disposition"),
                matches("attachment; filename=\"terminal-\\d{8}-\\d{6}\\.log\""));
        verify(res, never()).sendError(anyInt());
    }

    @Test
    void exportOfAnEmptyHubIsEmpty() throws Exception {
        assertEquals("", export());
    }

    /** A full buffer: the file says that its start is missing. The export needs no shell. */
    @Test
    void exportReportsGoneLinesAndWorksReadOnly() throws Exception {
        props.put("terminal.shell.enabled", "false");
        when(shell.available()).thenReturn(false);
        for (int i = 1; i <= 103; i++) { // the hub of setUp holds 100 lines
            hub.append("out", "line " + i);
        }
        String text = export();
        assertTrue(text.startsWith("[3 earlier lines are gone]\nline 4\n"), text);
        assertTrue(text.endsWith("line 103\n"), text);
    }

    @Test
    void pageContainsTokenAndFlags() throws Exception {
        props.put("terminal.prompt", "<p> ");
        StringWriter page = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(page));
        plugin().renderContent(req, res);
        String html = page.toString();
        assertTrue(html.contains("data-token=\"" + TOKEN + "\""), html);
        assertTrue(html.contains("data-shell=\"true\""), html);
        assertTrue(html.contains("data-prompt=\"&lt;p&gt; \""), html);
        assertTrue(html.contains("data-scrollback=\"100\""), html);
        assertTrue(html.contains("terminal/res/terminal.js"), html);
        assertEquals(-1, html.indexOf("<p> "), html);
    }

    /** terminal.js finds the controls by these ids and option values. */
    @Test
    void pageContainsExportThemeAndFontControls() throws Exception {
        StringWriter page = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(page));
        plugin().renderContent(req, res);
        String html = page.toString();
        assertTrue(html.contains("id=\"terminal-export\" href=\"terminal/export\" download"), html);
        for (String id : new String[] { "terminal-theme", "terminal-font", "terminal-font-smaller",
                "terminal-font-size", "terminal-font-larger" }) {
            assertTrue(html.contains("id=\"" + id + "\""), id);
        }
        for (String value : new String[] { "dark", "light", "contrast", "solarized",
                "default", "menlo", "consolas", "dejavu", "courier" }) {
            assertTrue(html.contains("<option value=\"" + value + "\">"), value);
        }
    }

    /**
     * With no bound command processor at render time, the page is read-only: it shows output and
     * no prompt. A page that loaded before Gogo was available stays read-only until a reload.
     */
    @Test
    void pageIsReadOnlyWithoutProcessor() throws Exception {
        when(shell.available()).thenReturn(false);
        StringWriter page = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(page));
        plugin().renderContent(req, res);
        String html = page.toString();
        assertTrue(html.contains("data-shell=\"false\""), html);
    }

    /** A request body. */
    private static final class BodyStream extends ServletInputStream {
        private final ByteArrayInputStream in;

        BodyStream(byte[] bytes) {
            in = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return in.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            return in.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
        }
    }

    /** A response body. */
    private static final class CapturingOutput extends ServletOutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            bytes.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
        }
    }

    /** Keeps the text. The second flush fails, as when the browser closes the connection. */
    private static final class FailingWriter extends Writer {
        final StringBuilder text = new StringBuilder();
        private int flushes;

        @Override
        public void write(char[] cbuf, int off, int len) {
            text.append(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            if (++flushes >= 2) {
                throw new IOException("connection closed");
            }
        }

        @Override
        public void close() {
        }
    }
}
