package com.kk.pde2tycho.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The SHA-1 is what Maven Central is searched by, so it must be the plain file digest. */
class JarInfoTest {

	@TempDir
	Path dir;

	@Test
	void sha1IsTheFileDigest() throws IOException {
		Path file = Files.writeString(dir.resolve("abc.txt"), "abc");
		assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", JarInfo.sha1(file));
	}

	@Test
	void readsBundleIdentity() throws IOException {
		JarInfo info = JarInfo.read(TestJars.bundle(dir.resolve("a.jar"), "org.a;singleton:=true", "1.2.3"));
		assertEquals("org.a", info.bsn());
		assertEquals("1.2.3", info.version());
	}

	@Test
	void plainJarHasNoBundleName() throws IOException {
		assertNull(JarInfo.read(TestJars.plain(dir.resolve("p.jar"))).bsn());
	}
}
