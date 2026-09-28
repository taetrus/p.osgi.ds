package com.kk.pde2tycho.scan;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Writes small jars for tests. */
final class TestJars {

	private TestJars() {
	}

	static Path bundle(Path file, String bsn, String version) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().putValue("Bundle-ManifestVersion", "2");
		manifest.getMainAttributes().putValue("Bundle-SymbolicName", bsn);
		manifest.getMainAttributes().putValue("Bundle-Version", version);
		Files.createDirectories(file.getParent());
		try (OutputStream out = Files.newOutputStream(file); JarOutputStream jar = new JarOutputStream(out, manifest)) {
			// manifest only
		}
		return file;
	}

	/** No manifest: not an OSGi bundle. One entry, because an empty zip cannot be written. */
	static Path plain(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (OutputStream out = Files.newOutputStream(file); JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("readme.txt"));
			jar.write("not a bundle".getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}
		return file;
	}
}
