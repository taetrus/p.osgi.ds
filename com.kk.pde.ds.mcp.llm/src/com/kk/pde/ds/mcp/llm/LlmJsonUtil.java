package com.kk.pde.ds.mcp.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON utilities for parsing LLM API responses and building requests.
 * Extended from the server's JsonUtil pattern with array support needed for
 * OpenRouter's choices/tool_calls response structure.
 */
public final class LlmJsonUtil {

	private LlmJsonUtil() {
	}

	/**
	 * Extract a string value for the given key from a JSON object string.
	 */
	public static String getString(String json, String key) {
		if (json == null) return null;
		int colonIdx = findKeyColon(json, key);
		if (colonIdx < 0) return null;

		int i = colonIdx + 1;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
		if (i >= json.length()) return null;

		char c = json.charAt(i);
		if (c == '"') return unescape(extractQuotedString(json, i));
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
	 * Extract a nested JSON object or array for the given key as a raw string.
	 */
	public static String getObject(String json, String key) {
		if (json == null) return null;
		int colonIdx = findKeyColon(json, key);
		if (colonIdx < 0) return null;

		int i = colonIdx + 1;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
		if (i >= json.length()) return null;

		char c = json.charAt(i);
		if (c == '{') return extractBracketed(json, i, '{', '}');
		if (c == '[') return extractBracketed(json, i, '[', ']');
		if (c == '"') return extractQuotedString(json, i);
		if (c == 'n' && json.startsWith("null", i)) return null;

		int end = i;
		while (end < json.length()) {
			char ch = json.charAt(end);
			if (ch == ',' || ch == '}' || ch == ']') break;
			end++;
		}
		return json.substring(i, end).trim();
	}

	/**
	 * Find the colon after a JSON key, distinguishing keys from values.
	 * In {"type":"function","function":{...}}, searching for "function"
	 * must skip the VALUE "function" and find the KEY "function".
	 * A key is "text" immediately followed by : (with optional whitespace).
	 * A value is "text" followed by , or } or ].
	 */
	private static int findKeyColon(String json, String key) {
		String search = "\"" + key + "\"";
		int fromIdx = 0;
		while (true) {
			int keyIdx = json.indexOf(search, fromIdx);
			if (keyIdx < 0) return -1;

			// Check if this occurrence is a key (followed by ':')
			int afterQuote = keyIdx + search.length();
			int j = afterQuote;
			while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
			if (j < json.length() && json.charAt(j) == ':') {
				return j;
			}

			// Not a key — it's a value. Keep searching after this occurrence.
			fromIdx = afterQuote;
		}
	}

	/**
	 * Returns the first element of a JSON array string as a raw string.
	 * e.g. "[{...}, {...}]" returns the raw text of the first object.
	 */
	public static String getFirstInArray(String arrayJson) {
		if (arrayJson == null) return null;
		String trimmed = arrayJson.trim();
		if (!trimmed.startsWith("[")) return null;

		int i = 1;
		while (i < trimmed.length() && Character.isWhitespace(trimmed.charAt(i))) i++;
		if (i >= trimmed.length() || trimmed.charAt(i) == ']') return null;

		char c = trimmed.charAt(i);
		if (c == '{') return extractBracketed(trimmed, i, '{', '}');
		if (c == '[') return extractBracketed(trimmed, i, '[', ']');
		if (c == '"') return extractQuotedString(trimmed, i);

		int end = i;
		while (end < trimmed.length() && trimmed.charAt(end) != ',' && trimmed.charAt(end) != ']') end++;
		return trimmed.substring(i, end).trim();
	}

	/**
	 * Returns all elements of a JSON array as a list of raw strings.
	 * e.g. "[{...}, {...}]" returns a list with each object's raw text.
	 */
	public static List<String> getAllInArray(String arrayJson) {
		List<String> results = new ArrayList<String>();
		if (arrayJson == null) return results;
		String trimmed = arrayJson.trim();
		if (!trimmed.startsWith("[")) return results;

		int i = 1;
		while (i < trimmed.length()) {
			while (i < trimmed.length() && (Character.isWhitespace(trimmed.charAt(i))
				|| trimmed.charAt(i) == ',')) i++;
			if (i >= trimmed.length() || trimmed.charAt(i) == ']') break;

			char c = trimmed.charAt(i);
			String element = null;
			if (c == '{') {
				element = extractBracketed(trimmed, i, '{', '}');
			} else if (c == '[') {
				element = extractBracketed(trimmed, i, '[', ']');
			} else if (c == '"') {
				element = extractQuotedString(trimmed, i);
				if (element != null) {
					results.add(element);
					i += element.length() + 2;
					continue;
				}
			} else {
				int end = i;
				while (end < trimmed.length() && trimmed.charAt(end) != ','
					&& trimmed.charAt(end) != ']') end++;
				element = trimmed.substring(i, end).trim();
				results.add(element);
				i = end;
				continue;
			}
			if (element != null) {
				results.add(element);
				i += element.length();
			} else {
				break;
			}
		}
		return results;
	}

	/**
	 * Parse a flat JSON object {"key":"value", ...} into a Map.
	 * Only handles string values. Suitable for tool arguments.
	 */
	public static Map<String, String> parseFlat(String json) {
		if (json == null || json.trim().isEmpty()) return Collections.emptyMap();

		String trimmed = json.trim();
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return Collections.emptyMap();

		Map<String, String> result = new LinkedHashMap<String, String>();
		int i = 1;
		int len = trimmed.length() - 1;

		while (i < len) {
			while (i < len && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ',')) i++;
			if (i >= len) break;

			if (trimmed.charAt(i) != '"') break;
			String key = extractQuotedString(trimmed, i);
			if (key == null) break;
			i += key.length() + 2;

			while (i < len && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ':')) i++;
			if (i >= len) break;

			if (trimmed.charAt(i) == '"') {
				String value = extractQuotedString(trimmed, i);
				if (value == null) break;
				result.put(key, value);
				i += value.length() + 2;
			} else if (trimmed.charAt(i) == 'n' && trimmed.startsWith("null", i)) {
				result.put(key, null);
				i += 4;
			} else {
				int start = i;
				while (i < len && trimmed.charAt(i) != ',' && trimmed.charAt(i) != '}') i++;
				result.put(key, trimmed.substring(start, i).trim());
			}
		}
		return result;
	}

	/** Escape a string for safe inclusion in a JSON string value. */
	public static String escape(String value) {
		if (value == null) return "";
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	/**
	 * Unescape a JSON string value. Converts JSON escape sequences
	 * ({@code \n}, {@code \"}, {@code \\}, etc.) to their actual characters.
	 */
	public static String unescape(String value) {
		if (value == null) return null;
		if (value.indexOf('\\') < 0) return value;
		StringBuilder sb = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length()) {
				char next = value.charAt(i + 1);
				switch (next) {
					case 'n':  sb.append('\n'); i++; break;
					case 'r':  sb.append('\r'); i++; break;
					case 't':  sb.append('\t'); i++; break;
					case '"':  sb.append('"');  i++; break;
					case '\\': sb.append('\\'); i++; break;
					case '/':  sb.append('/');  i++; break;
					default:   sb.append(c); break;
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String extractQuotedString(String json, int i) {
		if (i >= json.length() || json.charAt(i) != '"') return null;
		int start = i + 1;
		int end = start;
		while (end < json.length()) {
			char c = json.charAt(end);
			if (c == '\\') { end += 2; continue; }
			if (c == '"') return json.substring(start, end);
			end++;
		}
		return null;
	}

	private static String extractBracketed(String json, int i, char open, char close) {
		if (i >= json.length() || json.charAt(i) != open) return null;
		int depth = 0;
		int pos = i;
		boolean inString = false;
		while (pos < json.length()) {
			char c = json.charAt(pos);
			if (inString) {
				if (c == '\\') { pos += 2; continue; }
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
