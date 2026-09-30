package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Target;
import com.kk.pde2tycho.model.TargetJar;
import com.kk.pde2tycho.xml.Xml;

/** The eclipse-target-definition module: <groupId>.target/{pom.xml, <id>.target, vendor/plugins}. */
final class TargetGenerator {

	private TargetGenerator() {
	}

	static void generate(Inventory inv, Path dir) throws IOException {
		String id = PomGenerator.targetId(inv);
		Output.write(dir.resolve("pom.xml"), PomGenerator.module(inv, id, inv.version(), "eclipse-target-definition"));
		Target target = inv.target();
		StringBuilder locations = new StringBuilder();
		for (String location : target.locations()) {
			locations.append("\t\t").append(location.strip()).append('\n');
		}
		if (!target.resolved().isEmpty()) {
			locations.append("\t\t<location includeDependencyDepth=\"none\" includeDependencyScopes=\"compile\"")
					.append(" missingManifest=\"error\" type=\"Maven\">\n\t\t\t<dependencies>\n");
			for (TargetJar jar : target.resolved()) {
				String[] gav = jar.gav().split(":");
				locations.append("\t\t\t\t<dependency>\n")
						.append("\t\t\t\t\t<groupId>").append(Xml.escape(gav[0])).append("</groupId>\n")
						.append("\t\t\t\t\t<artifactId>").append(Xml.escape(gav[1])).append("</artifactId>\n")
						.append("\t\t\t\t\t<version>").append(Xml.escape(gav[2])).append("</version>\n")
						.append("\t\t\t\t\t<type>jar</type>\n")
						.append("\t\t\t\t</dependency>\n");
			}
			locations.append("\t\t\t</dependencies>\n\t\t</location>\n");
		}
		if (!target.vendor().isEmpty()) {
			Path plugins = Files.createDirectories(dir.resolve("vendor/plugins"));
			for (TargetJar jar : target.vendor()) {
				Files.copy(Path.of(jar.jar()), plugins.resolve(jar.bsn() + "_" + jar.version() + ".jar"),
						StandardCopyOption.REPLACE_EXISTING);
			}
			// The only form Tycho 4.0.13 resolves; relative, ${project_loc} and ${basedir} yield nothing.
			locations.append("\t\t<location path=\"${project_loc:/").append(id)
					.append("}/vendor/plugins\" type=\"Directory\"/>\n");
		}
		Output.write(dir.resolve(id + ".target"),
				Templates.render("target.target", Map.of("NAME", id, "LOCATIONS", locations.toString())));
	}
}
