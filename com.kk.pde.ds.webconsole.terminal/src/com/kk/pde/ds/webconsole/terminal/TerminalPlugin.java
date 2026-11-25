package com.kk.pde.ds.webconsole.terminal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.osgi.framework.BundleContext;

/**
 * Prototype (Task 1). A WebConsole plugin that streams the captured output and runs one Gogo
 * command for each POST. A later task replaces this class.
 */
public class TerminalPlugin extends AbstractWebConsolePlugin {

	private static final long serialVersionUID = 1L;
	private static final String LABEL = "terminal";

	private transient volatile CommandSession session;
	private transient ExecutorService worker;

	@Override
	public String getLabel() {
		return LABEL;
	}

	@Override
	public String getTitle() {
		return "Terminal";
	}

	@Override
	public void activate(BundleContext context) {
		super.activate(context);
		worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "webconsole-terminal-shell");
			t.setDaemon(true);
			return t;
		});
	}

	@Override
	public void deactivate() {
		worker.shutdownNow();
		super.deactivate();
	}

	public void bindProcessor(CommandProcessor processor) {
		PrintStream out = new PrintStream(new HubOutputStream(), true);
		InputStream in = new ByteArrayInputStream(new byte[0]);
		session = processor.createSession(in, out, out);
	}

	public void unbindProcessor(CommandProcessor processor) {
		CommandSession old = session;
		session = null;
		if (old != null) {
			old.close();
		}
	}

	@Override
	protected void renderContent(HttpServletRequest req, HttpServletResponse res) throws IOException {
		PrintWriter pw = res.getWriter();
		pw.println("<pre id=\"terminal\">Terminal prototype</pre>");
		pw.println("<script src=\"" + LABEL + "/res/terminal.js\"></script>");
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		if (("/" + LABEL + "/stream").equals(req.getPathInfo())) {
			stream(res);
			return;
		}
		super.doGet(req, res);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
		if (!("/" + LABEL + "/exec").equals(req.getPathInfo())) {
			res.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String line = readBody(req).trim();
		CommandSession s = session;
		if (s == null) {
			res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			return;
		}
		res.setStatus(HttpServletResponse.SC_ACCEPTED);
		worker.execute(() -> run(s, line));
	}

	/** Returns the bundle entry for {@code /terminal/res/...}. WebConsole calls it by reflection. */
	protected URL getResource(String path) {
		String prefix = "/" + LABEL + "/res/";
		if (path != null && path.startsWith(prefix)) {
			return getClass().getResource("/res/" + path.substring(prefix.length()));
		}
		return null;
	}

	private static void run(CommandSession s, String line) {
		add("cmd", "osgi> " + line);
		try {
			Object result = s.execute(line);
			if (result != null) {
				for (String l : s.format(result, 0).toString().split("\r?\n", -1)) {
					add("out", l);
				}
			}
		} catch (Exception e) {
			add("err", "gogo: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static void add(String type, String text) {
		TerminalRuntime runtime = TerminalRuntime.get();
		if (runtime != null) {
			runtime.hub().append(type, TextSanitizer.colorByLevel(TextSanitizer.clean(text)));
		}
	}

	private static void stream(HttpServletResponse res) throws IOException {
		res.setContentType("text/event-stream");
		res.setCharacterEncoding("UTF-8");
		res.setHeader("Cache-Control", "no-cache");
		PrintWriter pw = res.getWriter();
		long seq = 0;
		while (!pw.checkError()) {
			TerminalRuntime runtime = TerminalRuntime.get();
			if (runtime == null) {
				return;
			}
			OutputHub.Batch batch;
			try {
				batch = runtime.hub().await(seq, 15_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			for (OutputHub.Line l : batch.lines) {
				pw.print("id: " + l.seq + "\ndata: " + l.text + "\n\n");
				seq = l.seq;
			}
			if (batch.lines.isEmpty()) {
				pw.print(": keep-alive\n\n");
			}
			pw.flush();
		}
	}

	private static String readBody(HttpServletRequest req) throws IOException {
		java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		InputStream in = req.getInputStream();
		for (int n; (n = in.read(chunk)) > 0;) {
			buf.write(chunk, 0, n);
		}
		return new String(buf.toByteArray(), StandardCharsets.UTF_8);
	}

	/** Sends the session output to the hub, one line at a time. */
	private static final class HubOutputStream extends OutputStream {
		private final LineAssembler lines = new LineAssembler(Charset.defaultCharset(), l -> add("out", l));

		@Override
		public void write(int b) {
			write(new byte[] { (byte) b }, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) {
			lines.write(b, off, len);
		}
	}
}
