package com.kk.pde2tycho.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.kk.pde2tycho.json.Json;

/** Reads and writes {@link Inventory} as the hand-editable migration.json. */
public final class InventoryJson {

	private InventoryJson() {
	}

	public static String write(Inventory inv) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("workspace", inv.workspace());
		m.put("groupId", inv.groupId());
		m.put("version", inv.version());
		m.put("tychoVersion", inv.tychoVersion());
		m.put("environments", new ArrayList<Object>(inv.environments()));
		List<Object> projects = new ArrayList<>();
		for (Project p : inv.projects()) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("name", p.name());
			o.put("kind", p.kind().name().toLowerCase(Locale.ROOT));
			o.put("path", p.path());
			o.put("bsn", p.bsn());
			o.put("version", p.version());
			o.put("bree", p.bree());
			o.put("featurePlugins", new ArrayList<Object>(p.featurePlugins()));
			o.put("selected", p.selected());
			projects.add(o);
		}
		m.put("projects", projects);
		Target t = inv.target();
		Map<String, Object> target = new LinkedHashMap<>();
		target.put("source", t.source());
		target.put("locations", new ArrayList<Object>(t.locations()));
		target.put("resolved", jars(t.resolved()));
		target.put("vendor", jars(t.vendor()));
		m.put("target", target);
		List<Object> launches = new ArrayList<>();
		for (Launch l : inv.launches()) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("name", l.name());
			o.put("selected", l.selected());
			o.put("productId", l.productId());
			o.put("defaultAutoStart", l.defaultAutoStart());
			List<Object> bundles = new ArrayList<>();
			for (LaunchBundle b : l.bundles()) {
				Map<String, Object> bo = new LinkedHashMap<>();
				bo.put("id", b.id());
				bo.put("level", b.level());
				bo.put("autoStart", b.autoStart());
				bo.put("workspace", b.workspace());
				bundles.add(bo);
			}
			o.put("bundles", bundles);
			o.put("vmArgs", l.vmArgs());
			o.put("programArgs", l.programArgs());
			o.put("jre", l.jre());
			launches.add(o);
		}
		m.put("launches", launches);
		m.put("warnings", new ArrayList<Object>(inv.warnings()));
		return Json.stringifyPretty(m);
	}

	private static List<Object> jars(List<TargetJar> jars) {
		List<Object> out = new ArrayList<>();
		for (TargetJar j : jars) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("jar", j.jar());
			o.put("sha1", j.sha1());
			o.put("bsn", j.bsn());
			o.put("version", j.version());
			o.put("gav", j.gav());
			o.put("reason", j.reason());
			out.add(o);
		}
		return out;
	}

	/** @throws IllegalArgumentException if the text is not valid JSON or a field has the wrong shape */
	public static Inventory read(String json) {
		Object parsed;
		try {
			parsed = Json.parse(json);
		} catch (Json.JsonException e) {
			throw new IllegalArgumentException("migration.json is not valid JSON: " + e.getMessage(), e);
		}
		Map<String, Object> m = object(parsed, "the top level");
		List<Project> projects = new ArrayList<>();
		for (Map<String, Object> o : objects(m, "projects")) {
			projects.add(new Project(str(o, "name"), kind(str(o, "kind")), str(o, "path"), str(o, "bsn"),
					str(o, "version"), str(o, "bree"), strings(o, "featurePlugins"), bool(o, "selected")));
		}
		Map<String, Object> t = object(m.get("target"), "\"target\"");
		Target target = new Target(str(t, "source"), strings(t, "locations"), jars(t, "resolved"), jars(t, "vendor"));
		List<Launch> launches = new ArrayList<>();
		for (Map<String, Object> o : objects(m, "launches")) {
			List<LaunchBundle> bundles = new ArrayList<>();
			for (Map<String, Object> b : objects(o, "bundles")) {
				bundles.add(new LaunchBundle(str(b, "id"), integer(b, "level"), boolOrNull(b, "autoStart"),
						bool(b, "workspace")));
			}
			launches.add(new Launch(str(o, "name"), bool(o, "selected"), str(o, "productId"),
					bool(o, "defaultAutoStart"), bundles, str(o, "vmArgs"), str(o, "programArgs"), str(o, "jre")));
		}
		return new Inventory(str(m, "workspace"), str(m, "groupId"), str(m, "version"), str(m, "tychoVersion"),
				strings(m, "environments"), projects, target, launches, strings(m, "warnings"));
	}

	private static List<TargetJar> jars(Map<String, Object> m, String key) {
		List<TargetJar> out = new ArrayList<>();
		for (Map<String, Object> o : objects(m, key)) {
			out.add(new TargetJar(str(o, "jar"), str(o, "sha1"), str(o, "bsn"), str(o, "version"), str(o, "gav"),
					str(o, "reason")));
		}
		return out;
	}

	private static Kind kind(String value) {
		try {
			return Kind.valueOf(String.valueOf(value).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("migration.json: unknown project kind \"" + value
					+ "\" (expected plugin, fragment, feature, target or other)");
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> object(Object value, String what) {
		if (value instanceof Map) {
			return (Map<String, Object>) value;
		}
		throw new IllegalArgumentException("migration.json: " + what + " must be an object");
	}

	private static List<Map<String, Object>> objects(Map<String, Object> m, String key) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Object o : list(m, key)) {
			out.add(object(o, "each entry of \"" + key + "\""));
		}
		return out;
	}

	private static List<String> strings(Map<String, Object> m, String key) {
		List<String> out = new ArrayList<>();
		for (Object o : list(m, key)) {
			out.add(String.valueOf(o));
		}
		return out;
	}

	private static List<Object> list(Map<String, Object> m, String key) {
		Object v = m.get(key);
		if (v == null) {
			return List.of();
		}
		if (v instanceof List) {
			return Json.asList(v);
		}
		throw new IllegalArgumentException("migration.json: \"" + key + "\" must be an array");
	}

	private static String str(Map<String, Object> m, String key) {
		return Json.asString(m.get(key));
	}

	private static boolean bool(Map<String, Object> m, String key) {
		return Boolean.TRUE.equals(boolOrNull(m, key));
	}

	private static Boolean boolOrNull(Map<String, Object> m, String key) {
		Object v = m.get(key);
		if (v == null || v instanceof Boolean) {
			return (Boolean) v;
		}
		throw new IllegalArgumentException("migration.json: \"" + key + "\" must be true, false or null");
	}

	private static Integer integer(Map<String, Object> m, String key) {
		Object v = m.get(key);
		if (v == null) {
			return null;
		}
		if (v instanceof Number) {
			return ((Number) v).intValue();
		}
		throw new IllegalArgumentException("migration.json: \"" + key + "\" must be a number or null");
	}
}
