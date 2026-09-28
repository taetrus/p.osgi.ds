package com.kk.pde2tycho.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Maven and p2 (InstallableUnit) locations are copied verbatim; Directory and
 * Profile locations are expanded to the jars they contain so scan can identify them.
 */
class TargetReaderTest {

	@TempDir
	Path dir;

	private final List<String> warnings = new ArrayList<>();

	private Path target(String locations) throws IOException {
		return Files.writeString(dir.resolve("t.target"),
				"<?xml version=\"1.0\"?><?pde version=\"3.8\"?><target name=\"t\"><locations>" + locations
						+ "</locations></target>");
	}

	private final TargetReader.Variables vars = (name, arg) -> "project_loc".equals(name) && "/lib".equals(arg)
			? dir.resolve("libproject").toString()
			: null;

	@Test
	void mavenAndP2LocationsAreCopiedVerbatim() throws IOException {
		TargetReader.Content content = TargetReader.read(target(
				"<location type=\"Maven\" missingManifest=\"error\"><dependencies><dependency><groupId>g</groupId>"
						+ "<artifactId>a</artifactId><version>1</version></dependency></dependencies></location>"
						+ "<location type=\"InstallableUnit\"><repository location=\"https://x/\"/><unit id=\"u\" version=\"0.0.0\"/></location>"),
				vars, warnings);
		assertEquals(2, content.locations().size());
		assertTrue(content.locations().get(0).contains("<artifactId>a</artifactId>"), content.locations().get(0));
		assertTrue(content.locations().get(1).contains("type=\"InstallableUnit\""), content.locations().get(1));
		assertTrue(warnings.isEmpty(), warnings.toString());
	}

	@Test
	void directoryLocationListsJarsFromItAndItsPluginsFolder() throws IOException {
		Path lib = dir.resolve("libproject/jars");
		TestJars.bundle(lib.resolve("a.jar"), "org.a", "1.0.0");
		TestJars.bundle(lib.resolve("plugins/b.jar"), "org.b", "1.0.0");
		Files.writeString(lib.resolve("notes.txt"), "ignored");
		TargetReader.Content content = TargetReader.read(
				target("<location path=\"${project_loc:/lib}/jars\" type=\"Directory\"/>"), vars, warnings);
		assertEquals(List.of(lib.resolve("a.jar"), lib.resolve("plugins/b.jar")), content.jars());
	}

	@Test
	void unknownVariableSkipsTheLocationWithWarning() throws IOException {
		TargetReader.Content content = TargetReader.read(
				target("<location path=\"${eclipse_home}\" type=\"Profile\"/>"), vars, warnings);
		assertTrue(content.jars().isEmpty());
		assertTrue(warnings.get(0).contains("--eclipse-home"), warnings.get(0));
	}

	@Test
	void missingDirectoryIsWarned() throws IOException {
		TargetReader.read(target("<location path=\"" + dir.resolve("nope") + "\" type=\"Directory\"/>"), vars, warnings);
		assertTrue(warnings.get(0).contains("does not exist"), warnings.get(0));
	}

	@Test
	void unpackedBundleDirectoryIsWarned() throws IOException {
		Path lib = dir.resolve("libproject/jars");
		Files.createDirectories(lib.resolve("org.c_1.0.0/META-INF"));
		Files.writeString(lib.resolve("org.c_1.0.0/META-INF/MANIFEST.MF"), "Bundle-SymbolicName: org.c\n");
		TargetReader.read(target("<location path=\"${project_loc:/lib}/jars\" type=\"Directory\"/>"), vars, warnings);
		assertTrue(warnings.get(0).contains("Unpacked bundle directory"), warnings.get(0));
	}

	@Test
	void unsupportedLocationTypeIsWarned() throws IOException {
		TargetReader.read(target("<location id=\"f\" path=\"/x\" type=\"Feature\"/>"), vars, warnings);
		assertTrue(warnings.get(0).contains("'Feature'"), warnings.get(0));
	}
}
