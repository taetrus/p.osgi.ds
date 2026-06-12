package com.kk.pde.ds.rag.api;

import java.util.List;

/**
 * Stores chunk embeddings and answers nearest-neighbour queries.
 *
 * <p>Deliberately minimal so the initial in-memory, brute-force implementation
 * can later be swapped for a real vector database (e.g. pgvector) without
 * touching callers.</p>
 */
public interface VectorStore {

	/** Add a chunk and its embedding to the store. */
	void add(Chunk chunk, float[] embedding);

	/**
	 * Return the {@code topK} chunks most similar to the query embedding,
	 * highest score first.
	 */
	List<ScoredChunk> search(float[] queryEmbedding, int topK);

	/** Number of chunks currently stored. */
	int size();

	/** Remove everything. */
	void clear();
}
