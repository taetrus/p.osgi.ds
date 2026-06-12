package com.kk.pde.ds.rag.tool;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;
import com.kk.pde.ds.rag.api.DocumentIngestionService;
import com.kk.pde.ds.rag.api.IngestResult;

/**
 * Lets the model (or an operator, via the chat) load documents into the
 * knowledge base on demand by pointing at a file or folder path.
 */
@Component(service = IMcpTool.class)
public class IngestDocumentsTool implements IMcpTool {

	private static final Logger LOG = LoggerFactory.getLogger(IngestDocumentsTool.class);

	private DocumentIngestionService ingestion;

	@Reference
	public void setIngestion(DocumentIngestionService ingestion) {
		this.ingestion = ingestion;
	}

	@Activate
	public void activate() {
		LOG.info("IngestDocumentsTool activated");
	}

	@Override
	public String getName() {
		return "ingest_documents";
	}

	@Override
	public String getDescription() {
		return "Loads documents into the searchable knowledge base from a local file "
			+ "or folder path (folders are scanned recursively). Supported types: "
			+ "PDF, Word, PowerPoint, text, Markdown, HTML.";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{"
			+ "\"path\":{\"type\":\"string\",\"description\":\"Absolute path to a file or folder of documents\"}"
			+ "},\"required\":[\"path\"]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		String path = arguments.get("path");
		if (path == null || path.trim().isEmpty()) {
			return "Error: 'path' is required";
		}
		LOG.info("Ingest request for path: {}", path);
		IngestResult result = ingestion.ingestPath(path);
		StringBuilder sb = new StringBuilder(result.toString());
		if (result.getDetail() != null && !result.getDetail().isEmpty()) {
			sb.append('\n').append(result.getDetail());
		}
		sb.append("\nKnowledge base now holds ").append(ingestion.chunkCount()).append(" chunk(s).");
		return sb.toString();
	}
}
