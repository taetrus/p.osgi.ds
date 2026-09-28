package com.kk.pde2tycho.gen;

import java.util.List;
import java.util.Map;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.xml.Xml;

/** The parent pom and the ~10-line module poms Tycho's manifest-first build needs. */
final class PomGenerator {

	private PomGenerator() {
	}

	static String targetId(Inventory inv) {
		return inv.groupId() + ".target";
	}

	static String parent(Inventory inv, List<String> modules) {
		StringBuilder environments = new StringBuilder();
		for (String env : inv.environments()) {
			String[] p = env.split("/");
			environments.append("\t\t\t\t\t\t<environment>\n")
					.append("\t\t\t\t\t\t\t<os>").append(p[0]).append("</os>\n")
					.append("\t\t\t\t\t\t\t<ws>").append(p[1]).append("</ws>\n")
					.append("\t\t\t\t\t\t\t<arch>").append(p[2]).append("</arch>\n")
					.append("\t\t\t\t\t\t</environment>\n");
		}
		StringBuilder moduleList = new StringBuilder();
		for (String module : modules) {
			moduleList.append("\t\t<module>").append(Xml.escape(module)).append("</module>\n");
		}
		return Templates.render("parent-pom.xml", Map.of("GROUP_ID", inv.groupId(), "VERSION", inv.version(),
				"TYCHO_VERSION", inv.tychoVersion(), "TARGET_ID", targetId(inv), "ENVIRONMENTS",
				environments.toString(), "MODULES", moduleList.toString()));
	}

	static String module(Inventory inv, Project project) {
		return module(inv, project.bsn(), mavenVersion(project.version()),
				project.kind() == Kind.FEATURE ? "eclipse-feature" : "eclipse-plugin");
	}

	static String module(Inventory inv, String artifactId, String version, String packaging) {
		return Templates.render("module-pom.xml", Map.of("GROUP_ID", inv.groupId(), "VERSION", inv.version(),
				"ARTIFACT_ID", artifactId, "MODULE_VERSION", version, "PACKAGING", packaging));
	}

	/** Bundle-Version 1.2.3.qualifier ↔ pom 1.2.3-SNAPSHOT; any other version must match exactly. */
	static String mavenVersion(String osgi) {
		return osgi.endsWith(".qualifier") ? osgi.substring(0, osgi.length() - ".qualifier".length()) + "-SNAPSHOT"
				: osgi;
	}

	static String osgiVersion(String maven) {
		return maven.endsWith("-SNAPSHOT") ? maven.substring(0, maven.length() - "-SNAPSHOT".length()) + ".qualifier"
				: maven;
	}
}
