package com.kk.pde2tycho.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.w3c.dom.Element;

import com.kk.pde2tycho.xml.Xml;

/** Reads a PDE .target file. */
public final class TargetReader {

	/** Resolves "${name}" / "${name:arg}"; returns null for a variable it does not know. */
	@FunctionalInterface
	public interface Variables {
		String resolve(String name, String arg);
	}

	/** Verbatim Maven/InstallableUnit location elements, plus every jar of Directory/Profile locations. */
	public record Content(List<String> locations, List<Path> jars) {
	}

	private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

	private TargetReader() {
	}

	public static Content read(Path targetFile, Variables vars, List<String> warnings) throws IOException {
		Element root = Xml.parse(targetFile).getDocumentElement();
		List<String> locations = new ArrayList<>();
		List<Path> jars = new ArrayList<>();
		for (Element group : Xml.children(root, "locations")) {
			for (Element location : Xml.children(group, "location")) {
				String type = location.getAttribute("type");
				switch (type) {
					case "Maven", "InstallableUnit" -> locations.add(Xml.toString(location));
					case "Directory" -> addDirectoryJars(location.getAttribute("path"), vars, jars, warnings);
					case "Profile" -> {
						addDirectoryJars(location.getAttribute("path"), vars, jars, warnings);
						warnings.add("Target location " + location.getAttribute("path") + " is a Profile: it pulls in a"
								+ " whole Eclipse installation (often hundreds of jars, all vendored when not on Maven"
								+ " Central). It is best replaced by a p2 site (InstallableUnit) location listing only"
								+ " the features you need");
					}
					default -> warnings.add("Target location type '" + type
							+ "' is not supported; add its bundles to the generated target by hand");
				}
			}
		}
		return new Content(locations, jars);
	}

	private static void addDirectoryJars(String rawPath, Variables vars, List<Path> jars, List<String> warnings)
			throws IOException {
		String path = substitute(rawPath, vars);
		if (path == null) {
			warnings.add("Target location " + rawPath + " uses a variable scan cannot resolve; skipped"
					+ " (for ${eclipse_home} pass --eclipse-home <Eclipse installation>)");
			return;
		}
		Path dir = Path.of(path);
		if (!Files.isDirectory(dir)) {
			warnings.add("Target location " + dir + " does not exist; skipped");
			return;
		}
		addJars(dir, jars, warnings);
		if (Files.isDirectory(dir.resolve("plugins"))) {
			addJars(dir.resolve("plugins"), jars, warnings);
		}
	}

	private static void addJars(Path dir, List<Path> jars, List<String> warnings) throws IOException {
		try (Stream<Path> entries = Files.list(dir)) {
			for (Path entry : (Iterable<Path>) entries.sorted()::iterator) {
				if (entry.getFileName().toString().endsWith(".jar") && Files.isRegularFile(entry)) {
					jars.add(entry);
				} else if (Files.isRegularFile(entry.resolve("META-INF/MANIFEST.MF"))) {
					warnings.add("Unpacked bundle directory " + entry
							+ " is not migrated; jar it and put it in the generated target's vendor/plugins");
				}
			}
		}
	}

	static String substitute(String raw, Variables vars) {
		Matcher m = VARIABLE.matcher(raw);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String value = vars.resolve(m.group(1), m.group(2));
			if (value == null) {
				return null;
			}
			m.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		m.appendTail(out);
		return out.toString();
	}
}
