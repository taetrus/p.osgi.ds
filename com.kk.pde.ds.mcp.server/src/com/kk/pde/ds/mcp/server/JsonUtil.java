package com.kk.pde.ds.mcp.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON utilities for MCP JSON-RPC handling.
 * Not a general-purpose parser — targeted at the predictable structure of MCP messages.
 */
final class JsonUtil {

	private JsonUtil() {
	}

	/**
	 * Extract a string value for the given key from a JSON object string.
	 * Returns null if the key is not found or the value is not a string.
	 */
	static String getString(String json, String key) {
		if (json == null) return null;
		String search = "\"" + key + "\"";
		int keyIdx = json.indexOf(search);
		if (keyIdx < 0) return null;

		int colonIdx = json.indexOf(':', keyIdx + search.length());
		if (colonIdx < 0) return null;

		// Skip whitespace after colon
		int i = colonIdx + 1;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

		if (i >= json.length()) return null;

		char c = json.charAt(i);
		if (c == '"') {
			return extractQuotedString(json, i);
		}
		// Could be a number (for "id" field) — read until comma, brace, or bracket
		if (c == 'n' && json.startsWith("null", i)) return null;

		int end = i;
		while (end < json.length()) {
			char ch = json.charAt(end);
			if (ch == ',' || ch == '}' || ch == ']' || Character.isWhitespace(ch)) break;
			end++;
		}
		return json.substring(i, end);
	}

	/**
	 * Extract a nested JSON object for the given key as a raw string.
	 * Returns null if the key is not found.
	 */
	static String getObject(String json, String key) {
		if (json == null) return null;
		String search = "\"" + key + "\"";
		int keyIdx = json.indexOf(search);
		if (keyIdx < 0) return null;

		int colonIdx = json.indexOf(':', keyIdx + search.length());
		if (colonIdx < 0) return null;

		int i = colonIdx + 1;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

		if (i >= json.length()) return null;

		char c = json.charAt(i);
		if (c == '{') return extractBracketed(json, i, '{', '}');
		if (c == '[') return extractBracketed(json, i, '[', ']');
		if (c == '"') return extractQuotedString(json, i);
		if (c == 'n' && json.startsWith("null", i)) return null;

		// Primitive value
		int end = i;
		while (end < json.length()) {
			char ch = json.charAt(end);
			if (ch == ',' || ch == '}' || ch == ']') break;
			end++;
		}
		return json.substring(i, end).trim();
	}

	/**
	 * Parse a flat JSON object {"key":"value", ...} into a Map.
	 * Only handles string values. Suitable for tool arguments.
	 */
	static Map<String, String> parseFlat(String json) {
		if (json == null || json.trim().isEmpty()) return Collections.emptyMap();

		String trimmed = json.trim();
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
			return Collections.emptyMap();
		}

		Map<String, String> result = new LinkedHashMap<String, String>();
		int i = 1; // skip opening brace
		int len = trimmed.length() - 1; // skip closing brace

		while (i < len) {
			// Skip whitespace and commas
			while (i < len && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ',')) i++;
			if (i >= len) break;

			// Expect a quoted key
			if (trimmed.charAt(i) != '"') break;
			String key = extractQuotedString(trimmed, i);
			if (key == null) break;
			i += key.length() + 2; // skip key + two quotes

			// Skip colon and whitespace
			while (i < len && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ':')) i++;
			if (i >= len) break;

			// Extract value
			if (trimmed.charAt(i) == '"') {
				String value = extractQuotedString(trimmed, i);
				if (value == null) break;
				result.put(key, value);
				i += value.length() + 2;
			} else if (trimmed.charAt(i) == 'n' && trimmed.startsWith("null", i)) {
				result.put(key, null);
				i += 4;
			} else {
				// Number or boolean — read until delimiter
				int start = i;
				while (i < len && trimmed.charAt(i) != ',' && trimmed.charAt(i) != '}') i++;
				result.put(key, trimmed.substring(start, i).trim());
			}
		}
		return result;
	}

	/** Escape a string for safe inclusion in JSON. */
	static String escape(String value) {
		if (value == null) return "";
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	/**
	 * Extract a quoted string starting at position i (which must be a quote char).
	 * Handles escaped quotes within the string.
	 */
	private static String extractQuotedString(String json, int i) {
		if (i >= json.length() || json.charAt(i) != '"') return null;
		int start = i + 1;
		int end = start;
		while (end < json.length()) {
			char c = json.charAt(end);
			if (c == '\\') {
				end += 2; // skip escaped char
				continue;
			}
			if (c == '"') {
				return json.substring(start, end);
			}
			end++;
		}
		return null;
	}

	/**
	 * Extract a bracketed expression (object or array) starting at position i.
	 * Tracks nesting depth and respects quoted strings.
	 */
	private static String extractBracketed(String json, int i, char open, char close) {
		if (i >= json.length() || json.charAt(i) != open) return null;
		int depth = 0;
		int pos = i;
		boolean inString = false;
		while (pos < json.length()) {
			char c = json.charAt(pos);
			if (inString) {
				if (c == '\\') {
					pos += 2;
					continue;
				}
				if (c == '"') inString = false;
			} else {
				if (c == '"') inString = true;
				else if (c == open) depth++;
				else if (c == close) {
					depth--;
					if (depth == 0) return json.substring(i, pos + 1);
				}
			}
			pos++;
		}
		return null;
	}
}
