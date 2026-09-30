package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Target;
import com.kk.pde2tycho.model.TargetJar;

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

	/** --force regenerates: files generate owns from an earlier run must not survive and be built again. */
	@Test
	void forceRemovesStaleProductsAndVendoredJars() throws Exception {
		Path api = project();
		Path oldJar = Files.writeString(tmp.resolve("old.jar"), "old");
		Inventory first = GenFixtures.inventory(List.of(GenFixtures.plugin("com.x.api", api.toString())),
				new Target(null, List.of(), List.of(), List.of(new TargetJar(oldJar.toString(), "s", "org.old", "1.0.0",
						null, "offline"))),
				List.of(new LaunchBundle("com.x.api", 4, true, true), new LaunchBundle("org.old", null, null, false)));
		Path out = tmp.resolve("out");
		Generator.generate(first, out, false);
		assertTrue(Files.exists(out.resolve("com.x.target/vendor/plugins/org.old_1.0.0.jar")));
		Files.createDirectories(out.resolve("com.x.gone"));
		Files.writeString(out.resolve("com.x.gone/pom.xml"), "<project/>");

		Launch renamed = GenFixtures.simple(api.toString()).selectedLaunch().withSelection(true, "com.x.renamed");
		Inventory second = new Inventory("/ws", "com.x", "1.0.0-SNAPSHOT", "4.0.13", first.environments(),
				first.projects(), new Target(null, List.of(), List.of(), List.of()), List.of(renamed), List.of());
		Generator.generate(second, out, true);
		try (Stream<Path> files = Files.list(out.resolve("distribution"))) {
			assertEquals(List.of("com.x.renamed.product"),
					files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".product")).toList());
		}
		assertFalse(Files.exists(out.resolve("com.x.target/vendor/plugins/org.old_1.0.0.jar")));
		assertTrue(Files.exists(out.resolve("distribution/configuration/.gitkeep")));
		assertTrue(Files.exists(out.resolve("com.x.gone/pom.xml")), "stale module directories are reported, not deleted");
		assertTrue(Files.readString(out.resolve("MIGRATION-REPORT.md")).contains("com.x.gone"));
	}

	@Test
	void refusesAnOutputDirectoryOverlappingASelectedProject() throws Exception {
		Path api = project();
		Inventory inv = GenFixtures.simple(api.toString());
		for (Path out : new Path[] { api, api.resolve("out"), tmp.resolve("src") }) {
			GenerationException e = assertThrows(GenerationException.class, () -> Generator.generate(inv, out, true),
					out.toString());
			assertTrue(e.errors().get(0).contains("com.x.api"), e.errors().toString());
		}
		assertFalse(Files.exists(api.resolve("pom.xml")), "nothing may be written into a source project");
	}

	@Test
	void gitignoreOnlyIgnoresBuildOutputDirectories() throws Exception {
		Path out = tmp.resolve("out");
		Generator.generate(GenFixtures.simple(project().toString()), out, false);
		assertEquals("/target/\n/*/target/\n", Files.readString(out.resolve(".gitignore")));
	}
}
