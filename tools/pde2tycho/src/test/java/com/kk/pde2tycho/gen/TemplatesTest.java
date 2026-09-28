package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** A forgotten placeholder must fail loudly instead of leaking "@X@" into a pom. */
class TemplatesTest {

	@Test
	void unfilledPlaceholderFails() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> Templates.render("module-pom.xml", Map.of("GROUP_ID", "g")));
		assertTrue(e.getMessage().contains("@VERSION@"), e.getMessage());
	}

	@Test
	void missingTemplateFails() {
		assertThrows(IllegalStateException.class, () -> Templates.render("nope.xml", Map.of()));
	}
}
