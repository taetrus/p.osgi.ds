package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Target;
import com.kk.pde2tycho.model.TargetJar;

/**
 * Verbatim locations pass through, Central hits become one Maven location, and misses
 * are copied into vendor/plugins behind the only Directory path form Tycho 4.0.13
 * resolves: ${project_loc:/<module>}. Relative paths, ${project_loc} and ${basedir}
 * silently resolve to nothing (verified 2026-09-28).
 */
class TargetGeneratorTest {

	@TempDir
	Path tmp;

	Inventory inventory(Target target) {
		return GenFixtures.inventory(List.of(GenFixtures.plugin("com.x.api", "/src/com.x.api")), target,
				List.of(new LaunchBundle("com.x.api", 4, true, true)));
	}

	@Test
	void writesAllThreeKindsOfLocation() throws Exception {
		Path vendored = Files.writeString(tmp.resolve("b.jar"), "jar bytes");
		Target target = new Target("/t.target",
				List.of("<location type=\"InstallableUnit\"><unit id=\"u\" version=\"0.0.0\"/></location>"),
				List.of(new TargetJar("/lib/a.jar", "aa", "org.a", "1.0.0", "org.a:a-core:1.0.0", null)),
				List.of(new TargetJar(vendored.toString(), "bb", "com.b", "2.0.0", null, "not on Maven Central")));
		Path module = tmp.resolve("out/com.x.target");
		TargetGenerator.generate(inventory(target), module);

		String xml = Files.readString(module.resolve("com.x.target.target"));
		DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(module.resolve("com.x.target.target").toFile());
		assertTrue(xml.contains("<unit id=\"u\" version=\"0.0.0\"/>"), xml);
		assertTrue(xml.contains("<artifactId>a-core</artifactId>"), xml);
		assertTrue(xml.contains("path=\"${project_loc:/com.x.target}/vendor/plugins\" type=\"Directory\""), xml);
		assertTrue(Files.exists(module.resolve("vendor/plugins/com.b_2.0.0.jar")));
		assertTrue(Files.readString(module.resolve("pom.xml")).contains("<packaging>eclipse-target-definition</packaging>"));
	}

	@Test
	void emptyListsAddNoLocations() throws IOException {
		Path module = tmp.resolve("out/com.x.target");
		TargetGenerator.generate(inventory(new Target(null, List.of(), List.of(), List.of())), module);
		String xml = Files.readString(module.resolve("com.x.target.target"));
		assertFalse(xml.contains("<location "), xml);
		assertFalse(Files.exists(module.resolve("vendor")));
	}
}
