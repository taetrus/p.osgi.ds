package com.kk.pde.ds.rag.api;

import java.util.List;

/**
 * Splits a document's text into overlapping chunks suitable for embedding.
 * Chunks should respect natural boundaries (paragraphs/sentences) and carry
 * source metadata so retrieved passages can be cited.
 */
public interface TextChunker {

	/**
	 * @param text   the full document text
	 * @param source originating document name (used in chunk metadata)
	 * @return ordered list of chunks (possibly empty for blank input)
	 */
	List<Chunk> chunk(String text, String source);
}
