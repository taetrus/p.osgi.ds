package com.kk.pde2tycho.scan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.Attributes;

/** A jar's OSGi identity (null bsn = not a bundle) and SHA-1, the Maven Central search key. */
public record JarInfo(String bsn, String version, String sha1) {

	public static JarInfo read(Path jar) throws IOException {
		Attributes headers = Manifests.ofJar(jar);
		String version = headers.getValue("Bundle-Version");
		return new JarInfo(Manifests.clause(headers.getValue("Bundle-SymbolicName")),
				version == null ? "0.0.0" : version.trim(), sha1(jar));
	}

	public static String sha1(Path file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every JDK ships SHA-1", e);
		}
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			int n;
			while ((n = in.read(buffer)) > 0) {
				digest.update(buffer, 0, n);
			}
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
