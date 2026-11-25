package com.kk.pde.ds.webconsole.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.osgi.framework.BundleContext;

/**
 * The WebConsole plugin with the label {@code terminal}. It renders the page, serves the files
 * in the bundle folder {@code res/}, streams the {@link OutputHub} as Server-Sent Events
 * ({@code GET terminal/stream}) and gives one command line to the {@link Shell}
 * ({@code POST terminal/exec}).
 *
 * <p>
 * SCR reads {@code OSGI-INF/com.kk.pde.ds.webconsole.terminal.TerminalPlugin.xml}. That file
 * names the methods {@code activate}, {@code deactivate}, {@code bindProcessor} and
 * {@code unbindProcessor}. SCR calls the bind method before {@code activate}, so the plugin
 * keeps the processor until the shell exists.
 * </p>
 * <p>
 * The Gogo import is optional. Thus no field and no method signature of this class names a Gogo
 * type: the bind methods take {@code Object}, and only {@link Shell} casts to the Gogo type.
 * Without that, {@code Class.getDeclaredMethods()} (SCR, {@code HttpServlet.doOptions}) fails
 * with {@code NoClassDefFoundError} in an application with no Gogo. {@code NoGogoTest} checks this.
 * </p>
 */
public class TerminalPlugin extends AbstractWebConsolePlugin {

    private static final long serialVersionUID = 1L;

    static final String LABEL = "terminal";
    static final String TOKEN_HEADER = "X-Terminal-Token";
    static final int MAX_LINE = 4096;

