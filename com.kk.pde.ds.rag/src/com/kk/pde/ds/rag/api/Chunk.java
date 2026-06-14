package com.kk.pde.ds.rag.api;

/**
 * An immutable slice of a source document, the unit that gets embedded, stored
 * and retrieved. Carries enough source metadata to cite the originating
 * document in an answer.
 */
public final class Chunk {

	private final String id;
	private final String text;
	private final String source;
	private final int ordinal;
	private final String location;

	/**
	 * @param id       globally-unique chunk id (e.g. "report.pdf#3")
	 * @param text     the chunk text
	 * @param source   originating document name (filename)
	 * @param ordinal  0-based index of this chunk within its document
	 * @param location human-readable location for citation (e.g. "chunk 4")
	 */
	public Chunk(String id, String text, String source, int ordinal, String location) {
		this.id = id;
		this.text = text;
		this.source = source;
		this.ordinal = ordinal;
		this.location = location;
	}

	public String getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	public String getSource() {
		return source;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public String getLocation() {
		return location;
	}

	/** A short "source (location)" label for citing this chunk in an answer. */
	public String getCitation() {
		return source + " (" + location + ")";
	}

	@Override
	public String toString() {
		return "Chunk[" + id + "]";
	}
}
