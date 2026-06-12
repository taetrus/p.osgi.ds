package com.kk.pde.ds.rag.internal;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.rag.api.Chunk;
import com.kk.pde.ds.rag.api.TextChunker;

/**
 * Splits text into overlapping chunks on paragraph then sentence boundaries.
 *
 * <p>Defaults target ~400 tokens with ~12% overlap. The 400-token target is
 * deliberately below the 512-token input window of {@code multilingual-e5-large}
 * (the default embedding model): chunking larger would let the model silently
 * truncate each passage and lose content. Sizes are tunable via system
 * properties — useful if you switch to a longer-context embedding model:</p>
 *
 * <ul>
 *   <li>{@code rag.chunk.target.tokens} (default 400)</li>
 *   <li>{@code rag.chunk.overlap.tokens} (default 50)</li>
 *   <li>{@code rag.chunk.chars.per.token} (default 4)</li>
 * </ul>
 *
 * <p>Token counts are approximated as characters / charsPerToken — good enough
 * for boundary decisions without bundling a model-specific tokenizer.</p>
 */
@Component(service = TextChunker.class)
public class SlidingWindowChunker implements TextChunker {

	private static final Logger LOG = LoggerFactory.getLogger(SlidingWindowChunker.class);

	private int targetChars;
	private int overlapChars;

	@Activate
	public void activate() {
		int targetTokens = intProp("rag.chunk.target.tokens", 400);
		int overlapTokens = intProp("rag.chunk.overlap.tokens", 50);
		int charsPerToken = intProp("rag.chunk.chars.per.token", 4);
		this.targetChars = Math.max(200, targetTokens * charsPerToken);
		this.overlapChars = Math.max(0, Math.min(overlapTokens * charsPerToken, targetChars / 2));
		LOG.info("SlidingWindowChunker activated (targetChars={}, overlapChars={})",
			targetChars, overlapChars);
	}

	@Override
	public List<Chunk> chunk(String text, String source) {
		List<Chunk> chunks = new ArrayList<Chunk>();
		if (text == null) {
			return chunks;
		}

		List<String> segments = toSegments(text);
		if (segments.isEmpty()) {
			return chunks;
		}

		int i = 0;
		int ordinal = 0;
		while (i < segments.size()) {
			StringBuilder cur = new StringBuilder();
			int j = i;
			while (j < segments.size()) {
				String seg = segments.get(j);
				if (cur.length() > 0 && cur.length() + 1 + seg.length() > targetChars) {
					break;
				}
				if (cur.length() > 0) {
					cur.append(' ');
				}
				cur.append(seg);
				j++;
			}

			String chunkText = cur.toString().trim();
			if (!chunkText.isEmpty()) {
				String id = source + "#" + ordinal;
				String location = "chunk " + (ordinal + 1);
				chunks.add(new Chunk(id, chunkText, source, ordinal, location));
				ordinal++;
			}

			if (j >= segments.size()) {
				break;
			}

			// Start the next chunk a few segments back so chunks overlap.
			int nextStart = j;
			int acc = 0;
			while (nextStart > i + 1 && acc < overlapChars) {
				nextStart--;
				acc += segments.get(nextStart).length() + 1;
			}
			i = nextStart; // strictly greater than the previous i => always progresses
		}

		return chunks;
	}

	/**
	 * Break text into bounded segments: paragraphs first, oversized paragraphs
	 * into sentences, and any still-oversized sentence hard-split by length.
	 * Each returned segment has its internal whitespace collapsed.
	 */
	private List<String> toSegments(String text) {
		List<String> segments = new ArrayList<String>();
		// Paragraphs are separated by a blank line.
		String[] paragraphs = text.split("\\r?\\n[ \\t]*\\r?\\n");
		for (String para : paragraphs) {
			String p = collapse(para);
			if (p.isEmpty()) {
				continue;
			}
			if (p.length() <= targetChars) {
				segments.add(p);
			} else {
				addSentences(p, segments);
			}
		}
		return segments;
	}

	private void addSentences(String paragraph, List<String> out) {
		BreakIterator it = BreakIterator.getSentenceInstance(Locale.ROOT);
		it.setText(paragraph);
		int start = it.first();
		for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
			String sentence = paragraph.substring(start, end).trim();
			if (sentence.isEmpty()) {
				continue;
			}
			if (sentence.length() <= targetChars) {
				out.add(sentence);
			} else {
				// A single sentence longer than the window: hard-split by length.
				for (int k = 0; k < sentence.length(); k += targetChars) {
					out.add(sentence.substring(k, Math.min(k + targetChars, sentence.length())));
				}
			}
		}
	}

	/** Collapse all runs of whitespace (including newlines) to single spaces. */
	private static String collapse(String s) {
		return s.replaceAll("\\s+", " ").trim();
	}

	private static int intProp(String key, int def) {
		try {
			String v = System.getProperty(key);
			return (v == null || v.isEmpty()) ? def : Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
