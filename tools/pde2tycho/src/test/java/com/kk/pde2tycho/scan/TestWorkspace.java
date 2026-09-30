package com.kk.pde2tycho.scan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a minimal Eclipse workspace the way Eclipse lays it out on disk: projects live
 * outside the workspace (like a git checkout) and are registered through binary
 * .location files under .metadata.
 */
final class TestWorkspace {

	final Path root;
	final Path sources;

	TestWorkspace(Path tmp) throws IOException {
		root = tmp.resolve("ws");
		sources = tmp.resolve("src");
		Files.createDirectories(plugins());
	}

	Path plugins() {
		return root.resolve(".metadata/.plugins");
	}

	/** Registers an (empty) project at sources/dirName under the given Eclipse project name. */
	Path project(String name, String dirName) throws IOException {
		Path dir = Files.createDirectories(sources.resolve(dirName));
		Path meta = Files.createDirectories(plugins().resolve("org.eclipse.core.resources/.projects/" + name));
		Files.write(meta.resolve(".location"), locationBytes(dir.toUri().toString()));
		return dir;
	}

	Path bundle(String name, String bsn) throws IOException {
		Path dir = project(name, name);
		write(dir.resolve("META-INF/MANIFEST.MF"),
				"Manifest-Version: 1.0\nBundle-SymbolicName: " + bsn + "\nBundle-Version: 1.0.0.qualifier\n");
		return dir;
	}

	void prefs(String handle) throws IOException {
		write(plugins().resolve("org.eclipse.core.runtime/.settings/org.eclipse.pde.core.prefs"),
				"eclipse.preferences.version=1\nworkspace_target_handle=" + handle.replace(":", "\\:") + "\n");
	}

	void launch(String name, String xml) throws IOException {
		write(plugins().resolve("org.eclipse.debug.core/.launches/" + name + ".launch"), xml);
	}

	static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	/** Eclipse's layout: 16-byte header, then a 2-byte big-endian length and "URI//" + the URI. */
	static byte[] locationBytes(String uri) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { 0x40, (byte) 0xb1, (byte) 0x8b, (byte) 0x81, 0x23, (byte) 0xbc, 0x00, 0x14,
				0x1a, 0x25, (byte) 0x96, (byte) 0xe7, (byte) 0xa3, (byte) 0x93, (byte) 0xbe, 0x1e });
		byte[] text = ("URI//" + uri).getBytes(StandardCharsets.UTF_8);
		out.write(text.length >> 8);
		out.write(text.length & 0xff);
		out.writeBytes(text);
		out.writeBytes(new byte[16]);
		return out.toByteArray();
	}
}
