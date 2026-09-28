package com.kk.pde2tycho.scan;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.stream.Stream;

import org.w3c.dom.Element;

import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.xml.Xml;

/** Classifies one project directory by its files. */
public final class ProjectReader {

	private ProjectReader() {
	}

	/** Problems that do not block migration are added to {@code warnings}. */
	public static Project read(String name, Path dir, List<String> warnings) throws IOException {
		Path feature = dir.resolve("feature.xml");
		if (Files.isRegularFile(feature)) {
			Element root = Xml.parse(feature).getDocumentElement();
			List<String> plugins = new ArrayList<>();
			for (Element plugin : Xml.children(root, "plugin")) {
				plugins.add(plugin.getAttribute("id"));
			}
			return new Project(name, Kind.FEATURE, dir.toString(), root.getAttribute("id"),
					root.getAttribute("version"), null, plugins, true);
		}
		Path manifest = dir.resolve("META-INF/MANIFEST.MF");
		if (Files.isRegularFile(manifest)) {
			Attributes headers = Manifests.ofFile(manifest);
			String bsn = Manifests.clause(headers.getValue("Bundle-SymbolicName"));
			if (bsn != null) {
				checkNestedTests(name, dir, warnings);
				Kind kind = headers.getValue("Fragment-Host") != null ? Kind.FRAGMENT : Kind.PLUGIN;
				String version = headers.getValue("Bundle-Version");
				return new Project(name, kind, dir.toString(), bsn, version == null ? "0.0.0" : version.trim(),
						Manifests.clause(headers.getValue("Bundle-RequiredExecutionEnvironment")), List.of(), true);
			}
		}
		Kind kind;
		try (Stream<Path> files = Files.list(dir)) {
			kind = files.anyMatch(p -> p.getFileName().toString().endsWith(".target")) ? Kind.TARGET : Kind.OTHER;
		}
		return new Project(name, kind, dir.toString(), null, null, null, List.of(), false);
	}

	/** Tests under the production source root get compiled into the bundle by Tycho. */
	private static void checkNestedTests(String name, Path dir, List<String> warnings) throws IOException {
		Path buildProperties = dir.resolve("build.properties");
		if (!Files.isRegularFile(buildProperties)) {
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(buildProperties, StandardCharsets.ISO_8859_1)) {
			properties.load(reader);
		}
		String sources = properties.getProperty("source..");
		if (sources == null) {
			return;
		}
		for (String folder : sources.split(",")) {
			if (Files.isDirectory(dir.resolve(folder.trim()).resolve("test"))) {
				warnings.add("Project " + name + ": a test/ folder is nested under source folder " + folder.trim()
						+ "; Tycho compiles it into the bundle. Move the tests to src_test/"
						+ " (see docs/adding-tests-to-a-tycho-project.md)");
			}
		}
	}
}
