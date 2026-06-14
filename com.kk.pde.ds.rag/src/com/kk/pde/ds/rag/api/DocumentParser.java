package com.kk.pde.ds.rag.api;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extracts plain text from a document file. Implementations are expected to
 * handle the common office/portable formats (.pdf, .docx, .pptx, .txt, .html).
 */
public interface DocumentParser {

	/** @return true if this parser is willing to handle the given file. */
	boolean supports(Path file);

	/**
	 * Extract the document's textual content.
	 *
	 * @param file the document to read
	 * @return extracted plain text (never null; may be empty)
	 * @throws IOException if the file cannot be read or parsed
	 */
	String extractText(Path file) throws IOException;
}
