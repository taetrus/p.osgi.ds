package com.kk.pde.ds.rag.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.rag.api.Chunk;
import com.kk.pde.ds.rag.api.ScoredChunk;
import com.kk.pde.ds.rag.api.VectorStore;

/**
 * Brute-force, in-memory {@link VectorStore}: linear cosine similarity over
 * {@code float[]} vectors.
 *
 * <p>Adequate for thousands of chunks and trivially correct, which is what the
 * spec asks for as a starting point. Because it sits behind {@link VectorStore},
 * a pgvector-backed implementation can replace it later without touching any
 * caller. Each vector's L2 norm is cached at insert time so a query only pays for
 * the dot products.</p>
 */
@Component(service = VectorStore.class)
public class InMemoryVectorStore implements VectorStore {

	private static final Logger LOG = LoggerFactory.getLogger(InMemoryVectorStore.class);

	private static final class Entry {
		final Chunk chunk;
		final float[] vector;
		final double norm;

		Entry(Chunk chunk, float[] vector) {
			this.chunk = chunk;
			this.vector = vector;
			this.norm = l2norm(vector);
		}
	}

	private final List<Entry> entries = new CopyOnWriteArrayList<Entry>();

	@Activate
	public void activate() {
		LOG.info("InMemoryVectorStore activated");
	}

	@Override
	public void add(Chunk chunk, float[] embedding) {
		if (chunk == null || embedding == null || embedding.length == 0) {
			return;
		}
		entries.add(new Entry(chunk, embedding));
	}

	@Override
	public List<ScoredChunk> search(float[] queryEmbedding, int topK) {
		List<ScoredChunk> results = new ArrayList<ScoredChunk>();
		if (queryEmbedding == null || queryEmbedding.length == 0 || entries.isEmpty()) {
			return results;
		}
		double queryNorm = l2norm(queryEmbedding);
		if (queryNorm == 0.0) {
			return results;
		}

		for (Entry e : entries) {
			if (e.vector.length != queryEmbedding.length || e.norm == 0.0) {
				continue; // dimension mismatch (e.g. model changed) or zero vector
			}
			double dot = 0.0;
			for (int i = 0; i < queryEmbedding.length; i++) {
				dot += (double) queryEmbedding[i] * e.vector[i];
			}
			double cosine = dot / (queryNorm * e.norm);
			results.add(new ScoredChunk(e.chunk, cosine));
		}

		Collections.sort(results, new Comparator<ScoredChunk>() {
			@Override
			public int compare(ScoredChunk a, ScoredChunk b) {
				return Double.compare(b.getScore(), a.getScore());
			}
		});

		int limit = topK > 0 ? Math.min(topK, results.size()) : results.size();
		return new ArrayList<ScoredChunk>(results.subList(0, limit));
	}

	@Override
	public int size() {
		return entries.size();
	}

	@Override
	public void clear() {
		entries.clear();
	}

	private static double l2norm(float[] v) {
		double sum = 0.0;
		for (float x : v) {
			sum += (double) x * x;
		}
		return Math.sqrt(sum);
	}
}
