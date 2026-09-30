package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders src/main/resources/templates/* by replacing @TOKEN@ placeholders in one pass, so a
 * value is inserted verbatim even when it contains "@X@" or "$". Line endings
 * are normalised to LF on load: the repo's .gitattributes checks *.bat out with CRLF.
 */
final class Templates {

	private static final Pattern PLACEHOLDER = Pattern.compile("@[A-Z_]+@");

	private Templates() {
	}

	static String render(String name, Map<String, String> values) {
		String text;
		try (InputStream in = Templates.class.getResourceAsStream("/templates/" + name)) {
			if (in == null) {
				throw new IllegalStateException("Missing template " + name);
			}
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Matcher m = PLACEHOLDER.matcher(text);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String value = values.get(m.group().substring(1, m.group().length() - 1));
			if (value == null) {
				throw new IllegalStateException("Template " + name + " has no value for " + m.group());
			}
			m.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		m.appendTail(out);
		return out.toString();
	}
}
