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

/** Writes the whole Tycho build for a validated inventory. Overwrites files, never deletes. */
public final class Generator {

	private Generator() {
	}

	public static void generate(Inventory inv, Path out, boolean force) throws IOException, GenerationException {
		List<String> errors = InventoryValidator.validate(inv);
		if (!errors.isEmpty()) {
			throw new GenerationException(errors);
		}
		if (!force && Files.isDirectory(out)) {
			try (Stream<Path> entries = Files.list(out)) {
				if (entries.findAny().isPresent()) {
					throw new GenerationException(List.of(out + " is not empty; pass --force to write into it"
							+ " (existing files are overwritten, nothing is deleted)"));
				}
			}
		}
		Launch launch = inv.selectedLaunch();
		List<String> notes = new ArrayList<>();
		List<String> modules = new ArrayList<>();
		String targetId = PomGenerator.targetId(inv);
		TargetGenerator.generate(inv, out.resolve(targetId));
		modules.add(targetId);
		for (Project project : inv.modules()) {
			Path source = Path.of(project.path());
			if (Files.isRegularFile(source.resolve("pom.xml"))) {
				notes.add("Project " + project.name() + " had its own pom.xml; it was replaced by a generated one");
			}
			ProjectCopier.copy(source, out.resolve(project.name()));
			Output.write(out.resolve(project.name()).resolve("pom.xml"), PomGenerator.module(inv, project));
			modules.add(project.name());
		}
		LauncherArgs.Rewritten args = LauncherArgs.rewrite(launch.vmArgs(), launch.programArgs(), notes);
		DistributionGenerator.generate(inv, launch, args, out.resolve("distribution"));
		ScriptGenerator.generate(inv, launch, args, out.resolve("distribution/scripts"));
		modules.add("distribution");
		Output.write(out.resolve("pom.xml"), PomGenerator.parent(inv, modules));
		Output.write(out.resolve(".gitignore"), "target/\n");
		Output.write(out.resolve("MIGRATION-REPORT.md"), ReportGenerator.report(inv, launch, notes));
	}
}
