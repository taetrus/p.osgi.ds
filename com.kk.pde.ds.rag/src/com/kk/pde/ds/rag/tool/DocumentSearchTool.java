package com.kk.pde.ds.rag.tool;

import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;
import com.kk.pde.ds.rag.api.DocumentIngestionService;
import com.kk.pde.ds.rag.api.ScoredChunk;

/**
 * Retrieval-as-a-tool: the LLM calls {@code document_search} when it decides it
 * needs grounding from the ingested documents. Returns the top matching chunks
 * with a source citation each, so the model can attribute its answer.
 *
 * <p>Registering as a plain {@code IMcpTool} {@code @Component} is all that's
 * needed — {@code McpToolRegistry} collects it dynamically and {@code OpenRouterAgent}
 * exposes it to the model automatically.</p>
 */
@Component(service = IMcpTool.class)
public class DocumentSearchTool implements IMcpTool {

	private static final Logger LOG = LoggerFactory.getLogger(DocumentSearchTool.class);
	private static final int DEFAULT_TOP_K = 5;
	private static final int MAX_CHUNK_CHARS = 800;

	private DocumentIngestionService ingestion;

	@Reference
	public void setIngestion(DocumentIngestionService ingestion) {
		this.ingestion = ingestion;
	}

	@Activate
	public void activate() {
		LOG.info("DocumentSearchTool activated");
	}

	@Override
	public String getName() {
		return "document_search";
	}

	@Override
	public String getDescription() {
		return "Searches the ingested local documents (PDF/Word/PowerPoint/text/HTML) "
			+ "and returns the most relevant passages with their source citations. "
			+ "Use this to answer questions grounded in the user's documents.";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{"
			+ "\"query\":{\"type\":\"string\",\"description\":\"What to search for\"},"
			+ "\"top_k\":{\"type\":\"integer\",\"description\":\"How many passages to return (default 5)\"}"
			+ "},\"required\":[\"query\"]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		String query = arguments.get("query");
		if (query == null || query.trim().isEmpty()) {
			return "Error: 'query' is required";
		}
		if (ingestion.chunkCount() == 0) {
			return "The document knowledge base is empty. Ingest documents first "
				+ "(set -Drag.docs.dir at startup, or use the ingest_documents tool).";
		}

		int topK = parseTopK(arguments.get("top_k"));
		List<ScoredChunk> hits = ingestion.search(query, topK);
		if (hits.isEmpty()) {
			return "No relevant passages found for: " + query;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("Found ").append(hits.size()).append(" relevant passage(s):\n\n");
		int n = 1;
		for (ScoredChunk hit : hits) {
			sb.append("[").append(n++).append("] ")
				.append(hit.getChunk().getCitation())
				.append("  (score ").append(String.format("%.3f", hit.getScore())).append(")\n");
			sb.append(truncate(hit.getChunk().getText())).append("\n\n");
		}
		return sb.toString().trim();
	}

	private int parseTopK(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return DEFAULT_TOP_K;
		}
		try {
			int k = Integer.parseInt(raw.trim());
			return (k > 0 && k <= 20) ? k : DEFAULT_TOP_K;
		} catch (NumberFormatException e) {
			return DEFAULT_TOP_K;
		}
	}

	private static String truncate(String text) {
		if (text.length() <= MAX_CHUNK_CHARS) {
			return text;
		}
		return text.substring(0, MAX_CHUNK_CHARS) + " …";
	}
}
