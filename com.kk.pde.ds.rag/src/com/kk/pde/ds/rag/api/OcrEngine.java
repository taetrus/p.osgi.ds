package com.kk.pde.ds.rag.api;

import java.io.IOException;

/**
 * Optical character recognition: turns an encoded raster image into plain text.
 *
 * <p>Used by {@link DocumentParser} implementations to recover text that lives in
 * pixels rather than a document's text layer — scanned PDF pages and images
 * embedded in PDFs/office documents.</p>
 *
 * <p>Implementations must degrade gracefully: if the OCR backend is unavailable
 * (disabled by config, or the engine binary is not installed), {@link #isAvailable()}
 * returns {@code false} and {@link #ocr(byte[])} returns an empty string rather than
 * throwing. This lets the parser fall back to text-only behaviour with no hard failure.</p>
 */
public interface OcrEngine {

	/**
	 * @return true if OCR is enabled and the backend is usable. Callers should skip
	 *         the (expensive) work of rasterizing/extracting images when this is false.
	 */
	boolean isAvailable();

	/**
	 * Recognize text in a single encoded image.
	 *
	 * @param encodedImage the image bytes in a common raster format (PNG, JPEG, TIFF,
	 *                      BMP…) — i.e. a real image file's bytes, not raw pixels
	 * @return the recognized text (never null; empty if nothing was found, OCR is
	 *         unavailable, or recognition failed)
	 * @throws IOException only for unexpected I/O errors the caller may wish to surface;
	 *                     routine recognition failures are swallowed and return ""
	 */
	String ocr(byte[] encodedImage) throws IOException;
}
