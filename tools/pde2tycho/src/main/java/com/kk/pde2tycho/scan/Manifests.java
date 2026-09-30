package com.kk.pde2tycho.scan;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Reads OSGi headers from MANIFEST.MF files, jars and unpacked bundle directories. */
final class Manifests {

	private Manifests() {
	}

	/**
	 * Main attributes of a MANIFEST.MF. A newline is appended when the file lacks one:
	 * java.util.jar.Manifest otherwise silently drops the last header, and hand-edited
	 * manifests often end without one.
	 */
	static Attributes ofFile(Path manifest) throws IOException {
		byte[] bytes = Files.readAllBytes(manifest);
		if (bytes.length > 0 && bytes[bytes.length - 1] != '\n') {
			bytes = Arrays.copyOf(bytes, bytes.length + 1);
			bytes[bytes.length - 1] = '\n';
		}
		return new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes();
	}

	static Attributes ofJar(Path jar) throws IOException {
		try (JarFile file = new JarFile(jar.toFile())) {
			Manifest manifest = file.getManifest();
			return manifest == null ? new Attributes() : manifest.getMainAttributes();
		}
	}

	/** A bundle given as a jar or as a directory (how PDE runs workspace projects). */
	static Attributes ofBundle(Path bundle) throws IOException {
		if (Files.isDirectory(bundle)) {
			Path manifest = bundle.resolve("META-INF/MANIFEST.MF");
			return Files.isRegularFile(manifest) ? ofFile(manifest) : new Attributes();
		}
		return ofJar(bundle);
	}

	/** First clause of a header without directives: "a.b;singleton:=true" → "a.b". */
	static String clause(String header) {
		if (header == null) {
			return null;
		}
		String first = header.split("[;,]", 2)[0].trim();
		return first.isEmpty() ? null : first;
	}
}
