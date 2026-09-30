package com.kk.pde2tycho.scan;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.LaunchBundle;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.model.Target;
import com.kk.pde2tycho.model.TargetJar;
import com.kk.pde2tycho.resolve.ArtifactResolver;

/** Reads an Eclipse workspace's .metadata into an {@link Inventory}. Never writes anything. */
public final class WorkspaceScanner {

	public static final List<String> DEFAULT_ENVIRONMENTS = List.of("macosx/cocoa/aarch64", "macosx/cocoa/x86_64",
			"win32/win32/x86_64");
	public static final String TYCHO_VERSION = "4.0.13";

	/** After this many failed lookups in a row, Central is treated as unreachable. */
	private static final int MAX_CONSECUTIVE_LOOKUP_FAILURES = 3;

	/** Progress is reported after every this many target jars (a Profile location can hold hundreds). */
	private static final int PROGRESS_EVERY = 25;

	private final ArtifactResolver resolver;
	private final Path eclipseHome;
	private final List<Path> extraProjects;
	private final Consumer<String> progress;

	public WorkspaceScanner(ArtifactResolver resolver, Path eclipseHome, List<Path> extraProjects,
			Consumer<String> progress) {
		this.resolver = resolver;
		this.eclipseHome = eclipseHome;
		this.extraProjects = extraProjects;
		this.progress = progress;
	}

	public Inventory scan(Path workspace) throws IOException {
		Path plugins = workspace.resolve(".metadata/.plugins");
		if (!Files.isDirectory(plugins)) {
			throw new IOException(workspace + " is not an Eclipse workspace (no .metadata/.plugins)");
		}
		List<String> warnings = new ArrayList<>();
		Map<String, Path> locations = projectLocations(workspace, plugins, warnings);
		for (Path extra : extraProjects) {
			Path dir = extra.toAbsolutePath().normalize();
			String name = dir.getFileName().toString();
			if (!Files.isDirectory(dir)) {
				warnings.add("--add-project " + extra + " is not a directory; skipped");
			} else if (locations.putIfAbsent(name, dir) != null) {
				warnings.add("--add-project " + extra + ": the workspace already has a project named " + name
						+ "; kept the workspace one");
			}
		}
		List<Project> projects = deselectIncompleteFeatures(readProjects(locations, warnings), warnings);
		String groupId = groupId(projects);
		Target target = readActiveTarget(workspace, plugins, locations, warnings);
		List<Launch> launches = readLaunches(plugins, locations, groupId, warnings);
		return new Inventory(workspace.toString(), groupId, "1.0.0-SNAPSHOT", TYCHO_VERSION, DEFAULT_ENVIRONMENTS,
				projects, target, launches, warnings);
	}

	// ---- projects ------------------------------------------------------------

	private static Map<String, Path> projectLocations(Path workspace, Path plugins, List<String> warnings)
			throws IOException {
		Map<String, Path> result = new TreeMap<>();
		Path dir = plugins.resolve("org.eclipse.core.resources/.projects");
		if (!Files.isDirectory(dir)) {
			return result;
		}
		try (Stream<Path> entries = Files.list(dir)) {
			for (Path entry : (Iterable<Path>) entries::iterator) {
				String name = entry.getFileName().toString();
				if (name.startsWith(".")) {
					continue;
				}
				Path location;
				try {
					location = readLocation(entry.resolve(".location"));
				} catch (IOException | RuntimeException e) {
					// e.g. a project on a non-file: file system, or a malformed URI
					warnings.add("Project " + name + ": cannot read its location (" + e.getMessage() + "); skipped");
					continue;
				}
				if (location == null) {
					location = workspace.resolve(name);
				}
				if (Files.isDirectory(location)) {
					result.put(name, location);
				} else {
					warnings.add("Project " + name + ": location " + location + " does not exist; skipped");
				}
			}
		}
		return result;
	}

	/** Eclipse's binary .location file holds the project URI as a length-prefixed "URI//..." string. */
	static Path readLocation(Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		byte[] bytes = Files.readAllBytes(file);
		byte[] marker = "URI//".getBytes(StandardCharsets.US_ASCII);
		for (int i = 2; i + marker.length <= bytes.length; i++) {
			if (Arrays.equals(bytes, i, i + marker.length, marker, 0, marker.length)) {
				int length = ((bytes[i - 2] & 0xff) << 8) | (bytes[i - 1] & 0xff);
				String uri = new String(bytes, i + marker.length, length - marker.length, StandardCharsets.UTF_8);
				return Path.of(URI.create(uri));
			}
		}
		return null;
	}

	private static List<Project> readProjects(Map<String, Path> locations, List<String> warnings) {
		List<Project> projects = new ArrayList<>();
		for (Map.Entry<String, Path> e : locations.entrySet()) {
			try {
				projects.add(ProjectReader.read(e.getKey(), e.getValue(), warnings));
			} catch (IOException ex) {
				warnings.add("Project " + e.getKey() + " skipped: " + ex.getMessage());
			}
		}
		return projects;
	}

