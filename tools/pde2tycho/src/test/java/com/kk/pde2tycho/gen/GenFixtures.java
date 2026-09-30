package com.kk.pde2tycho.gen;

import java.util.List;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.model.Target;

/** A small valid inventory for generator tests; tests derive variants from it. */
final class GenFixtures {

	private GenFixtures() {
	}

	static Project plugin(String bsn, String path) {
		return new Project(bsn, Kind.PLUGIN, path, bsn, "1.0.0.qualifier", "JavaSE-17", List.of(), true);
	}

	static Launch launch(List<LaunchBundle> bundles) {
		return new Launch("app", true, "com.x.product", false, bundles,
				"-Declipse.ignoreApp=true -Dosgi.noShutdown=true -Dlogback.configurationFile=configuration/logback.xml -XstartOnFirstThread",
				"-os ${target.os} -ws ${target.ws} -arch ${target.arch} -nl ${target.nl} -consoleLog", "JavaSE-21");
	}

	static Inventory inventory(List<Project> projects, Target target, List<LaunchBundle> bundles) {
		return new Inventory("/ws", "com.x", "1.0.0-SNAPSHOT", "4.0.13",
				List.of("macosx/cocoa/aarch64", "macosx/cocoa/x86_64", "win32/win32/x86_64"), projects, target,
				List.of(launch(bundles)), List.of());
	}

	static Inventory simple(String apiPath) {
		return inventory(List.of(plugin("com.x.api", apiPath)), new Target(null, List.of(), List.of(), List.of()),
				List.of(new LaunchBundle("com.x.api", 4, true, true)));
	}
}
