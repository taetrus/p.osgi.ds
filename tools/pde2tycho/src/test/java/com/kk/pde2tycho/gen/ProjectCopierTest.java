package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Build output is skipped only at the project root, so source packages named bin/target survive. */
class ProjectCopierTest {

	@TempDir
	Path tmp;

	private void touch(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, "x");
	}

	@Test
	void copiesSourcesAndSkipsRootBuildOutput() throws IOException {
		Path src = tmp.resolve("p");
		touch(src.resolve("META-INF/MANIFEST.MF"));
		touch(src.resolve(".settings/org.eclipse.jdt.core.prefs"));
		touch(src.resolve("src_test/x/ATest.java"));
		touch(src.resolve("bin/x/A.class"));
		touch(src.resolve("target/p.jar"));
		touch(src.resolve(".git/HEAD"));
		Path dst = tmp.resolve("out/p");
		ProjectCopier.copy(src, dst);
		assertTrue(Files.exists(dst.resolve("META-INF/MANIFEST.MF")));
		assertTrue(Files.exists(dst.resolve(".settings/org.eclipse.jdt.core.prefs")));
		assertTrue(Files.exists(dst.resolve("src_test/x/ATest.java")));
		assertFalse(Files.exists(dst.resolve("bin")));
		assertFalse(Files.exists(dst.resolve("target")));
		assertFalse(Files.exists(dst.resolve(".git")));
	}

	@Test
	void nestedBinPackageIsCopied() throws IOException {
		Path src = tmp.resolve("p");
		touch(src.resolve("src/com/x/bin/Tool.java"));
		touch(src.resolve("src/com/x/target/Aim.java"));
		Path dst = tmp.resolve("out/p");
		ProjectCopier.copy(src, dst);
		assertTrue(Files.exists(dst.resolve("src/com/x/bin/Tool.java")));
		assertTrue(Files.exists(dst.resolve("src/com/x/target/Aim.java")));
	}
}
