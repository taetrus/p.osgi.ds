package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.Project;

/**
 * Writes the whole Tycho build for a validated inventory. Overwrites files; the only files it
 * deletes are ones an earlier run generated and this one would not ({@code distribution/*.product},
 * vendored target jars).
 */
public final class Generator {

	private Generator() {
	}

	public static void generate(Inventory inv, Path out, boolean force) throws IOException, GenerationException {
		List<String> errors = InventoryValidator.validate(inv);
		if (!errors.isEmpty()) {
			throw new GenerationException(errors);
		}
		List<String> overlaps = new ArrayList<>();
		for (Project project : inv.modules()) {
			Path source = Path.of(project.path()).toAbsolutePath().normalize();
			if (out.startsWith(source) || source.startsWith(out)) {
				overlaps.add("The output directory " + out + " overlaps project " + project.name() + " (" + source
						+ "); generate into a directory outside every selected project");
			}
		}
		if (!overlaps.isEmpty()) {
			throw new GenerationException(overlaps);
		}
		if (!force && Files.isDirectory(out)) {
			try (Stream<Path> entries = Files.list(out)) {
				if (entries.findAny().isPresent()) {
					throw new GenerationException(List.of(out + " is not empty; pass --force to write into it"
							+ " (existing files are overwritten; stale generated products and vendored jars are removed)"));
				}
			}
		}
		Launch launch = inv.selectedLaunch();
		List<String> notes = new ArrayList<>();
		String targetId = PomGenerator.targetId(inv);
		List<String> modules = new ArrayList<>();
		modules.add(targetId);
		inv.modules().forEach(project -> modules.add(project.name()));
		modules.add("distribution");
		removeStaleGeneratedFiles(out, targetId);
		noteStaleModules(out, modules, notes);
		TargetGenerator.generate(inv, out.resolve(targetId));
		for (Project project : inv.modules()) {
			Path source = Path.of(project.path());
			if (Files.isRegularFile(source.resolve("pom.xml"))) {
				notes.add("Project " + project.name() + " had its own pom.xml; it was replaced by a generated one");
			}
			ProjectCopier.copy(source, out.resolve(project.name()));
			Output.write(out.resolve(project.name()).resolve("pom.xml"), PomGenerator.module(inv, project));
		}
		LauncherArgs.Rewritten args = LauncherArgs.rewrite(launch.vmArgs(), launch.programArgs(), notes);
		DistributionGenerator.generate(inv, launch, args, out.resolve("distribution"));
		ScriptGenerator.generate(inv, launch, args, out.resolve("distribution/scripts"));
		Output.write(out.resolve("pom.xml"), PomGenerator.parent(inv, modules));
		// Anchored, so a source package or a module named "target" is never ignored.
		Output.write(out.resolve(".gitignore"), "/target/\n/*/target/\n");
		Output.write(out.resolve("MIGRATION-REPORT.md"), ReportGenerator.report(inv, launch, notes));
	}

	/** An earlier run's product (another productId) or vendored jars would otherwise be built again. */
	private static void removeStaleGeneratedFiles(Path out, String targetId) throws IOException {
		deleteMatching(out.resolve("distribution"), ".product");
		deleteMatching(out.resolve(targetId).resolve("vendor/plugins"), ".jar");
	}

	private static void deleteMatching(Path dir, String suffix) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				if (file.getFileName().toString().endsWith(suffix) && Files.isRegularFile(file)) {
					Files.delete(file);
				}
			}
		}
	}

	/** Module directories a previous run generated for since-deselected projects; the user decides. */
	private static void noteStaleModules(Path out, List<String> modules, List<String> notes) throws IOException {
		if (!Files.isDirectory(out)) {
			return;
		}
		try (Stream<Path> entries = Files.list(out)) {
			for (Path dir : (Iterable<Path>) entries.sorted()::iterator) {
				String name = dir.getFileName().toString();
				if (Files.isRegularFile(dir.resolve("pom.xml")) && !modules.contains(name)) {
					notes.add("Directory " + name + " has a pom.xml but is not a module of this build (left over from"
							+ " an earlier run?); it was not deleted — remove it if it is stale");
				}
			}
		}
	}
}
