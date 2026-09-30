package com.kk.pde2tycho.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.model.Target;
import com.kk.pde2tycho.model.TargetJar;

/** Checks an inventory before anything is written. */
public final class InventoryValidator {

	private InventoryValidator() {
	}

	public static List<String> validate(Inventory inv) {
		List<String> errors = new ArrayList<>();
		required(errors, "groupId", inv.groupId());
		required(errors, "version", inv.version());
		required(errors, "tychoVersion", inv.tychoVersion());
		List<Project> modules = inv.modules();
		if (modules.isEmpty()) {
			errors.add("No projects are selected");
		}
		for (String env : inv.environments()) {
			if (env.split("/").length != 3) {
				errors.add("Environment \"" + env + "\" must be os/ws/arch, e.g. macosx/cocoa/aarch64");
			}
		}
		String targetId = PomGenerator.targetId(inv);
		Map<String, String> providers = new HashMap<>();
		for (Project p : modules) {
			if (p.name().equals("distribution") || p.name().equals(targetId)) {
				errors.add("Project \"" + p.name() + "\" clashes with a generated module name; rename or deselect it");
			}
			if (isBlank(p.bsn())) {
				errors.add("Selected project " + p.name() + " has no \"bsn\"; fill it in or deselect the project");
			}
			if (isBlank(p.version())) {
				errors.add("Selected project " + p.name() + " has no \"version\"; fill it in or deselect the project");
			}
			String other = providers.put(p.bsn(), p.name());
			if (other != null) {
				errors.add("\"" + p.bsn() + "\" is provided by both " + other + " and " + p.name() + "; deselect one");
			}
		}
		long selected = inv.launches().stream().filter(Launch::selected).count();
		if (selected != 1) {
			errors.add("Exactly one launch must be \"selected\": true (found " + selected + ")");
			return errors;
		}
		Launch launch = inv.selectedLaunch();
		if (launch.productId() == null || launch.productId().isBlank()) {
			errors.add("Launch " + launch.name() + " needs a productId");
		}
		Target target = inv.target();
		Set<String> targetBundles = new HashSet<>();
		for (TargetJar jar : target.resolved()) {
			targetBundles.add(jar.bsn());
			String[] gav = jar.gav() == null ? new String[0] : jar.gav().split(":", -1);
			if (gav.length != 3 || Arrays.stream(gav).anyMatch(String::isBlank)) {
				errors.add("Resolved target jar " + jar.jar() + " has gav \"" + jar.gav()
						+ "\"; it must be groupId:artifactId:version (or move the entry to target.vendor)");
			}
		}
		for (TargetJar jar : target.vendor()) {
			targetBundles.add(jar.bsn());
		}
		// Bundles inside Maven/p2 locations are unknown until Tycho resolves them.
		boolean targetFullyKnown = target.locations().isEmpty();
		boolean notInTarget = false;
		for (LaunchBundle bundle : launch.bundles()) {
			if (providers.containsKey(bundle.id())) {
				continue;
			}
			if (bundle.workspace()) {
				errors.add("Launch bundle " + bundle.id() + " is a workspace bundle but no selected project provides it;"
						+ " select its project (or scan with --add-project) or remove it from the launch");
			} else if (targetFullyKnown && !targetBundles.contains(bundle.id())) {
				errors.add("Launch bundle " + bundle.id() + " is not in the target platform");
				notInTarget = true;
			}
		}
		if (notInTarget) {
			errors.add("The target platform knows no bundles for these; add a Maven/p2 location to target.locations"
					+ " (see docs/pde-to-tycho-migration.md, section 7)");
		}
		for (Project feature : modules) {
			if (feature.kind() != Kind.FEATURE) {
				continue;
			}
			for (String plugin : feature.featurePlugins()) {
				if (!providers.containsKey(plugin) && targetFullyKnown && !targetBundles.contains(plugin)) {
					errors.add("Feature " + feature.bsn() + " includes " + plugin
							+ ", which neither a selected project nor the target provides");
				}
			}
		}
		return errors;
	}

	private static void required(List<String> errors, String field, String value) {
		if (isBlank(value)) {
			errors.add("migration.json has no \"" + field + "\"; set it (scan writes one)");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
