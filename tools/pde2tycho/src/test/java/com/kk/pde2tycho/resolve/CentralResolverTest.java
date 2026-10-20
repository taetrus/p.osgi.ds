package com.kk.pde2tycho.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntToLongFunction;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Parsing is tested on a recorded search.maven.org response, and timing on a local HTTP server;
 * the real network is never touched.
 */
class CentralResolverTest {

	/** Recorded 2026-09-28: q=1:377d592e740dc77124e0901291dbfaa6810a200e (commons-io 2.16.1), trimmed. */
	static final String HIT = "{\"responseHeader\":{\"status\":0},\"response\":{\"numFound\":1,\"start\":0,"
			+ "\"docs\":[{\"id\":\"commons-io:commons-io:2.16.1\",\"g\":\"commons-io\",\"a\":\"commons-io\","
			+ "\"v\":\"2.16.1\",\"p\":\"jar\",\"timestamp\":1712280332000,\"ec\":[\".jar\"],\"tags\":[]}]}}";

	static final String MISS = "{\"responseHeader\":{\"status\":0},\"response\":{\"numFound\":0,\"start\":0,\"docs\":[]}}";

	@Test
	void hitBecomesCoordinates() throws IOException {
		assertEquals(Optional.of("commons-io:commons-io:2.16.1"), CentralResolver.parse(HIT));
	}

	@Test
	void missIsEmpty() throws IOException {
		assertEquals(Optional.empty(), CentralResolver.parse(MISS));
	}

	@Test
	void garbageIsAnIoError() {
		assertThrows(IOException.class, () -> CentralResolver.parse("<html>502 Bad Gateway</html>"));
	}

	@Test
	void offlineResolverNeverFinds() throws IOException {
		assertEquals(Optional.empty(), ArtifactResolver.OFFLINE.findBySha1("377d592e740dc77124e0901291dbfaa6810a200e"));
	}

	/**
	 * search.maven.org answers one request in 0.2 s and the next one for the same checksum in
	 * 17 s, 30 s or never (measured 2026-10-20, over HTTP/1.1 and HTTP/2). A retry is usually
	 * fast, so a short timeout with retries beats one long wait.
	 */
	@Test
	void hangingRequestsAreRetriedUntilOneAnswers() throws IOException {
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = server(n -> n <= 2 ? 2000 : 0, requests);
		try {
			CentralResolver resolver = new CentralResolver(base(server), Duration.ofMillis(300), 4);
			assertEquals(Optional.of("commons-io:commons-io:2.16.1"), resolver.findBySha1("abc"));
			assertEquals(3, requests.get());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void everyAttemptTimingOutIsAnIoErrorAfterTheLastAttempt() throws IOException {
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = server(n -> 2000, requests);
		try {
			CentralResolver resolver = new CentralResolver(base(server), Duration.ofMillis(200), 3);
			IOException e = assertThrows(IOException.class, () -> resolver.findBySha1("abc"));
			assertTrue(e.getMessage().contains("timed out"), e.getMessage());
			assertEquals(3, requests.get());
		} finally {
			server.stop(0);
		}
	}

	@Test
	void httpErrorIsNotRetried() throws IOException {
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			exchange.sendResponseHeaders(502, -1);
			exchange.close();
		});
		server.start();
		try {
			CentralResolver resolver = new CentralResolver(base(server), Duration.ofSeconds(5), 4);
			assertThrows(IOException.class, () -> resolver.findBySha1("abc"));
			assertEquals(1, requests.get());
		} finally {
			server.stop(0);
		}
	}

	/** Answers request n (from 1) with {@link #HIT} after delay.applyAsLong(n) milliseconds. */
	private static HttpServer server(IntToLongFunction delay, AtomicInteger requests) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool());
		server.createContext("/", exchange -> {
			try {
				Thread.sleep(delay.applyAsLong(requests.incrementAndGet()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] body = HIT.getBytes(StandardCharsets.UTF_8);
			try (OutputStream out = exchange.getResponseBody()) {
				exchange.sendResponseHeaders(200, body.length);
				out.write(body);
			} catch (IOException e) {
				// the client gave up on this request; nothing to answer
			}
		});
		server.start();
		return server;
	}

	private static String base(HttpServer server) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/select?q=1:";
	}
}