    private static final String SHELL_ENABLED = "terminal.shell.enabled";
    private static final String PROMPT = "terminal.prompt";
    private static final String DEFAULT_PROMPT = "osgi> ";
    private static final String STREAM_PATH = "/" + LABEL + "/stream";
    private static final String EXEC_PATH = "/" + LABEL + "/exec";
    private static final String RES_PREFIX = "/" + LABEL + "/res/";
    private static final Set<String> FILES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "xterm.js", "xterm.css", "addon-search.js", "addon-fit.js", "terminal.js", "terminal.css")));

    /** Reads a configuration property. */
    interface Config {
        String get(String key, String defaultValue);
    }

    /** The time without a line after which the stream sends a ping comment. */
    transient long pingMillis = 15_000;

    private transient volatile OutputHub hub;
    private transient volatile Shell shell;
    private transient volatile String token;
    private transient volatile Config config;
    private transient volatile boolean active;
    private transient Object processor; // a Gogo CommandProcessor, guarded by this

    /** For SCR. */
    public TerminalPlugin() {
    }

    /** For the tier-1 tests: no OSGi framework. */
    TerminalPlugin(OutputHub hub, Shell shell, String token, Config config) {
        this.hub = hub;
        this.shell = shell;
        this.token = token;
        this.config = config;
        this.active = true;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getTitle() {
        return "Terminal";
    }

    @Override
    public void activate(final BundleContext context) {
        super.activate(context);
        TerminalRuntime runtime = TerminalRuntime.get();
        hub = runtime != null ? runtime.hub() : new OutputHub(Activator.DEFAULT_SCROLLBACK);
        config = new Config() {
            @Override
            public String get(String key, String defaultValue) {
                String value = context.getProperty(key);
                return value != null ? value : defaultValue;
            }
        };
        token = Token.generate();
        synchronized (this) {
            if (shellEnabled()) {
                shell = new Shell(hub, prompt());
                if (processor != null) {
                    shell.bind(processor);
                }
            }
        }
        active = true;
    }

    @Override
    public void deactivate() {
        active = false;
        synchronized (this) {
            if (shell != null) {
                shell.close();
                shell = null;
            }
        }
        super.deactivate();
    }

    /** SCR calls this with the Gogo {@code CommandProcessor}. */
    public synchronized void bindProcessor(Object newProcessor) {
        processor = newProcessor;
        if (shell != null) {
            shell.bind(newProcessor);
        }
    }

    public synchronized void unbindProcessor(Object oldProcessor) {
        if (processor == oldProcessor) {
            processor = null;
        }
        if (shell != null) {
            shell.unbind(oldProcessor);
        }
    }

    @Override
    protected void renderContent(HttpServletRequest req, HttpServletResponse res) throws IOException {
        PrintWriter pw = res.getWriter();
        String base = LABEL + "/res/";
        pw.println("<link rel=\"stylesheet\" href=\"" + base + "xterm.css\">");
        pw.println("<link rel=\"stylesheet\" href=\"" + base + "terminal.css\">");
        pw.println("<div id=\"terminal-toolbar\">");
        pw.println("  <input type=\"search\" id=\"terminal-search\" placeholder=\"Search\">");
        pw.println("  <button type=\"button\" id=\"terminal-prev\" title=\"Previous match\">&lt;</button>");
        pw.println("  <button type=\"button\" id=\"terminal-next\" title=\"Next match\">&gt;</button>");
        pw.println("  <button type=\"button\" id=\"terminal-clear\">Clear</button>");
        pw.println("  <label><input type=\"checkbox\" id=\"terminal-follow\" checked> Follow</label>");
        pw.println("</div>");
        // Without a bound processor the page is read-only. A later bind needs a reload.
        Shell current = shell;
        boolean shellOn = shellEnabled() && current != null && current.available();
        pw.println("<div id=\"terminal\" data-token=\"" + escape(token) + "\" data-shell=\"" + shellOn
                + "\" data-prompt=\"" + escape(prompt()) + "\" data-scrollback=\"" + hub.capacity() + "\"></div>");
        pw.println("<script src=\"" + base + "xterm.js\"></script>");
        pw.println("<script src=\"" + base + "addon-search.js\"></script>");
        pw.println("<script src=\"" + base + "addon-fit.js\"></script>");
        pw.println("<script src=\"" + base + "terminal.js\"></script>");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (STREAM_PATH.equals(path)) {
            stream(req, res);
            return;
        }
        if (path != null && path.startsWith(RES_PREFIX)) {
            serveFile(path.substring(RES_PREFIX.length()), res);
            return;
        }
        super.doGet(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!EXEC_PATH.equals(req.getPathInfo())) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!Token.matches(token, req.getHeader(TOKEN_HEADER)) || !shellEnabled()) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Shell current = shell;
        if (current == null || !current.available()) {
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        String line = readLine(req.getInputStream());
        if (line == null) {
            res.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        if (line.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (hasControlCharacter(line)) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!current.submit(line)) {
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        res.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    /** The bundle entry {@code res/<name>}. The tier-1 test reads the module folder instead. */
    URL resource(String name) {
        return getClass().getResource("/res/" + name);
    }

    private void serveFile(String name, HttpServletResponse res) throws IOException {
        URL url = FILES.contains(name) ? resource(name) : null;
        if (url == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        res.setContentType(name.endsWith(".css") ? "text/css; charset=UTF-8"
                : "application/javascript; charset=UTF-8");
        InputStream in = url.openStream();
        try {
            OutputStream out = res.getOutputStream();
            byte[] chunk = new byte[8192];
            for (int n; (n = in.read(chunk)) > 0;) {
                out.write(chunk, 0, n);
            }
        } finally {
            in.close();
        }
    }

    // Blocks until a write fails or the component deactivates. A client disconnect shows
    // only as a PrintWriter error, so the loop never throws to the container for it.
    private void stream(HttpServletRequest req, HttpServletResponse res) throws IOException {
        OutputHub source = hub;
        res.setContentType("text/event-stream; charset=UTF-8");
        res.setHeader("Cache-Control", "no-cache");
        res.setHeader("X-Accel-Buffering", "no");
        PrintWriter pw = res.getWriter();
        long last = startAfter(req.getHeader("Last-Event-ID"), source.lastSeq());
        while (active && !pw.checkError()) {
            OutputHub.Batch batch;
            try {
                batch = source.await(last, pingMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (batch.lines.isEmpty()) {
                pw.print(SseEncoder.comment("ping"));
            } else {
                if (batch.dropped > 0) {
                    last += batch.dropped;
                    pw.print(SseEncoder.event(last, "gone", String.valueOf(batch.dropped)));
                }
                for (OutputHub.Line line : batch.lines) {
                    pw.print(SseEncoder.event(line.seq, line.type, line.text));
                    last = line.seq;
                }
            }
            pw.flush();
        }
    }

    // A value ahead of the hub is from before an application restart: start again from 0.
    private static long startAfter(String header, long lastSeq) {
        if (header == null) {
            return 0;
        }
        try {
            long value = Long.parseLong(header.trim());
            return value < 0 || value > lastSeq ? 0 : value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Returns the line without one trailing line break, or null when it is longer than MAX_LINE.
    private static String readLine(InputStream body) throws IOException {
        Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8);
        char[] buf = new char[MAX_LINE + 3];
        int len = 0;
        for (int n; len < buf.length && (n = reader.read(buf, len, buf.length - len)) > 0;) {
            len += n;
        }
        if (len > 0 && buf[len - 1] == '\n') {
            len--;
        }
        if (len > 0 && buf[len - 1] == '\r') {
            len--;
        }
        return len > MAX_LINE ? null : new String(buf, 0, len);
    }

    // A line break would run a second statement, and an escape sequence would reach every page.
    // A tab is whitespace for Gogo, and TextSanitizer keeps it, so a pasted tab is accepted.
    private static boolean hasControlCharacter(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7f || (c >= 0x80 && c <= 0x9f)) {
                return true;
            }
        }
        return false;
    }

    private boolean shellEnabled() {
        return !"false".equalsIgnoreCase(config.get(SHELL_ENABLED, "true").trim());
    }

    private String prompt() {
        return config.get(PROMPT, DEFAULT_PROMPT);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '&':
                sb.append("&amp;");
                break;
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            case '\'':
                sb.append("&#39;");
                break;
            default:
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
