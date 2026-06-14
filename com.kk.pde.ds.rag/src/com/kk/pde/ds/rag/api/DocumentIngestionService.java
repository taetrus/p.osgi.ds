package com.kk.pde.ds.rag.api;

import java.util.List;

/**
 * Orchestrates the ingestion pipeline (parse &rarr; chunk &rarr; embed &rarr;
 * store) and answers retrieval queries against what has been ingested.
 */
public interface DocumentIngestionService {

	/**
	 * Ingest a single file or, if a directory, every supported document found
	 * underneath it (recursively).
	 *
	 * @param path filesystem path to a file or folder
	 * @return a summary of what was ingested
	 */
	IngestResult ingestPath(String path);

	/**
	 * Embed the query and return the {@code topK} most relevant stored chunks.
	 */
	List<ScoredChunk> search(String query, int topK);

	/** Number of chunks currently retrievable. */
	int chunkCount();
}
