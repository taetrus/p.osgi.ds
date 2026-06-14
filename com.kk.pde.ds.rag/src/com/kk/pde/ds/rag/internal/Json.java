package com.kk.pde.ds.rag.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser/writer.
 *
 * <p>The project's chat client parses JSON with ad-hoc string scanning, which is
 * fine for flat chat payloads but fragile for the deeply-nested numeric arrays
 * an embeddings endpoint returns. This recursive-descent parser handles arbitrary
 * nesting correctly. Parsed values map to: {@link Map}&lt;String,Object&gt;,
 * {@link List}&lt;Object&gt;, {@link String}, {@link Double}, {@link Boolean},
 * or {@code null}.</p>
 */
public final class Json {

	private Json() {
	}

	// ---- Parsing -----------------------------------------------------------

	public static Object parse(String text) {
		Parser p = new Parser(text);
		p.skipWs();
		Object v = p.value();
		p.skipWs();
		if (!p.atEnd()) {
			throw new IllegalArgumentException("Trailing characters at index " + p.pos);
		}
		return v;
	}

	private static final class Parser {
		private final String s;
		private int pos;

		Parser(String s) {
			this.s = s;
		}

		boolean atEnd() {
			return pos >= s.length();
		}

		void skipWs() {
			while (pos < s.length()) {
				char c = s.charAt(pos);
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
					pos++;
				} else {
					break;
				}
			}
		}

		Object value() {
			skipWs();
			if (atEnd()) {
				throw new IllegalArgumentException("Unexpected end of input");
			}
			char c = s.charAt(pos);
			switch (c) {
				case '{':
					return object();
				case '[':
					return array();
				case '"':
					return string();
				case 't':
				case 'f':
					return bool();
				case 'n':
					return nul();
				default:
					return number();
			}
		}

		Map<String, Object> object() {
			Map<String, Object> map = new LinkedHashMap<String, Object>();
			pos++; // consume '{'
			skipWs();
			if (peek() == '}') {
				pos++;
				return map;
			}
			while (true) {
				skipWs();
				String key = string();
				skipWs();
				expect(':');
				Object val = value();
				map.put(key, val);
				skipWs();
				char c = next();
				if (c == '}') {
					break;
				}
				if (c != ',') {
					throw new IllegalArgumentException("Expected ',' or '}' at index " + (pos - 1));
				}
			}
			return map;
		}

		List<Object> array() {
			List<Object> list = new ArrayList<Object>();
			pos++; // consume '['
			skipWs();
			if (peek() == ']') {
				pos++;
				return list;
			}
			while (true) {
				list.add(value());
				skipWs();
				char c = next();
				if (c == ']') {
					break;
				}
				if (c != ',') {
					throw new IllegalArgumentException("Expected ',' or ']' at index " + (pos - 1));
				}
			}
			return list;
		}

		String string() {
			expect('"');
			StringBuilder sb = new StringBuilder();
			while (true) {
				if (atEnd()) {
					throw new IllegalArgumentException("Unterminated string");
				}
				char c = s.charAt(pos++);
				if (c == '"') {
					break;
				}
				if (c == '\\') {
					char e = s.charAt(pos++);
					switch (e) {
						case '"': sb.append('"'); break;
						case '\\': sb.append('\\'); break;
						case '/': sb.append('/'); break;
						case 'b': sb.append('\b'); break;
						case 'f': sb.append('\f'); break;
						case 'n': sb.append('\n'); break;
						case 'r': sb.append('\r'); break;
						case 't': sb.append('\t'); break;
						case 'u':
							sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
							pos += 4;
							break;
						default:
							throw new IllegalArgumentException("Bad escape \\" + e);
					}
				} else {
					sb.append(c);
				}
			}
			return sb.toString();
		}

		Double number() {
			int start = pos;
			while (pos < s.length()) {
				char c = s.charAt(pos);
				if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
					pos++;
				} else {
					break;
				}
			}
			if (start == pos) {
				throw new IllegalArgumentException("Invalid token at index " + pos);
			}
			return Double.valueOf(s.substring(start, pos));
		}

		Boolean bool() {
			if (s.startsWith("true", pos)) {
				pos += 4;
				return Boolean.TRUE;
			}
			if (s.startsWith("false", pos)) {
				pos += 5;
				return Boolean.FALSE;
			}
			throw new IllegalArgumentException("Invalid literal at index " + pos);
		}

		Object nul() {
			if (s.startsWith("null", pos)) {
				pos += 4;
				return null;
			}
			throw new IllegalArgumentException("Invalid literal at index " + pos);
		}

		char peek() {
			skipWs();
			return atEnd() ? '\0' : s.charAt(pos);
		}

		char next() {
			return s.charAt(pos++);
		}

		void expect(char c) {
			skipWs();
			if (atEnd() || s.charAt(pos) != c) {
				throw new IllegalArgumentException("Expected '" + c + "' at index " + pos);
			}
			pos++;
		}
	}

	// ---- Writing -----------------------------------------------------------

	/** Escape a string for embedding inside JSON double quotes (no quotes added). */
	public static String escape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(value.length() + 16);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				default:
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}

	// ---- Navigation helpers ------------------------------------------------

	@SuppressWarnings("unchecked")
	public static Map<String, Object> asObject(Object o) {
		return (o instanceof Map) ? (Map<String, Object>) o : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> asArray(Object o) {
		return (o instanceof List) ? (List<Object>) o : null;
	}
}
