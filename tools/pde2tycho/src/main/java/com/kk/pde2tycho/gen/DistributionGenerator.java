package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.xml.Xml;

/** The eclipse-repository module: p2 repository, the product, and runnable per-OS archives. */
final class DistributionGenerator {

	private static final String PRODUCTS = "${project.build.directory}/products/";

	private DistributionGenerator() {
	}

	static void generate(Inventory inv, Launch launch, LauncherArgs.Rewritten args, Path dir) throws IOException {
		Output.write(dir.resolve("pom.xml"), pom(inv, launch.productId()));
		Output.write(dir.resolve("category.xml"), category(inv, launch));
		Output.write(dir.resolve(launch.productId() + ".product"), product(inv, launch, args));
		Output.write(dir.resolve("configuration/.gitkeep"), "");
	}

	/** Where a materialized product's plugins/ and configuration/ live, relative to target/products/. */
	static String productRoot(String productId, String env) {
		return productId + "/" + env + (env.startsWith("macosx/") ? "/Eclipse.app/Contents/Eclipse" : "");
	}

	private static String pom(Inventory inv, String productId) {
		StringBuilder steps = new StringBuilder();
		for (String env : inv.environments()) {
			boolean windows = env.startsWith("win32/");
			String root = PRODUCTS + productRoot(productId, env);
			String script = windows ? "run.bat" : "run.sh";
			String archive = PRODUCTS + productId + "-" + env.replace('/', '.') + (windows ? ".zip" : ".tar.gz");
			String i = "\t\t\t\t\t\t\t\t";
			steps.append(i).append("<copy todir=\"").append(root).append("/configuration\" failonerror=\"false\">\n")
					.append(i).append("\t<fileset dir=\"${project.basedir}/configuration\" excludes=\".gitkeep\"/>\n")
					.append(i).append("</copy>\n")
					.append(i).append("<copy file=\"${project.basedir}/scripts/").append(script).append("\" todir=\"")
					.append(root).append("\"/>\n");
			if (windows) {
				steps.append(i).append("<zip destfile=\"").append(archive).append("\" basedir=\"").append(PRODUCTS)
						.append(productId).append('/').append(env).append("\"/>\n");
			} else {
				steps.append(i).append("<chmod file=\"").append(root).append("/run.sh\" perm=\"755\"/>\n")
						.append(i).append("<tar destfile=\"").append(archive).append("\" basedir=\"").append(PRODUCTS)
						.append(productId).append('/').append(env).append("\" compression=\"gzip\"/>\n");
			}
		}
		return Templates.render("distribution-pom.xml",
				Map.of("GROUP_ID", Xml.escape(inv.groupId()), "VERSION", Xml.escape(inv.version()), "FINISH_STEPS",
						steps.toString()));
	}

	/** Selected features, plus workspace bundles no selected feature covers (Tycho 4 accepts <bundle>). */
	private static String category(Inventory inv, Launch launch) {
		String group = Xml.escape(inv.groupId());
		Set<String> covered = new HashSet<>();
		StringBuilder entries = new StringBuilder();
		for (Project feature : inv.modules()) {
			if (feature.kind() == Kind.FEATURE) {
				covered.addAll(feature.featurePlugins());
				entries.append("\t<feature id=\"").append(Xml.escape(feature.bsn())).append("\">\n\t\t<category name=\"")
						.append(group).append("\"/>\n\t</feature>\n");
			}
		}
		for (LaunchBundle bundle : launch.bundles()) {
			if (bundle.workspace() && !covered.contains(bundle.id())) {
				entries.append("\t<bundle id=\"").append(Xml.escape(bundle.id()))
						.append("\" version=\"0.0.0\">\n\t\t<category name=\"").append(group)
						.append("\"/>\n\t</bundle>\n");
			}
		}
		return Templates.render("category.xml", Map.of("GROUP_ID", group, "ENTRIES", entries.toString()));
	}

	private static String product(Inventory inv, Launch launch, LauncherArgs.Rewritten args) {
		Set<String> fragments = inv.modules().stream().filter(p -> p.kind() == Kind.FRAGMENT).map(Project::bsn)
				.collect(Collectors.toSet());
		StringBuilder plugins = new StringBuilder();
		StringBuilder configurations = new StringBuilder();
		for (LaunchBundle bundle : launch.bundles()) {
			String id = Xml.escape(bundle.id());
			plugins.append("\t\t<plugin id=\"").append(id).append('"')
					.append(fragments.contains(bundle.id()) ? " fragment=\"true\"" : "").append("/>\n");
			boolean autoStart = bundle.autoStart() != null ? bundle.autoStart() : launch.defaultAutoStart();
			if (autoStart || bundle.level() != null) {
				configurations.append("\t\t<plugin id=\"").append(id).append("\" autoStart=\"").append(autoStart)
						.append("\" startLevel=\"").append(bundle.level() == null ? 0 : bundle.level()).append("\"/>\n");
			}
		}
		String mac = args.vmArgsMac().isEmpty() ? "" : "\t\t<vmArgsMac>" + Xml.escape(args.vmArgsMac()) + "</vmArgsMac>\n";
		return Templates.render("product.product", Map.of("PRODUCT_ID", Xml.escape(launch.productId()),
				"OSGI_VERSION", PomGenerator.osgiVersion(inv.version()), "PROGRAM_ARGS", Xml.escape(args.programArgs()),
				"VM_ARGS", Xml.escape(args.vmArgs()), "VM_ARGS_MAC", mac, "PLUGINS", plugins.toString(),
				"CONFIGURATIONS", configurations.toString()));
	}
}
