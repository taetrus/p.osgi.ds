package com.kk.pde2tycho.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * migration.json is the one file people edit between scan and generate, so it must
 * round-trip every field exactly and reject hand-made mistakes with a readable message.
 */
class InventoryJsonTest {

	static Inventory sample() {
		Project api = new Project("com.x.api", Kind.PLUGIN, "/src/com.x.api", "com.x.api", "1.0.0.qualifier",
				"JavaSE-1.8", List.of(), true);
		Project feature = new Project("com.x.feature", Kind.FEATURE, "/src/com.x.feature", "com.x.feature",
				"1.0.0.qualifier", null, List.of("com.x.api"), false);
		Target target = new Target("/src/t/t.target", List.of("<location type=\"Maven\"/>"),
				List.of(new TargetJar("/lib/a.jar", "aa", "org.a", "1.0.0", "org.a:a:1.0.0", null)),
				List.of(new TargetJar("/lib/b.jar", "bb", "com.b", "2.0.0", null, "not on Maven Central")));
		Launch launch = new Launch("app", true, "com.x.product", false,
				List.of(new LaunchBundle("com.x.api", 4, true, true), new LaunchBundle("org.a", null, null, false)),
				"-Dfoo=\"a b\"", "-consoleLog", "JavaSE-21");
		return new Inventory("/ws", "com.x", "1.0.0-SNAPSHOT", "4.0.13", List.of("macosx/cocoa/aarch64"),
				List.of(api, feature), target, List.of(launch), List.of("a warning"));
	}

	@Test
	void roundTripPreservesEveryField() {
		Inventory original = sample();
		assertEquals(original, InventoryJson.read(InventoryJson.write(original)));
	}

	@Test
	void writesHumanEditableJson() {
		String json = InventoryJson.write(sample());
		assertTrue(json.contains("\n  \"projects\": [\n"), json);
		assertTrue(json.contains("\"kind\": \"plugin\""), json);
		assertTrue(json.contains("\"level\": null"), json);
	}

	@Test
	void malformedJsonFailsWithReadableMessage() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> InventoryJson.read("{\"projects\": [}"));
		assertTrue(e.getMessage().startsWith("migration.json is not valid JSON"), e.getMessage());
	}

	@Test
	void unknownKindIsRejectedByName() {
		String json = InventoryJson.write(sample()).replace("\"kind\": \"plugin\"", "\"kind\": \"plugn\"");
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> InventoryJson.read(json));
		assertTrue(e.getMessage().contains("\"plugn\""), e.getMessage());
	}

	@Test
	void wrongTypeIsRejectedByKey() {
		String json = InventoryJson.write(sample()).replace("\"selected\": true", "\"selected\": \"yes\"");
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> InventoryJson.read(json));
		assertTrue(e.getMessage().contains("\"selected\""), e.getMessage());
	}
}