	/** A feature including plug-ins the workspace lacks would fail the build, so it starts deselected. */
	private static List<Project> deselectIncompleteFeatures(List<Project> projects, List<String> warnings) {
		Set<String> bundles = projects.stream().filter(p -> p.kind() == Kind.PLUGIN || p.kind() == Kind.FRAGMENT)
				.map(Project::bsn).collect(Collectors.toSet());
		List<Project> out = new ArrayList<>();
		for (Project p : projects) {
			if (p.kind() == Kind.FEATURE) {
				List<String> missing = p.featurePlugins().stream().filter(id -> !bundles.contains(id)).toList();
				if (!missing.isEmpty()) {
					warnings.add("Feature " + p.bsn() + " deselected: it includes plug-ins that are not workspace projects ("
							+ String.join(", ", missing) + "). Add them with --add-project, or leave it deselected:"
							+ " the p2 repository lists bundles directly");
					p = p.withSelected(false);
				}
			}
			out.add(p);
		}
		return out;
	}

	/** Longest common dot-separated prefix of the selected bundle names. */
	static String groupId(List<Project> projects) {
		List<String[]> names = projects.stream().filter(Project::selected).filter(p -> p.bsn() != null)
				.map(p -> p.bsn().split("\\.")).toList();
		if (names.isEmpty()) {
			return "migrated";
		}
		String[] first = names.get(0);
		int common = 0;
		while (common < first.length) {
			int index = common;
			if (!names.stream().allMatch(n -> n.length > index && n[index].equals(first[index]))) {
				break;
			}
			common++;
		}
		return common == 0 ? "migrated" : String.join(".", Arrays.copyOf(first, common));
	}

	// ---- target --------------------------------------------------------------

	private Target readActiveTarget(Path workspace, Path plugins, Map<String, Path> locations, List<String> warnings)
			throws IOException {
		String handle = activeTargetHandle(plugins);
		Path file = resolveHandle(handle, plugins, locations);
		if (file == null || !Files.isRegularFile(file)) {
			warnings.add("No active target file found (workspace_target_handle=" + handle
					+ "); the generated target platform starts empty. To fill it, add a Maven or p2 (InstallableUnit)"
					+ " location to target.locations in migration.json, or rescan with a real .target");
			return new Target(null, List.of(), List.of(), List.of());
		}
		TargetReader.Content content = TargetReader.read(file, variables(workspace, locations), warnings);
		List<TargetJar> resolved = new ArrayList<>();
		List<TargetJar> vendor = new ArrayList<>();
		int total = content.jars().size();
		if (total > 0) {
			progress.accept(resolver == ArtifactResolver.OFFLINE ? "Vendoring " + total + " directory jar(s) (offline)"
					: "Identifying " + total + " directory jar(s) on Maven Central...");
		}
		ArtifactResolver active = resolver;
		int failures = 0;
		int done = 0;
		for (Path jar : content.jars()) {
			if (++done % PROGRESS_EVERY == 0) {
				progress.accept("Identified " + done + "/" + total + " jars...");
			}
			JarInfo info;
			try {
				info = JarInfo.read(jar);
			} catch (IOException | RuntimeException e) {
				warnings.add("Target jar " + jar + " unreadable (" + e.getMessage() + "); skipped");
				continue;
			}
			if (info.bsn() == null) {
				warnings.add("Target jar " + jar + " has no Bundle-SymbolicName (PDE ignores it too); skipped");
				continue;
			}
			Optional<String> gav = Optional.empty();
			String reason = "offline";
			if (active != ArtifactResolver.OFFLINE) {
				try {
					gav = active.findBySha1(info.sha1());
					failures = 0;
					reason = "not on Maven Central";
				} catch (IOException e) {
					reason = "Maven Central lookup failed: " + e.getMessage();
					if (++failures >= MAX_CONSECUTIVE_LOOKUP_FAILURES) {
						warnings.add("Maven Central unreachable (" + e.getMessage()
								+ "); the remaining directory jars are vendored");
						active = ArtifactResolver.OFFLINE;
					}
				}
			}
			TargetJar entry = new TargetJar(jar.toString(), info.sha1(), info.bsn(), info.version(), gav.orElse(null),
					gav.isPresent() ? null : reason);
			(gav.isPresent() ? resolved : vendor).add(entry);
		}
		return new Target(file.toString(), content.locations(), resolved, vendor);
	}

	private static String activeTargetHandle(Path plugins) throws IOException {
		Path prefs = plugins.resolve("org.eclipse.core.runtime/.settings/org.eclipse.pde.core.prefs");
		if (!Files.isRegularFile(prefs)) {
			return null;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(prefs, StandardCharsets.ISO_8859_1)) {
			properties.load(reader);
		}
		return properties.getProperty("workspace_target_handle");
	}

