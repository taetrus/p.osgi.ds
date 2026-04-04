package com.kk.pde.ds.chatbot;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.llm.LlmJsonUtil;
import com.kk.pde.ds.mcp.llm.OpenRouterAgent;

/**
 * DS component that manages chat conversations with an LLM.
 * Maintains message history and delegates to OpenRouterAgent for completions.
 *
 * Registered as a service so other bundles (e.g. App) can inject it.
 */
@Component(service = ChatService.class)
public class ChatService {

	private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
	private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

	private OpenRouterAgent agent;
	private final List<String> history = Collections.synchronizedList(new ArrayList<String>());
	private String currentModel;
	private String baseUrl;
	private String apiKeyOverride;

	@Activate
	public void activate() {
		LOG.info("ChatService activated");
	}

	@Reference
	public void setAgent(OpenRouterAgent agent) {
		this.agent = agent;
		LOG.info("ChatService: OpenRouterAgent injected");
	}

	/**
	 * Send a user message and get the LLM's response.
	 * The message and response are added to conversation history.
	 *
	 * @param userMessage the user's text input
	 * @return the LLM's response text
	 */
	public String send(String userMessage) {
		if (userMessage == null || userMessage.trim().isEmpty()) {
			return "(empty message)";
		}

		history.add("{\"role\":\"user\",\"content\":\""
			+ LlmJsonUtil.escape(userMessage) + "\"}");

		LOG.info("Sending chat message (history size={}): {}",
			history.size(), userMessage);

		agent.setApiKey(getApiKey());
		agent.setBaseUrl(getBaseUrl());
		String response = agent.chatWithHistory(history, currentModel);

		LOG.info("Chat response: {}", response);
		return response;
	}

	/** Clear all conversation history. */
	public void clearHistory() {
		history.clear();
		LOG.info("Chat history cleared");
	}

	/** Get the number of messages in history. */
	public int getHistorySize() {
		return history.size();
	}

	/** Set the model ID to use for completions. Null uses the agent's default. */
	public void setModel(String model) {
		this.currentModel = model;
		LOG.info("Chat model set to: {}", model);
	}

	/** Get the current model ID, or null if using default. */
	public String getModel() {
		return currentModel;
	}

	/** Set the API base URL (e.g. "https://openrouter.ai/api/v1"). */
	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
		LOG.info("Chat base URL set to: {}", baseUrl);
	}

	/** Get the API base URL. */
	public String getBaseUrl() {
		if (baseUrl != null && !baseUrl.isEmpty()) {
			return baseUrl;
		}
		return System.getProperty("openrouter.base.url", DEFAULT_BASE_URL);
	}

	/** Get the API key: override > system property > OPENROUTER_API_KEY env var. */
	public String getApiKey() {
		if (apiKeyOverride != null && !apiKeyOverride.isEmpty()) {
			return apiKeyOverride;
		}
		String key = System.getProperty("openrouter.api.key", "");
		if (key.isEmpty()) {
			String envKey = System.getenv("OPENROUTER_API_KEY");
			if (envKey != null && !envKey.isEmpty()) {
				return envKey;
			}
		}
		return key;
	}

	/** Set an API key override (takes priority over system property and env var). */
	public void setApiKey(String apiKey) {
		this.apiKeyOverride = apiKey;
		LOG.info("Chat API key override set");
	}

	/**
	 * Fetch available models from the configured API endpoint.
	 * Runs synchronously — caller should invoke from a background thread.
	 */
	public List<String> fetchModels() {
		return ModelFetcher.fetchModels(getBaseUrl(), getApiKey());
	}
}
