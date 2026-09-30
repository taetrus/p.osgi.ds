package com.kk.pde2tycho;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.kk.pde2tycho.gen.GenerationException;
import com.kk.pde2tycho.gen.Generator;
import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.InventoryJson;
import com.kk.pde2tycho.resolve.ArtifactResolver;
import com.kk.pde2tycho.resolve.CentralResolver;
import com.kk.pde2tycho.scan.WorkspaceScanner;

/** Command line: scan a workspace into migration.json, then generate a Tycho build from it. */
public final class Main {

	static final String USAGE = String.join("\n", "Usage:",
			"  pde2tycho scan <workspace> [-o migration.json] [--offline] [--eclipse-home <dir>] [--add-project <dir>]...",
			"  pde2tycho generate <migration.json> <outdir> [--force] [--linux]");

	private Main() {
	}

	public static void main(String[] args) {
		int code;
		try {
			code = run(args, System.out, System.err);
		} catch (IOException | RuntimeException e) {
			System.err.println("pde2tycho: " + e.getMessage());
			code = 1;
		}
		System.exit(code);
	}

	static int run(String[] args, PrintStream out, PrintStream err) throws IOException {
		if (args.length == 0) {
			return usage(err, null);
		}
		List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
		return switch (args[0]) {
			case "scan" -> scan(rest, out, err);
			case "generate" -> generate(rest, out, err);
			default -> usage(err, "unknown command " + args[0]);
		};
	}

	private static int usage(PrintStream err, String problem) {
		if (problem != null) {
			err.println("pde2tycho: " + problem);
		}
		err.println(USAGE);
		return 2;
	}

	private static int scan(List<String> args, PrintStream out, PrintStream err) throws IOException {
		Path output = Path.of("migration.json");
		Path eclipseHome = null;
		boolean offline = false;
		List<Path> extra = new ArrayList<>();
		List<String> positional = new ArrayList<>();
		for (int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			boolean hasValue = i + 1 < args.size();
			switch (arg) {
				case "-o" -> {
					if (!hasValue) {
						return usage(err, "-o needs a file name");
					}
					output = Path.of(args.get(++i));
				}
				case "--eclipse-home" -> {
					if (!hasValue) {
						return usage(err, "--eclipse-home needs a directory");
					}
					eclipseHome = Path.of(args.get(++i));
				}
				case "--add-project" -> {
					if (!hasValue) {
						return usage(err, "--add-project needs a directory");
					}
					extra.add(Path.of(args.get(++i)));
				}
				case "--offline" -> offline = true;
				default -> {
					if (arg.startsWith("-")) {
						return usage(err, "unknown option " + arg);
					}
					positional.add(arg);
				}
			}
		}
		if (positional.size() != 1) {
			return usage(err, "scan needs exactly one workspace directory");
		}
		ArtifactResolver resolver = offline ? ArtifactResolver.OFFLINE : new CentralResolver();
		Inventory inv = new WorkspaceScanner(resolver, eclipseHome, extra, err::println)
				.scan(Path.of(positional.get(0)).toAbsolutePath().normalize());
		if (Files.exists(output)) {
			// migration.json is meant to be hand-edited; a rescan must not silently lose those edits.
			Path backup = output.resolveSibling(output.getFileName() + ".bak");
			Files.copy(output, backup, StandardCopyOption.REPLACE_EXISTING);
			out.println("Backed up the existing " + output + " to " + backup);
		}
		Files.writeString(output, InventoryJson.write(inv));
		out.printf("Scanned %d project(s), %d selected; target: %d verbatim location(s), %d jar(s) resolved,"
				+ " %d vendored; %d launch(es); %d warning(s)%n", inv.projects().size(), inv.modules().size(),
				inv.target().locations().size(), inv.target().resolved().size(), inv.target().vendor().size(),
				inv.launches().size(), inv.warnings().size());
		out.println("Wrote " + output + ". Review projects[].selected and launches[].selected, then run:"
				+ " pde2tycho generate " + output + " <outdir>");
		return 0;
	}

	private static int generate(List<String> args, PrintStream out, PrintStream err) throws IOException {
		boolean force = false;
		boolean linux = false;
		List<String> positional = new ArrayList<>();
		for (String arg : args) {
			switch (arg) {
				case "--force" -> force = true;
				case "--linux" -> linux = true;
				default -> {
					if (arg.startsWith("-")) {
						return usage(err, "unknown option " + arg);
					}
					positional.add(arg);
				}
			}
		}
		if (positional.size() != 2) {
			return usage(err, "generate needs <migration.json> <outdir>");
		}
		Inventory inv = InventoryJson.read(Files.readString(Path.of(positional.get(0))));
		if (linux && !inv.environments().contains("linux/gtk/x86_64")) {
			List<String> environments = new ArrayList<>(inv.environments());
			environments.add("linux/gtk/x86_64");
			inv = inv.withEnvironments(environments);
		}
		Path target = Path.of(positional.get(1)).toAbsolutePath().normalize();
		try {
			Generator.generate(inv, target, force);
		} catch (GenerationException e) {
			err.println("pde2tycho: cannot generate from " + positional.get(0) + ":");
			e.errors().forEach(message -> err.println("  - " + message));
			return 1;
		}
		out.println("Generated a Tycho build in " + target + ". Read MIGRATION-REPORT.md, then run: mvn clean verify");
		return 0;
	}
}
