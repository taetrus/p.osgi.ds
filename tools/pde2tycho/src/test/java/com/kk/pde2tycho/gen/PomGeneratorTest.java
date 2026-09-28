package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Project;

/** Tycho requires each pom version to mirror its Bundle-Version (.qualifier ↔ -SNAPSHOT). */
class PomGeneratorTest {

	final Inventory inv = GenFixtures.simple("/src/com.x.api");

	@Test
	void versionMapping() {
		assertEquals("1.0.0-SNAPSHOT", PomGenerator.mavenVersion("1.0.0.qualifier"));
		assertEquals("2.3.1", PomGenerator.mavenVersion("2.3.1"));
		assertEquals("1.0.0.qualifier", PomGenerator.osgiVersion("1.0.0-SNAPSHOT"));
	}

	@Test
	void parentPinsLiteralTargetVersionAndListsEnvironments() {
		String pom = PomGenerator.parent(inv, List.of("com.x.target", "com.x.api", "distribution"));
		assertTrue(pom.contains("<artifactId>com.x.target</artifactId>\n\t\t\t\t\t\t\t<version>1.0.0-SNAPSHOT</version>"), pom);
		assertTrue(pom.contains("<os>macosx</os>\n\t\t\t\t\t\t\t<ws>cocoa</ws>\n\t\t\t\t\t\t\t<arch>aarch64</arch>"), pom);
		assertTrue(pom.contains("\t\t<module>com.x.api</module>\n"), pom);
		assertTrue(pom.contains("<id>default-testCompile</id>"), pom);
	}

	@Test
	void moduleVersionFollowsBundleVersion() {
		Project p = new Project("x", Kind.PLUGIN, "/x", "com.x.lib", "2.1.0.qualifier", null, List.of(), true);
		String pom = PomGenerator.module(inv, p);
		assertTrue(pom.contains("<artifactId>com.x.lib</artifactId>"), pom);
		assertTrue(pom.contains("<version>2.1.0-SNAPSHOT</version>\n\t<packaging>eclipse-plugin</packaging>"), pom);
		assertTrue(pom.contains("<parent>\n\t\t<groupId>com.x</groupId>\n\t\t<artifactId>parent</artifactId>\n\t\t<version>1.0.0-SNAPSHOT</version>"), pom);
	}

	@Test
	void featureIsAnEclipseFeature() {
		Project f = new Project("f", Kind.FEATURE, "/f", "com.x.feature", "1.0.0.qualifier", null, List.of(), true);
		assertTrue(PomGenerator.module(inv, f).contains("<packaging>eclipse-feature</packaging>"));
	}
}
