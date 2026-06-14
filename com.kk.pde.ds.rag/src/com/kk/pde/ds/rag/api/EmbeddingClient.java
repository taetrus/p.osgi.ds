package com.kk.pde.ds.rag.api;

import java.util.List;

/**
 * Turns text into dense vectors via an OpenAI-compatible {@code /v1/embeddings}
 * endpoint.
 *
 * <p>Distinguishes queries from passages because asymmetric retrieval models
 * (e.g. the E5 family) were trained with role prefixes ("query:" / "passage:")
 * and degrade noticeably if the wrong role — or none — is used.</p>
 */
public interface EmbeddingClient {

	/**
	 * Embed a search query (applies the configured query role prefix).
	 *
	 * @return the embedding vector, or {@code null} if the call failed
	 */
	float[] embedQuery(String query);

	/**
	 * Embed document passages for storage (applies the passage role prefix).
	 * Returns one vector per input, in the same order. Entries may be
	 * {@code null} individually if a particular item could not be embedded.
	 */
	List<float[]> embedPassages(List<String> passages);
}
