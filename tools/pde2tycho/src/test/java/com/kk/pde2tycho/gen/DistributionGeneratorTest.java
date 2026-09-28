package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.model.Target;

/**
 * The product mirrors the launch one-to-one: every bundle listed, start levels and
 * auto-start carried over, "default:default" entries left to the product defaults.
 */
class DistributionGeneratorTest {

	@TempDir
	Path tmp;

	final List<LaunchBundle> bundles = List.of(new LaunchBundle("com.x.api", 4, true, true),
			new LaunchBundle("com.x.frag", null, null, true), new LaunchBundle("org.apache.felix.scr", 2, true, false),
			new LaunchBundle("org.apache.felix.http.jetty", null, true, false),
			new LaunchBundle("org.apache.commons.commons-io", null, null, false));

	Inventory inventory() {
		Project frag = new Project("com.x.frag", Kind.FRAGMENT, "/f", "com.x.frag", "1.0.0.qualifier", null, List.of(), true);
		Project feature = new Project("com.x.feature", Kind.FEATURE, "/fe", "com.x.feature", "1.0.0.qualifier", null,
				List.of("com.x.api"), true);
		return GenFixtures.inventory(List.of(GenFixtures.plugin("com.x.api", "/a"), frag, feature),
				new Target(null, List.of(), List.of(), List.of()), bundles);
	}

	Path generate() throws Exception {
		Inventory inv = inventory();
		Path dir = tmp.resolve("distribution");
		DistributionGenerator.generate(inv, inv.selectedLaunch(),
				LauncherArgs.rewrite(inv.selectedLaunch().vmArgs(), inv.selectedLaunch().programArgs(), new ArrayList<>()),
				dir);
		for (String file : List.of("pom.xml", "category.xml", "com.x.product.product")) {
			DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(dir.resolve(file).toFile());
		}
		return dir;
	}

	@Test
	void productCarriesBundlesAndStartConfiguration() throws Exception {
		String product = Files.readString(generate().resolve("com.x.product.product"));
		assertTrue(product.contains("uid=\"com.x.product\""), product);
		assertTrue(product.contains("version=\"1.0.0.qualifier\""), product);
		assertTrue(product.contains("\t\t<plugin id=\"com.x.frag\" fragment=\"true\"/>\n"), product);
		assertTrue(product.contains("<plugin id=\"org.apache.felix.scr\" autoStart=\"true\" startLevel=\"2\"/>"), product);
		assertTrue(product.contains("<plugin id=\"org.apache.felix.http.jetty\" autoStart=\"true\" startLevel=\"0\"/>"), product);
		assertFalse(product.contains("commons-io\" autoStart"), product);
		assertTrue(product.contains("<vmArgsMac>-XstartOnFirstThread</vmArgsMac>"), product);
		assertTrue(product.contains("<programArgs>-consoleLog</programArgs>"), product);
	}

	@Test
	void categoryListsFeaturesAndUncoveredWorkspaceBundles() throws Exception {
		String category = Files.readString(generate().resolve("category.xml"));
		assertTrue(category.contains("<feature id=\"com.x.feature\">"), category);
		assertTrue(category.contains("<bundle id=\"com.x.frag\" version=\"0.0.0\">"), category);
		assertFalse(category.contains("<bundle id=\"com.x.api\""), category);
		assertFalse(category.contains("org.apache.felix.scr"), category);
	}

	@Test
	void pomFinishesEveryEnvironment() throws Exception {
		Path dir = generate();
		String pom = Files.readString(dir.resolve("pom.xml"));
		assertTrue(pom.contains("products/com.x.product/macosx/cocoa/aarch64/Eclipse.app/Contents/Eclipse\"/>"), pom);
		assertTrue(pom.contains("<zip destfile=\"${project.build.directory}/products/com.x.product-win32.win32.x86_64.zip\""), pom);
		assertTrue(pom.contains("<copy file=\"${project.basedir}/scripts/run.bat\" todir=\"${project.build.directory}/products/com.x.product/win32/win32/x86_64\"/>"), pom);
		assertTrue(Files.exists(dir.resolve("configuration/.gitkeep")));
	}
}
