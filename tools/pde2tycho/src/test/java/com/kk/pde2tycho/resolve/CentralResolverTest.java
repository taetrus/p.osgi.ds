package com.kk.pde2tycho.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Parsing is tested on a recorded search.maven.org response; the network is never touched. */
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
}
