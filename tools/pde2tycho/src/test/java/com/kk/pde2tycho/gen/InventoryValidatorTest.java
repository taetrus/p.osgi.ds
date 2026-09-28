package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.model.Target;
import com.kk.pde2tycho.model.TargetJar;

/** generate refuses inventories that cannot produce a working build, and says why. */
class InventoryValidatorTest {

	static final Target EMPTY = new Target(null, List.of(), List.of(), List.of());

	@Test
	void validInventoryHasNoErrors() {
		assertEquals(List.of(), InventoryValidator.validate(GenFixtures.simple("/a")));
	}

	@Test
	void workspaceBundleWithoutSelectedProjectIsAnError() {
		Inventory inv = GenFixtures.inventory(List.of(GenFixtures.plugin("com.x.api", "/a")), EMPTY,
				List.of(new LaunchBundle("com.x.chatbot", 4, true, true)));
		assertTrue(InventoryValidator.validate(inv).get(0).contains("com.x.chatbot"));
	}

	@Test
	void targetBundleIsCheckedOnlyWhenTheTargetIsFullyKnown() {
		List<LaunchBundle> bundles = List.of(new LaunchBundle("com.x.api", 4, true, true),
				new LaunchBundle("org.missing", null, null, false));
		Inventory known = GenFixtures.inventory(List.of(GenFixtures.plugin("com.x.api", "/a")), new Target(null,
				List.of(), List.of(new TargetJar("/a.jar", "s", "org.present", "1", "g:a:1", null)), List.of()), bundles);
		assertTrue(InventoryValidator.validate(known).get(0).contains("org.missing"));
		Inventory opaque = GenFixtures.inventory(List.of(GenFixtures.plugin("com.x.api", "/a")),
				new Target(null, List.of("<location type=\"Maven\"/>"), List.of(), List.of()), bundles);
		assertEquals(List.of(), InventoryValidator.validate(opaque));
	}

	@Test
	void noSelectedLaunchIsAnError() {
		Inventory inv = GenFixtures.simple("/a");
		inv = new Inventory(inv.workspace(), inv.groupId(), inv.version(), inv.tychoVersion(), inv.environments(),
				inv.projects(), inv.target(), List.of(inv.launches().get(0).withSelection(false, "p")), inv.warnings());
		assertTrue(InventoryValidator.validate(inv).get(0).contains("Exactly one launch"));
	}

	@Test
	void duplicateBundleNamesAndReservedModuleNamesAreErrors() {
		Project twin = new Project("twin", Kind.PLUGIN, "/b", "com.x.api", "1.0.0", null, List.of(), true);
		Project reserved = new Project("distribution", Kind.PLUGIN, "/d", "com.x.dist", "1.0.0", null, List.of(), true);
		Inventory inv = GenFixtures.inventory(List.of(GenFixtures.plugin("com.x.api", "/a"), twin, reserved), EMPTY,
				List.of(new LaunchBundle("com.x.api", 4, true, true)));
		List<String> errors = InventoryValidator.validate(inv);
		assertTrue(errors.stream().anyMatch(e -> e.contains("provided by both")), errors.toString());
		assertTrue(errors.stream().anyMatch(e -> e.contains("\"distribution\"")), errors.toString());
	}

	@Test
	void malformedEnvironmentIsAnError() {
		Inventory inv = GenFixtures.simple("/a").withEnvironments(List.of("macosx/cocoa"));
		assertTrue(InventoryValidator.validate(inv).get(0).contains("os/ws/arch"));
	}
}
