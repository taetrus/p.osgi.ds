package com.kk.pde.ds.rag.api;

/** Summary of an ingestion run, suitable for reporting back to a caller/LLM. */
public final class IngestResult {

	private final int filesIngested;
	private final int filesFailed;
	private final int chunksAdded;
	private final String detail;

	public IngestResult(int filesIngested, int filesFailed, int chunksAdded, String detail) {
		this.filesIngested = filesIngested;
		this.filesFailed = filesFailed;
		this.chunksAdded = chunksAdded;
		this.detail = detail;
	}

	public int getFilesIngested() {
		return filesIngested;
	}

	public int getFilesFailed() {
		return filesFailed;
	}

	public int getChunksAdded() {
		return chunksAdded;
	}

	/** Human-readable, possibly multi-line breakdown (per-file notes, errors). */
	public String getDetail() {
		return detail;
	}

	@Override
	public String toString() {
		return "Ingested " + filesIngested + " file(s), " + filesFailed
			+ " failed, " + chunksAdded + " chunk(s) added.";
	}
}
