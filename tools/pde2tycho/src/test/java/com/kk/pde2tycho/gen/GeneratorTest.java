package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Inventory;

/** End-to-end over the generators: the output tree a user runs mvn clean verify in. */
class GeneratorTest {

	@TempDir
	Path tmp;

	Path project() throws Exception {
		Path api = tmp.resolve("src/com.x.api");
		Files.createDirectories(api.resolve("META-INF"));
		Files.writeString(api.resolve("META-INF/MANIFEST.MF"), "Bundle-SymbolicName: com.x.api\n");
		return api;
	}

	@Test
	void writesTheWholeBuild() throws Exception {
		Inventory inv = GenFixtures.simple(project().toString());
		Path out = tmp.resolve("out");
		Generator.generate(inv, out, false);
		for (String file : new String[] { "pom.xml", ".gitignore", "MIGRATION-REPORT.md", "com.x.target/pom.xml",
				"com.x.target/com.x.target.target", "com.x.api/pom.xml", "com.x.api/META-INF/MANIFEST.MF",
				"distribution/pom.xml", "distribution/category.xml", "distribution/com.x.product.product",
				"distribution/scripts/run.sh", "distribution/scripts/run.bat" }) {
			assertTrue(Files.exists(out.resolve(file)), file);
		}
		String parent = Files.readString(out.resolve("pom.xml"));
		assertTrue(parent.indexOf("<module>com.x.target</module>") < parent.indexOf("<module>distribution</module>"));
		assertTrue(Files.readString(out.resolve("MIGRATION-REPORT.md")).contains("-XstartOnFirstThread"));
	}

	@Test
	void refusesANonEmptyDirectoryWithoutForce() throws Exception {
		Inventory inv = GenFixtures.simple(project().toString());
		Path out = Files.createDirectories(tmp.resolve("out"));
		Files.writeString(out.resolve("keep.txt"), "mine");
		GenerationException e = assertThrows(GenerationException.class, () -> Generator.generate(inv, out, false));
		assertTrue(e.errors().get(0).contains("--force"));
		Generator.generate(inv, out, true);
		assertTrue(Files.exists(out.resolve("keep.txt")), "--force overwrites but never deletes");
	}
}
