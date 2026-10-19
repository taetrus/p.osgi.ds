package com.kk.pde.ds.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ChatConfig}: which value wins, and what the Settings dialog may save.
 *
 * <h2>What this class teaches</h2>
 * <p>
 * A setting with several sources (a file, a {@code -D} system property, an environment
 * variable, a code default) needs two different views. The <em>effective</em> value is
 * what the chatbot uses. The <em>saved</em> value is what the file holds. A UI that edits
 * the file must show the saved value, not the effective value. Otherwise Save copies a
 * fallback into the file, and the file then hides every later change to {@code -D}.
 * </p>
 * <p>
 * That was a real bug: the Settings dialog showed the effective values, so Save wrote a
 * key from {@code OPENROUTER_API_KEY} into the plaintext settings file, and a model from
 * {@code -Dopenrouter.model} became permanent.
 * {@link #savingTheSavedViewKeepsLaterSystemPropertyChangesLive()} guards against it.
 * </p>
 * <p>
 * Each test uses a {@link TempDir} file through the package-private constructor, and
 * restores the system properties it sets. The environment variable cannot be set from
 * Java, so the tests use the {@code -D} property, which has priority over it.
 * </p>
 */
class ChatConfigTest {

	private static final String[] PROPS = {
		"openrouter.api.key", "openrouter.model", "openrouter.base.url"
	};

	@TempDir
	File tempDir;

	private final Properties savedSystemProps = new Properties();

	@BeforeEach
	void rememberSystemProperties() {
		for (String p : PROPS) {
			String v = System.getProperty(p);
			if (v != null) {
				savedSystemProps.setProperty(p, v);
			}
			System.clearProperty(p);
		}
	}

	@AfterEach
	void restoreSystemProperties() {
		for (String p : PROPS) {
			System.clearProperty(p);
			String v = savedSystemProps.getProperty(p);
			if (v != null) {
				System.setProperty(p, v);
			}
		}
	}

	private File settingsFile() {
		return new File(tempDir, "settings/chatbot.properties");
	}

	@Test
	void firstStartCreatesTheFileWithEmptyValues() throws IOException {
		new ChatConfig(settingsFile());

		Properties onDisk = new Properties();
		try (FileInputStream in = new FileInputStream(settingsFile())) {
			onDisk.load(in);
		}
		assertEquals("", onDisk.getProperty("api.key"));
		assertEquals("", onDisk.getProperty("model"));
		assertEquals("", onDisk.getProperty("base.url"));
	}

	@Test
	void emptySavedValueFallsBackToSystemPropertyThenDefault() {
		ChatConfig config = new ChatConfig(settingsFile());
		assertEquals(ChatConfig.DEFAULT_MODEL, config.getModel());

		System.setProperty("openrouter.model", "vendor/from-system-property");
		assertEquals("vendor/from-system-property", config.getModel());
	}

	@Test
	void savedValueWinsOverSystemProperty() {
		System.setProperty("openrouter.model", "vendor/from-system-property");
		ChatConfig config = new ChatConfig(settingsFile());
		config.setModel("vendor/saved");

		assertEquals("vendor/saved", config.getModel());
		assertEquals("vendor/from-system-property", config.getFallbackModel());
	}

	@Test
	void savedViewNeverContainsAFallbackKey() {
		System.setProperty("openrouter.api.key", "sk-from-system-property");
		ChatConfig config = new ChatConfig(settingsFile());

		assertEquals("sk-from-system-property", config.getApiKey(), "effective key");
		assertEquals("", config.getSavedApiKey(), "the dialog shows this: it must stay empty");
	}

	@Test
	void fallbackSourceNamesTheSourceButNotTheKey() {
		System.setProperty("openrouter.api.key", "sk-secret");
		ChatConfig config = new ChatConfig(settingsFile());

		String source = config.getFallbackApiKeySource();
		assertEquals("-Dopenrouter.api.key", source);
		assertFalse(source.contains("sk-secret"));
	}

	/**
	 * The regression test for the Settings dialog bug. It does what the dialog's Save
	 * button does: it writes the fields back to the file. The fields show the saved view,
	 * so nothing from {@code -D} reaches the file, and a later {@code -D} change still wins.
	 */
	@Test
	void savingTheSavedViewKeepsLaterSystemPropertyChangesLive() {
		System.setProperty("openrouter.api.key", "sk-first");
		System.setProperty("openrouter.model", "vendor/first");
		ChatConfig config = new ChatConfig(settingsFile());

		// The dialog's Save: copy each field (the saved view) back, then save.
		config.setApiKey(config.getSavedApiKey());
		config.setModel(config.getSavedModel());
		config.setBaseUrl(config.getSavedBaseUrl());
		config.save();

		// Next start, with different -D values.
		System.setProperty("openrouter.api.key", "sk-second");
		System.setProperty("openrouter.model", "vendor/second");
		ChatConfig restarted = new ChatConfig(settingsFile());

		assertEquals("sk-second", restarted.getApiKey());
		assertEquals("vendor/second", restarted.getModel());
		assertTrue(restarted.getSavedApiKey().isEmpty(), "no key was written to the file");
	}
}
