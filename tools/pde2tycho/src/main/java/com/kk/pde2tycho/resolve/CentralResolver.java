package com.kk.pde2tycho.resolve;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.kk.pde2tycho.json.Json;

/**
 * Maven Central's checksum search. Response times vary from 0.2 s to 30 s (measured),
 * so each request gets a generous timeout and the caller decides when to give up.
 */
public final class CentralResolver implements ArtifactResolver {

	private static final String SEARCH = "https://search.maven.org/solrsearch/select?rows=1&wt=json&q=1:";

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@Override
	public Optional<String> findBySha1(String sha1) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH + sha1)).timeout(Duration.ofSeconds(30)).GET()
				.build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IOException("Maven Central search returned HTTP " + response.statusCode());
			}
			return parse(response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while querying Maven Central", e);
		}
	}

	static Optional<String> parse(String body) throws IOException {
		Object docs;
		try {
			docs = Json.get(Json.get(Json.parse(body), "response"), "docs");
		} catch (Json.JsonException e) {
			throw new IOException("Unexpected Maven Central response: " + e.getMessage(), e);
		}
		if (!(docs instanceof List) || ((List<?>) docs).isEmpty()) {
			if (docs == null) {
				throw new IOException("Unexpected Maven Central response: no response.docs");
			}
			return Optional.empty();
		}
		Object doc = ((List<?>) docs).get(0);
		String g = Json.getString(doc, "g");
		String a = Json.getString(doc, "a");
		String v = Json.getString(doc, "v");
		return g == null || a == null || v == null ? Optional.empty() : Optional.of(g + ":" + a + ":" + v);
	}
}
