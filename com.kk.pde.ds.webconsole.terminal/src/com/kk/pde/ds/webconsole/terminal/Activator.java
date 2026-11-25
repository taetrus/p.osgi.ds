package com.kk.pde.ds.webconsole.terminal;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.List;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Prototype (Task 1). Replaces {@code System.out} and {@code System.err} with a tee into an
 * in-memory line buffer. A later task replaces this class with {@code StdCapture} and
 * {@code OutputHub}.
 */
public class Activator implements BundleActivator {

	/** The shared line buffer. The plugin reads it. */
	static final Lines LINES = new Lines(50_000);

	private PrintStream originalOut;
	private PrintStream originalErr;
	private PrintStream teeOut;
	private PrintStream teeErr;

	@Override
	public void start(BundleContext context) {
		originalOut = System.out;
		originalErr = System.err;
		teeOut = new PrintStream(new Tee(originalOut, LINES), true);
		teeErr = new PrintStream(new Tee(originalErr, LINES), true);
		System.setOut(teeOut);
		System.setErr(teeErr);
		System.out.println("probe");
	}

	@Override
	public void stop(BundleContext context) {
		if (System.out == teeOut) {
			System.setOut(originalOut);
		}
		if (System.err == teeErr) {
			System.setErr(originalErr);
		}
	}

	/** Writes each byte to the original stream, and each complete line to the buffer. */
	static final class Tee extends OutputStream {
		private final OutputStream original;
		private final Lines lines;
		private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

		Tee(OutputStream original, Lines lines) {
			this.original = original;
			this.lines = lines;
		}

		@Override
		public synchronized void write(int b) {
			write(new byte[] { (byte) b }, 0, 1);
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) {
			try {
				original.write(b, off, len);
			} catch (java.io.IOException e) {
				// The original stream failed. The buffer still gets the bytes.
			}
			for (int i = off; i < off + len; i++) {
				if (b[i] == '\n') {
					lines.add(new String(pending.toByteArray(), Charset.defaultCharset()));
					pending.reset();
				} else if (b[i] != '\r') {
					pending.write(b[i]);
				}
			}
		}

		@Override
		public void flush() throws java.io.IOException {
			original.flush();
		}
	}

	/** A bounded list of lines with sequence numbers. */
	static final class Lines {
		private final int capacity;
		private final ArrayDeque<String> buffer = new ArrayDeque<>();
		private long nextSeq;

		Lines(int capacity) {
			this.capacity = capacity;
		}

		synchronized void add(String line) {
			if (buffer.size() == capacity) {
				buffer.removeFirst();
			}
			buffer.addLast(line);
			nextSeq++;
			notifyAll();
		}

		/**
		 * Adds the lines with a sequence number of {@code from} or more to {@code out}.
		 * Returns the sequence number of the next line.
		 */
		synchronized long drainTo(long from, List<String> out) {
			long first = nextSeq - buffer.size();
			long skip = Math.max(0, from - first);
			long i = 0;
			for (String s : buffer) {
				if (i++ >= skip) {
					out.add(s);
				}
			}
			return nextSeq;
		}

		synchronized void await(long seq, long millis) throws InterruptedException {
			if (nextSeq <= seq) {
				wait(millis);
			}
		}
	}
}
