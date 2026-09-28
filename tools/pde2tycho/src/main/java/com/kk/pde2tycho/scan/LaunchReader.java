package com.kk.pde2tycho.scan;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.xml.Xml;

/**
 * Reads a PDE .launch file. Any launch with a bundle list counts, whatever its type:
 * "Eclipse Application" launches with useProduct=false are as bundle-based as
 * "OSGi Framework" ones.
 */
public final class LaunchReader {

	private static final String EQUINOX_LAUNCHER = "org.eclipse.pde.ui.EquinoxLauncher";

	private LaunchReader() {
	}

	/** Returns null when the launch carries no bundle list (e.g. a plain Java application). */
	public static Launch read(Path file) throws IOException {
		Element root = Xml.parse(file).getDocumentElement();
		Map<String, String> strings = new HashMap<>();
		Map<String, Boolean> booleans = new HashMap<>();
		Map<String, List<String>> sets = new HashMap<>();
		for (Element e : Xml.children(root)) {
			String key = e.getAttribute("key");
			switch (e.getTagName()) {
				case "stringAttribute", "intAttribute" -> strings.put(key, e.getAttribute("value"));
				case "booleanAttribute" -> booleans.put(key, Boolean.valueOf(e.getAttribute("value")));
				case "setAttribute", "listAttribute" -> {
					List<String> values = new ArrayList<>();
					for (Element entry : Xml.children(e)) {
						values.add(entry.getAttribute("value"));
					}
					sets.put(key, values);
				}
				default -> {
					// other attribute kinds (mapAttribute) carry nothing we migrate
				}
			}
		}
		List<String> workspace = sets.get("selected_workspace_bundles");
		List<String> target = sets.get("selected_target_bundles");
		if (workspace == null && target == null) {
			// PDE before 3.6 stored the lists as comma-separated strings
			workspace = splitCsv(strings.get("workspace_bundles"));
			target = splitCsv(strings.get("target_bundles"));
			if (workspace == null && target == null) {
				return null;
			}
		}
		List<LaunchBundle> bundles = new ArrayList<>();
		if (workspace != null) {
			workspace.forEach(entry -> bundles.add(parseEntry(entry, true)));
		}
		if (target != null) {
			target.forEach(entry -> bundles.add(parseEntry(entry, false)));
		}
		boolean equinox = EQUINOX_LAUNCHER.equals(root.getAttribute("type"));
		boolean defaultAutoStart = equinox && booleans.getOrDefault("default_auto_start", true);
		String productId = strings.get("productId");
		String jre = strings.get("org.eclipse.jdt.launching.JRE_CONTAINER");
		String name = file.getFileName().toString().replaceFirst("\\.launch$", "");
		return new Launch(name, false, productId == null || productId.isBlank() ? null : productId, defaultAutoStart,
				bundles, strings.getOrDefault("org.eclipse.jdt.launching.VM_ARGUMENTS", ""),
				strings.getOrDefault("org.eclipse.jdt.launching.PROGRAM_ARGUMENTS", ""),
				jre == null ? null : jre.substring(jre.lastIndexOf('/') + 1));
	}

	/** "id@level:autoStart", where either part may be "default" and id may carry "*version". */
	static LaunchBundle parseEntry(String entry, boolean workspace) {
		String id = entry;
		Integer level = null;
		Boolean autoStart = null;
		int at = entry.indexOf('@');
		if (at >= 0) {
			id = entry.substring(0, at);
			String[] parts = entry.substring(at + 1).split(":", 2);
			if (!parts[0].isEmpty() && !"default".equals(parts[0])) {
				level = Integer.valueOf(parts[0]);
			}
			if (parts.length > 1 && !"default".equals(parts[1])) {
				autoStart = Boolean.valueOf(parts[1]);
			}
		}
		int star = id.indexOf('*');
		if (star >= 0) {
			id = id.substring(0, star);
		}
		return new LaunchBundle(id, level, autoStart, workspace);
	}

	private static List<String> splitCsv(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return List.of(value.split(","));
	}
}
