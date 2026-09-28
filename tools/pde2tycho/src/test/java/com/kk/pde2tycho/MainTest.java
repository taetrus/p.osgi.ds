package com.kk.pde2tycho;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.InventoryJson;

/** Exit codes: 0 success, 1 failure, 2 usage error. */
class MainTest {

	@TempDir
	Path tmp;

	final ByteArrayOutputStream out = new ByteArrayOutputStream();
	final ByteArrayOutputStream err = new ByteArrayOutputStream();

	int run(String... args) throws IOException {
		return Main.run(args, new PrintStream(out, true), new PrintStream(err, true));
	}

	@Test
	void noArgumentsPrintsUsage() throws IOException {
		assertEquals(2, run());
		assertTrue(err.toString().contains("pde2tycho scan"), err.toString());
	}

	@Test
	void unknownOptionIsAUsageError() throws IOException {
		assertEquals(2, run("scan", tmp.toString(), "--bogus"));
		assertTrue(err.toString().contains("unknown option --bogus"), err.toString());
	}

	@Test
	void scanWritesAnInventory() throws IOException {
		Files.createDirectories(tmp.resolve("ws/.metadata/.plugins"));
		Path json = tmp.resolve("migration.json");
		assertEquals(0, run("scan", tmp.resolve("ws").toString(), "-o", json.toString(), "--offline"));
		assertEquals("migrated", InventoryJson.read(Files.readString(json)).groupId());
		assertTrue(out.toString().contains("Scanned 0 project(s)"), out.toString());
	}

	@Test
	void generateWithBrokenInventoryExitsOneWithMessage() throws IOException {
		Path json = Files.writeString(tmp.resolve("migration.json"), "{\"projects\": [}");
		IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> run("generate", json.toString(), tmp.resolve("out").toString()));
		assertTrue(e.getMessage().startsWith("migration.json is not valid JSON"), e.getMessage());
	}

	@Test
	void generateReportsValidationErrorsAndExitsOne() throws IOException {
		Files.createDirectories(tmp.resolve("ws/.metadata/.plugins"));
		Path json = tmp.resolve("migration.json");
		run("scan", tmp.resolve("ws").toString(), "-o", json.toString(), "--offline");
		assertEquals(1, run("generate", json.toString(), tmp.resolve("out").toString()));
		assertTrue(err.toString().contains("No projects are selected"), err.toString());
	}
}
