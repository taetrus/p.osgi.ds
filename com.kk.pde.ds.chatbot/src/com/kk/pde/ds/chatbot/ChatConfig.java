package com.kk.pde.ds.chatbot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages chatbot configuration with file-based persistence.
 *
 * <p>On first use, if no config file exists, one is created with default values.
 * Settings are saved to {@code settings/chatbot.properties} relative to the
 * working directory (the product root).
 *
 * <p>Resolution priority for each setting:
 * <ol>
 *   <li>Saved file value (if non-empty)</li>
 *   <li>System property ({@code -D})</li>
 *   <li>Environment variable (API key only)</li>
 *   <li>Code default</li>
 * </ol>
 */
public final class ChatConfig {

	private static final Logger LOG = LoggerFactory.getLogger(ChatConfig.class);

	// Code defaults
	public static final String DEFAULT_MODEL = "minimax/minimax-m3.1-flash:free";
	public static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

	// Property keys
	private static final String KEY_API_KEY = "api.key";
	private static final String KEY_MODEL = "model";
	private static final String KEY_BASE_URL = "base.url";

	private static final String CONFIG_DIR = "settings";
	private static final String CONFIG_FILE = "chatbot.properties";

	private final Properties props = new Properties();
	private final File configFile;

	public ChatConfig() {
		this(new File(CONFIG_DIR, CONFIG_FILE));
	}

	/** Use a specific settings file. Package-private so tests can use a temporary file. */
	ChatConfig(File configFile) {
		this.configFile = configFile;
		load();
	}

	/**
	 * Load configuration from file. If the file doesn't exist, create it
	 * with default values so the user can discover and edit it.
	 */
	private void load() {
		if (configFile.exists()) {
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(configFile);
				props.load(fis);
				LOG.info("Loaded config from {}", configFile.getAbsolutePath());
			} catch (IOException e) {
				LOG.warn("Failed to load config: {}", e.getMessage());
			} finally {
				if (fis != null) {
					try { fis.close(); } catch (IOException e) { /* ignore */ }
				}
			}
		} else {
			LOG.info("No config file found at {}, creating with empty (fall-through) values",
				configFile.getAbsolutePath());
			// Write the keys empty, not with the code defaults: an empty value falls
			// through to the -D system property (then the default), so a persisted
			// default never shadows -Dopenrouter.model / -Dopenrouter.base.url on the
			// next launch. Only a value the user explicitly sets here takes precedence.
			props.setProperty(KEY_API_KEY, "");
			props.setProperty(KEY_MODEL, "");
			props.setProperty(KEY_BASE_URL, "");
			save();
		}
	}

	/**
	 * Persist current settings to disk on a background thread.
	 * Safe to call from the EDT — file I/O is offloaded so the
	 * event dispatch thread is never blocked by disk writes.
	 */
	public void saveAsync() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				save();
			}
		}, "ChatConfig-save").start();
	}

	/** Persist current settings to disk (blocks the calling thread). */
	public void save() {
		File dir = configFile.getParentFile();
		if (dir != null && !dir.exists()) {
			dir.mkdirs();
		}
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(configFile);
			props.store(fos, "OSGi Chatbot Settings");
			LOG.info("Saved config to {}", configFile.getAbsolutePath());
		} catch (IOException e) {
			LOG.error("Failed to save config: {}", e.getMessage());
		} finally {
			if (fos != null) {
				try { fos.close(); } catch (IOException e) { /* ignore */ }
			}
		}
	}

	// Each setting has three views:
	//   saved    - the value in the settings file only ("" when not set). The Settings
	//              dialog shows and edits only this value.
	//   fallback - the value used when the saved value is empty (-D, env var, default).
	//   effective (getApiKey/getModel/getBaseUrl) - saved if set, else fallback.
	// The dialog must never copy a fallback into the file: a saved value has priority on
	// every later start, so it would hide a later -D change, and a key from the
	// environment would land in a plaintext file.

	/** Get the API key. Priority: file > system property > env var > empty. */
	public String getApiKey() {
		String saved = getSavedApiKey();
		return saved.isEmpty() ? getFallbackApiKey() : saved;
	}

	/** The API key in the settings file, or "" when the file has none. */
	public String getSavedApiKey() {
		return props.getProperty(KEY_API_KEY, "").trim();
	}

	/** The API key from {@code -Dopenrouter.api.key}, else {@code OPENROUTER_API_KEY}, else "". */
	public String getFallbackApiKey() {
		String val = System.getProperty("openrouter.api.key", "");
		if (!val.isEmpty()) return val;

		String envKey = System.getenv("OPENROUTER_API_KEY");
		return envKey != null ? envKey : "";
	}

	/** Where the fallback API key comes from, for display. Never contains the key. */
	public String getFallbackApiKeySource() {
		if (!System.getProperty("openrouter.api.key", "").isEmpty()) {
			return "-Dopenrouter.api.key";
		}
		String envKey = System.getenv("OPENROUTER_API_KEY");
		if (envKey != null && !envKey.isEmpty()) {
			return "OPENROUTER_API_KEY";
		}
		return "";
	}

	public void setApiKey(String apiKey) {
		props.setProperty(KEY_API_KEY, apiKey != null ? apiKey : "");
	}

	/** Get the model ID. Priority: file > system property > code default. */
	public String getModel() {
		String saved = getSavedModel();
		return saved.isEmpty() ? getFallbackModel() : saved;
	}

	/** The model in the settings file, or "" when the file has none. */
	public String getSavedModel() {
		return props.getProperty(KEY_MODEL, "").trim();
	}

	/** The model from {@code -Dopenrouter.model}, else the code default. */
	public String getFallbackModel() {
		return System.getProperty("openrouter.model", DEFAULT_MODEL);
	}

	public void setModel(String model) {
		props.setProperty(KEY_MODEL, model != null ? model : "");
	}

	/** Get the API base URL. Priority: file > system property > code default. */
	public String getBaseUrl() {
		String saved = getSavedBaseUrl();
		return saved.isEmpty() ? getFallbackBaseUrl() : saved;
	}

	/** The base URL in the settings file, or "" when the file has none. */
	public String getSavedBaseUrl() {
		return props.getProperty(KEY_BASE_URL, "").trim();
	}

	/** The base URL from {@code -Dopenrouter.base.url}, else the code default. */
	public String getFallbackBaseUrl() {
		return System.getProperty("openrouter.base.url", DEFAULT_BASE_URL);
	}

	public void setBaseUrl(String baseUrl) {
		props.setProperty(KEY_BASE_URL, baseUrl != null ? baseUrl : "");
	}
}
