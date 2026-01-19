package com.kk.pde.ds.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;

final class McpSettings {

	private static final String SETTINGS_DIR_PROPERTY = "mcp.settings.dir";
	private static final String INSTALL_AREA_PROPERTY = "osgi.install.area";
	private static final String SETTINGS_RELATIVE_PATH = "settings/mcp.json";

	private McpSettings() {
	}

	static String load(Logger log) {
		Path settingsFile = resolveExternalSettings(log);
		if (settingsFile != null) {
			return readFile(settingsFile, log, settingsFile.toString());
		}

		String classpathResource = "/" + SETTINGS_RELATIVE_PATH;
		try (InputStream input = McpSettings.class.getResourceAsStream(classpathResource)) {
			if (input == null) {
				log.warn("mcp.json not found (checked {}, {}, and classpath {}).", SETTINGS_DIR_PROPERTY,
						INSTALL_AREA_PROPERTY, classpathResource);
				return null;
			}
			byte[] content = readStream(input);
			log.info("Loaded mcp.json from classpath:{} ({} bytes).", SETTINGS_RELATIVE_PATH, content.length);
			return new String(content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warn("Failed to read mcp.json from classpath:{}.", SETTINGS_RELATIVE_PATH, e);
			return null;
		}
	}

	private static Path resolveExternalSettings(Logger log) {
		String overrideDir = System.getProperty(SETTINGS_DIR_PROPERTY);
		if (overrideDir != null && !overrideDir.trim().isEmpty()) {
			Path overridePath = Paths.get(overrideDir).resolve("mcp.json");
			if (Files.isRegularFile(overridePath)) {
				return overridePath;
			}
			log.warn("mcp.settings.dir set to {}, but {} was not found.", overrideDir, overridePath);
		}

		Path installPath = resolveInstallAreaPath(log);
		if (installPath != null && Files.isRegularFile(installPath)) {
			return installPath;
		}

		Path workingDirPath = Paths.get(SETTINGS_RELATIVE_PATH);
		if (Files.isRegularFile(workingDirPath)) {
			return workingDirPath;
		}

		return null;
	}

	private static Path resolveInstallAreaPath(Logger log) {
		String installArea = System.getProperty(INSTALL_AREA_PROPERTY);
		if (installArea == null || installArea.trim().isEmpty()) {
			return null;
		}

		try {
			URI installUri = new URI(installArea);
			Path installPath;
			if (installUri.getScheme() == null) {
				installPath = Paths.get(installArea);
			} else if ("file".equalsIgnoreCase(installUri.getScheme())) {
				installPath = Paths.get(installUri);
			} else {
				return null;
			}
			return installPath.resolve(SETTINGS_RELATIVE_PATH);
		} catch (URISyntaxException | IllegalArgumentException e) {
			log.debug("Unable to parse osgi.install.area: {}", installArea, e);
			return null;
		}
	}

	private static String readFile(Path path, Logger log, String source) {
		try {
			byte[] content = Files.readAllBytes(path);
			log.info("Loaded mcp.json from {} ({} bytes).", source, content.length);
			return new String(content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warn("Failed to read mcp.json from {}.", source, e);
			return null;
		}
	}

	private static byte[] readStream(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = input.read(buffer)) != -1) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}
}
