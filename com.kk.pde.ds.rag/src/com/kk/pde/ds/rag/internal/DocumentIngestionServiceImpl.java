package com.kk.pde.ds.rag.internal;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.rag.api.Chunk;
import com.kk.pde.ds.rag.api.DocumentIngestionService;
import com.kk.pde.ds.rag.api.DocumentParser;
import com.kk.pde.ds.rag.api.EmbeddingClient;
import com.kk.pde.ds.rag.api.IngestResult;
import com.kk.pde.ds.rag.api.ScoredChunk;
import com.kk.pde.ds.rag.api.TextChunker;
import com.kk.pde.ds.rag.api.VectorStore;

/**
 * Orchestrates parse &rarr; chunk &rarr; embed &rarr; store, and serves retrieval
 * queries. Collaborators are injected as OSGi services so any one of them
 * (parser, chunker, embedding backend, vector store) can be swapped independently.
 *
 * <p>If {@code -Drag.docs.dir} is set, its contents are ingested at activation on
 * a background daemon thread — embedding calls are network I/O and must not block
 * the OSGi framework's startup.</p>
 */
@Component(service = DocumentIngestionService.class)
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

	private static final Logger LOG = LoggerFactory.getLogger(DocumentIngestionServiceImpl.class);

	private DocumentParser parser;
	private TextChunker chunker;
	private EmbeddingClient embeddingClient;
	private VectorStore vectorStore;

	@Reference
	public void setParser(DocumentParser parser) {
		this.parser = parser;
	}

	@Reference
	public void setChunker(TextChunker chunker) {
		this.chunker = chunker;
	}

	@Reference
	public void setEmbeddingClient(EmbeddingClient embeddingClient) {
		this.embeddingClient = embeddingClient;
	}

	@Reference
	public void setVectorStore(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Activate
	public void activate() {
		LOG.info("DocumentIngestionService activated");
		final String docsDir = System.getProperty("rag.docs.dir", "");
		if (!docsDir.isEmpty()) {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					LOG.info("Auto-ingesting documents from {}", docsDir);
					IngestResult result = ingestPath(docsDir);
					LOG.info("Auto-ingest complete: {}", result);
				}
			}, "rag-startup-ingest");
			t.setDaemon(true);
			t.start();
		}
	}

	@Override
	public IngestResult ingestPath(String path) {
		if (path == null || path.trim().isEmpty()) {
			return new IngestResult(0, 0, 0, "No path provided.");
		}
		Path root = Paths.get(path.trim());
		if (!Files.exists(root)) {
			return new IngestResult(0, 0, 0, "Path does not exist: " + root);
		}

		List<Path> files = new ArrayList<Path>();
		try {
			collectFiles(root, files);
		} catch (IOException e) {
			return new IngestResult(0, 0, 0, "Failed to scan path: " + e.getMessage());
		}
		if (files.isEmpty()) {
			return new IngestResult(0, 0, 0, "No supported documents found under " + root);
		}

		int ingested = 0;
		int failed = 0;
		int chunksAdded = 0;
		StringBuilder detail = new StringBuilder();

		for (Path file : files) {
			try {
				int added = ingestFile(file);
				ingested++;
				chunksAdded += added;
				detail.append("  + ").append(file.getFileName()).append(" -> ")
					.append(added).append(" chunk(s)\n");
			} catch (Exception e) {
				failed++;
				detail.append("  ! ").append(file.getFileName()).append(" -> ")
					.append(e.getMessage()).append('\n');
				LOG.warn("Failed to ingest {}", file, e);
			}
		}

		return new IngestResult(ingested, failed, chunksAdded, detail.toString().trim());
	}

	/** Parse, chunk, embed and store one file. Returns number of chunks added. */
	private int ingestFile(Path file) throws IOException {
		String text = parser.extractText(file);
		if (text == null || text.trim().isEmpty()) {
			return 0;
		}
		String source = file.getFileName().toString();
		List<Chunk> chunks = chunker.chunk(text, source);
		if (chunks.isEmpty()) {
			return 0;
		}

		List<String> texts = new ArrayList<String>(chunks.size());
		for (Chunk c : chunks) {
			texts.add(c.getText());
		}
		List<float[]> vectors = embeddingClient.embedPassages(texts);

		int added = 0;
		for (int i = 0; i < chunks.size(); i++) {
			float[] vec = (i < vectors.size()) ? vectors.get(i) : null;
			if (vec != null) {
				vectorStore.add(chunks.get(i), vec);
				added++;
			}
		}
		if (added < chunks.size()) {
			throw new IOException("embedded " + added + "/" + chunks.size()
				+ " chunks (embedding backend unavailable?)");
		}
		return added;
	}

	@Override
	public List<ScoredChunk> search(String query, int topK) {
		if (query == null || query.trim().isEmpty()) {
			return Collections.emptyList();
		}
		float[] queryVec = embeddingClient.embedQuery(query.trim());
		if (queryVec == null) {
			LOG.warn("Query embedding failed; returning no results");
			return Collections.emptyList();
		}
		return vectorStore.search(queryVec, topK);
	}

	@Override
	public int chunkCount() {
		return vectorStore.size();
	}

	private void collectFiles(Path root, List<Path> out) throws IOException {
		if (Files.isRegularFile(root)) {
			if (parser.supports(root)) {
				out.add(root);
			}
			return;
		}
		// walkFileTree (not Files.walk) so a single unreadable file or directory
		// is logged and skipped instead of aborting the entire ingestion.
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
				if (attrs.isRegularFile() && parser.supports(p)) {
					out.add(p);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path p, IOException exc) {
				// Skip the unreadable entry but keep walking the rest of the tree.
				LOG.warn("Skipping unreadable path during ingestion: {} ({})", p, exc.getMessage());
				return FileVisitResult.CONTINUE;
			}
		});
		// Deterministic order for reproducible chunk ids.
		Collections.sort(out, new Comparator<Path>() {
			@Override
			public int compare(Path a, Path b) {
				return a.toString().compareTo(b.toString());
			}
		});
	}
}