	/** Handles: resource:/project/path, file:URI, or local:name (a .local_targets file). */
	private static Path resolveHandle(String handle, Path plugins, Map<String, Path> locations) {
		if (handle == null || handle.isBlank()) {
			return null;
		}
		if (handle.startsWith("resource:/")) {
			String resource = resourcePath(handle.substring("resource:".length()), locations);
			return resource == null ? null : Path.of(resource);
		}
		if (handle.startsWith("file:")) {
			return Path.of(URI.create(handle));
		}
		return plugins.resolve("org.eclipse.pde.core/.local_targets").resolve(handle.substring(handle.indexOf(':') + 1));
	}

	/** "/project/some/path" → the project's location plus the path. */
	private static String resourcePath(String arg, Map<String, Path> locations) {
		String rest = arg.startsWith("/") ? arg.substring(1) : arg;
		int slash = rest.indexOf('/');
		Path base = locations.get(slash < 0 ? rest : rest.substring(0, slash));
		if (base == null) {
			return null;
		}
		return (slash < 0 ? base : base.resolve(rest.substring(slash + 1))).toString();
	}

	private TargetReader.Variables variables(Path workspace, Map<String, Path> locations) {
		return (name, arg) -> switch (name) {
			case "eclipse_home" -> eclipseHome == null ? null : eclipseHome.toString();
			case "workspace_loc" -> arg == null ? workspace.toString() : resourcePath(arg, locations);
			case "project_loc" -> arg == null ? null : resourcePath(arg, locations);
			case "env_var" -> arg == null ? null : System.getenv(arg);
			case "system_property" -> arg == null ? null : System.getProperty(arg);
			default -> null;
		};
	}

	// ---- launches ------------------------------------------------------------

	private static List<Launch> readLaunches(Path plugins, Map<String, Path> locations, String groupId,
			List<String> warnings) throws IOException {
		Set<Path> files = new TreeSet<>();
		collectLaunchFiles(plugins.resolve("org.eclipse.debug.core/.launches"), 1, files);
		for (Path project : locations.values()) {
			collectLaunchFiles(project, 2, files);
		}
		List<Launch> launches = new ArrayList<>();
		for (Path file : files) {
			try {
				Launch launch = LaunchReader.read(file);
				if (launch == null) {
					continue;
				}
				if (LaunchReader.type(file).toLowerCase(Locale.ROOT).contains("junit")) {
					warnings.add("Launch " + launch.name() + " is a JUnit Plug-in Test launch; skipped (tests are not"
							+ " migrated into the product)");
					continue;
				}
				String application = LaunchReader.application(file);
				if (application != null) {
					warnings.add("Launch " + launch.name() + " runs application " + application + "; the generated"
							+ " product starts bundles only — add -application " + application
							+ " to launches[].programArgs (and the bundles it needs) if you want it");
				}
				launch = launch.withSelection(false,
						launch.productId() != null ? launch.productId() : groupId + ".product");
				launches.add(launch);
				try {
					checkConfigIni(launch,
							plugins.resolve("org.eclipse.pde.core").resolve(launch.name()).resolve("config.ini"), warnings);
				} catch (IOException | RuntimeException e) {
					// The check only adds hints; a broken last-run config.ini must not cost the launch.
					warnings.add("Launch " + launch.name() + ": could not check its last run's config.ini ("
							+ e.getMessage() + ")");
				}
			} catch (IOException | RuntimeException e) {
				// LaunchReader.parseEntry throws NumberFormatException for a malformed start level;
				// a malformed launch becomes a warning and the scan continues.
				warnings.add("Launch " + file + " skipped: " + e.getMessage());
			}
		}
		if (launches.size() == 1) {
			launches.set(0, launches.get(0).withSelection(true, launches.get(0).productId()));
		}
		return launches;
	}

	private static void collectLaunchFiles(Path dir, int depth, Set<Path> out) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir, depth)) {
			paths.filter(p -> p.getFileName().toString().endsWith(".launch") && Files.isRegularFile(p))
					.map(p -> p.toAbsolutePath().normalize()).forEach(out::add);
		}
	}

	/** PDE's last run records every bundle it really started, including ones "include required bundles" added. */
	private static void checkConfigIni(Launch launch, Path configIni, List<String> warnings) throws IOException {
		if (!Files.isRegularFile(configIni)) {
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(configIni, StandardCharsets.ISO_8859_1)) {
			properties.load(reader);
		}
		String bundles = properties.getProperty("osgi.bundles");
		if (bundles == null) {
			return;
		}
		Set<String> launched = launch.bundles().stream().map(LaunchBundle::id).collect(Collectors.toSet());
		for (String entry : bundles.split(",")) {
			Path path = Path.of(entry.trim().replaceFirst("^reference:", "").replaceFirst("^file:", "")
					.replaceFirst("@.*$", ""));
			if (!Files.exists(path)) {
				continue;
			}
			String bsn = Manifests.clause(Manifests.ofBundle(path).getValue("Bundle-SymbolicName"));
			if (bsn != null && !launched.contains(bsn)) {
				warnings.add("Launch " + launch.name() + ": its last run also started " + bsn
						+ " (added by 'include required bundles'). The product pulls in required bundles itself;"
						+ " if " + bsn + " is needed but not required by any import, add it to the launch's bundles");
			}
		}
	}
}
