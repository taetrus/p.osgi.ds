package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders src/main/resources/templates/* by replacing @TOKEN@ placeholders. Line endings
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
		for (Map.Entry<String, String> e : values.entrySet()) {
			text = text.replace("@" + e.getKey() + "@", e.getValue());
		}
		Matcher leftover = PLACEHOLDER.matcher(text);
		if (leftover.find()) {
			throw new IllegalStateException("Template " + name + " has no value for " + leftover.group());
		}
		return text;
	}
}
