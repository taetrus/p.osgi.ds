package com.kk.pde.ds.rag.api;

/** A {@link Chunk} paired with its similarity score against a query. */
public final class ScoredChunk {

	private final Chunk chunk;
	private final double score;

	public ScoredChunk(Chunk chunk, double score) {
		this.chunk = chunk;
		this.score = score;
	}

	public Chunk getChunk() {
		return chunk;
	}

	/** Cosine similarity in [-1, 1]; higher is more relevant. */
	public double getScore() {
		return score;
	}
}
