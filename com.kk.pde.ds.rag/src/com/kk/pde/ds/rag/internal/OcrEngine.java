package com.kk.pde.ds.rag.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optical character recognition for text-bearing images, used to recover the
 * text that lives <em>inside</em> bitmaps (scanned PDF pages, embedded
 * screenshots, photographed documents). Scope is deliberately narrow: it reads
 * text out of images and nothing more — no figure/diagram description, no layout
 * understanding.
 *
 * <p><b>Why shell out to the {@code tesseract} CLI</b> rather than use a Java OCR
 * library (Tess4J)? Tess4J pulls in JNA and slf4j-api, and a second slf4j on the
 * classpath is precisely what derailed Apache Tika for this project (see
 * CLAUDE.md). Invoking the native binary keeps this bundle's dependency tree at
 * zero new Java libraries and preserves the airgap — no network access occurs.</p>
 *
 * <p><b>Graceful degradation:</b> if OCR is disabled via {@code -Drag.ocr.enabled=false}
 * or the {@code tesseract} binary is not installed, every method returns an empty
 * string and the surrounding parser falls back to text-layer-only extraction,
 * exactly as it behaved before OCR existed. Availability is probed once and cached.</p>
 *
 * <p>Configuration (system properties):</p>
 * <ul>
 *   <li>{@code rag.ocr.enabled} — master switch (default {@code true}).</li>
 *   <li>{@code rag.ocr.tesseract.path} — binary name or absolute path (default {@code tesseract}).</li>
 *   <li>{@code rag.ocr.language} — Tesseract language pack(s), e.g. {@code eng} or {@code eng+deu} (default {@code eng}).</li>
 *   <li>{@code rag.ocr.dpi} — render/OCR resolution for rasterised PDF pages (default {@code 300}).</li>
 * </ul>
 */
final class OcrEngine {

	private static final Logger LOG = LoggerFactory.getLogger(OcrEngine.class);

	private final boolean enabled;
	private final String binary;
	private final String language;
	private final int dpi;

	/** Tri-state availability cache: null = not yet probed. */
	private Boolean available;

	OcrEngine() {
		this.enabled = !"false".equalsIgnoreCase(System.getProperty("rag.ocr.enabled", "true"));
		this.binary = System.getProperty("rag.ocr.tesseract.path", "tesseract");
		this.language = System.getProperty("rag.ocr.language", "eng");
		this.dpi = parseInt(System.getProperty("rag.ocr.dpi"), 300);
	}

	boolean isEnabled() {
		return enabled;
	}

	/** Resolution (DPI) at which callers should rasterise pages before OCR. */
	int dpi() {
		return dpi;
	}

	/**
	 * @return true if OCR is enabled and a working {@code tesseract} binary is
	 *         present. Probed lazily once; a missing binary is reported a single
	 *         time at WARN, then cached.
	 */
	synchronized boolean isAvailable() {
		if (!enabled) {
			return false;
		}
		if (available == null) {
			available = Boolean.valueOf(probe());
			if (available.booleanValue()) {
				LOG.info("OCR available via '{}' (lang={}, dpi={})", binary, language, dpi);
			} else {
				LOG.warn("OCR is enabled but '{}' was not found/usable; text inside images "
					+ "will be skipped. Install Tesseract or set -Drag.ocr.tesseract.path, "
					+ "or disable with -Drag.ocr.enabled=false", binary);
			}
		}
		return available.booleanValue();
	}

	private boolean probe() {
		try {
			Process p = new ProcessBuilder(binary, "--version").redirectErrorStream(true).start();
			drain(p.getInputStream());
			return p.waitFor() == 0;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** OCR a decoded image. Returns recognised text (trimmed; possibly empty), never null. */
	String ocr(BufferedImage image) {
		if (image == null || !isAvailable()) {
			return "";
		}
		File tmp = null;
		try {
			tmp = File.createTempFile("rag-ocr-", ".png");
			ImageIO.write(image, "png", tmp);
			return runTesseract(tmp);
		} catch (IOException e) {
			LOG.warn("OCR failed for an image: {}", e.getMessage());
			return "";
		} finally {
			if (tmp != null && !tmp.delete()) {
				tmp.deleteOnExit();
			}
		}
	}

	/**
	 * OCR raw image bytes (e.g. an embedded OOXML media part or a standalone image
	 * file). Bytes that {@link ImageIO} cannot decode (EMF/WMF vector parts, etc.)
	 * yield an empty string rather than an error.
	 */
	String ocr(byte[] imageBytes) {
		if (imageBytes == null || imageBytes.length == 0 || !isAvailable()) {
			return "";
		}
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
			return ocr(img);
		} catch (IOException e) {
			LOG.warn("Could not decode an embedded image for OCR: {}", e.getMessage());
			return "";
		}
	}

	// ---- internals ---------------------------------------------------------

	private String runTesseract(File png) throws IOException {
		// `tesseract <input> stdout -l <lang> --dpi <dpi>` writes recognised text to
		// stdout. --dpi is supplied so Tesseract does not warn about/guess resolution.
		ProcessBuilder pb = new ProcessBuilder(
			binary, png.getAbsolutePath(), "stdout", "-l", language, "--dpi", String.valueOf(dpi));
		Process p = pb.start();
		// Drain stderr on a separate thread so a chatty process can never deadlock
		// by filling its stderr buffer while we block reading stdout.
		final InputStream err = p.getErrorStream();
		Thread errDrain = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					drain(err);
				} catch (IOException ignore) {
					// stderr is diagnostic only
				}
			}
		}, "rag-ocr-stderr");
		errDrain.setDaemon(true);
		errDrain.start();

		String out = drain(p.getInputStream());
		try {
			p.waitFor();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return out.trim();
	}

	private static String drain(InputStream in) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) != -1) {
			bos.write(buf, 0, n);
		}
		in.close();
		return new String(bos.toByteArray(), StandardCharsets.UTF_8);
	}

	private static int parseInt(String s, int def) {
		if (s == null || s.isEmpty()) {
			return def;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
