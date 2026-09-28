package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** Copies a project into the output, leaving out build output and VCS data at its root. */
final class ProjectCopier {

	/** Only skipped directly under the project root, so a source package named "bin" survives. */
	private static final Set<String> SKIPPED_AT_ROOT = Set.of("bin", "bin_test", "target", ".git");

	private ProjectCopier() {
	}

	static void copy(Path source, Path destination) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (source.equals(dir.getParent()) && SKIPPED_AT_ROOT.contains(dir.getFileName().toString())) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				Files.createDirectories(destination.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, destination.resolve(source.relativize(file).toString()),
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
