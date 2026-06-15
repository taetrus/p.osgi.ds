package com.kk.pde.ds.mcp.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;
import com.kk.pde.ds.mcp.api.IMcpToolRegistry;

/**
 * Bridges the OSGi MCP tool registry to the OpenRouter API (OpenAI-compatible).
 *
 * Reads available tools directly from IMcpToolRegistry via OSGi @Reference —
 * no HTTP call to the MCP server needed; tool execution happens in-process.
 *
 * Agent loop:
 *  1. Sends user message + tools to OpenRouter chat completions
 *  2. If model responds with tool_calls: executes the tool via the registry,
 *     appends the result to the conversation, and repeats
 *  3. If model responds with stop: returns the final text answer
 *
 * Configuration via system properties (set with -D at startup):
 *   openrouter.api.key   — required; your OpenRouter API key
 *   openrouter.model     — optional; model ID (default: google/gemini-flash-1.5)
 *   openrouter.base.url  — optional; API base URL (default: https://openrouter.ai/api/v1)
 */
@Component(service = OpenRouterAgent.class)
public class OpenRouterAgent {

	private static final Logger LOG = LoggerFactory.getLogger(OpenRouterAgent.class);
	private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
	private static final String DEFAULT_MODEL = "google/gemini-flash-1.5";
	private static final int MAX_TURNS = 10;

	/** Name of the RAG retrieval tool whose results carry document citations. */
	private static final String DOC_SEARCH_TOOL = "document_search";

	/** A citation line emitted by document_search, e.g. "[1] report.pdf (chunk 2)  (score 0.812)". */
	private static final Pattern CITATION_LINE = Pattern.compile("^\\s*\\[\\d+\\]\\s+(.*)$");

	private IMcpToolRegistry registry;
	private String apiKeyOverride;
	private String baseUrlOverride;

	@Activate
	public void activate() {
		LOG.info("OpenRouterAgent activated");
	}

	@Reference
	public void setRegistry(IMcpToolRegistry registry) {
		this.registry = registry;
	}

	/** Set an API key override (takes priority over system property and env var). */
	public void setApiKey(String apiKey) {
		this.apiKeyOverride = apiKey;
	}

	/**
	 * Set a base URL override. Takes priority over the {@code openrouter.base.url}
	 * system property and the built-in default.
	 */
	public void setBaseUrl(String baseUrl) {
		this.baseUrlOverride = baseUrl;
	}

	/**
	 * Run an agent conversation. Tools are auto-discovered from the OSGi registry.
	 *
	 * @param userMessage  the user's prompt
	 * @param modelOverride  optional model ID; null/empty uses system property or default
	 * @return final text answer from the LLM
	 */
	public String chat(String userMessage, String modelOverride) {
		String apiKey = resolveApiKey();
		if (apiKey.isEmpty()) {
			return "Error: No API key found. Set OPENROUTER_API_KEY env var "
				+ "or start with -Dopenrouter.api.key=your_key";
		}

		String model = (modelOverride != null && !modelOverride.isEmpty())
			? modelOverride
			: System.getProperty("openrouter.model", DEFAULT_MODEL);

		List<String> messages = new ArrayList<String>();
		messages.add("{\"role\":\"user\",\"content\":\"" + LlmJsonUtil.escape(userMessage) + "\"}");

		return chatWithHistory(messages, model);
	}

	/**
	 * Run an agent conversation with externally-managed message history.
	 * The caller provides the full message list (including prior user/assistant messages).
	 * New messages generated during tool-call loops are appended to the provided list.
	 *
	 * @param messages  mutable list of JSON message strings (caller retains reference)
	 * @param model     model ID to use
	 * @return final text answer from the LLM, or error string
	 */
	public String chatWithHistory(List<String> messages, String model) {
		String apiKey = resolveApiKey();
		if (apiKey.isEmpty()) {
			return "Error: No API key found. Set OPENROUTER_API_KEY env var "
				+ "or start with -Dopenrouter.api.key=your_key";
		}

		if (model == null || model.isEmpty()) {
			model = System.getProperty("openrouter.model", DEFAULT_MODEL);
		}

		String toolsJson = buildToolsJson();
		LOG.info("Chat with history: model={}, messages={}, tools={}",
			model, messages.size(), registry.getTools().size());

		// Documents cited via document_search during this answer (insertion-ordered, deduped).
		Set<String> referencedDocs = new LinkedHashSet<String>();

		for (int turn = 0; turn < MAX_TURNS; turn++) {
			String requestBody = buildRequest(model, messages, toolsJson);
			String response = post(apiKey, requestBody);

			if (response == null) {
				return "Error: failed to reach OpenRouter API";
			}

			LOG.debug("OpenRouter response (turn {}): {}", turn, response);

			String choices = LlmJsonUtil.getObject(response, "choices");
			String firstChoice = LlmJsonUtil.getFirstInArray(choices);

			if (firstChoice == null) {
				String errorBlock = LlmJsonUtil.getObject(response, "error");
				if (errorBlock != null) {
					String errMsg = LlmJsonUtil.getString(errorBlock, "message");
					return "Error from OpenRouter: " + errMsg;
				}
				return "Error: unexpected response format from OpenRouter";
			}

			String finishReason = LlmJsonUtil.getString(firstChoice, "finish_reason");
			String message = LlmJsonUtil.getObject(firstChoice, "message");

			if ("stop".equals(finishReason) || finishReason == null) {
				String content = LlmJsonUtil.getString(message, "content");
				if (content != null) {
					messages.add("{\"role\":\"assistant\",\"content\":\""
						+ LlmJsonUtil.escape(content) + "\"}");
				}
				String answer = content != null ? content : "(no content in response)";
				return appendReferences(answer, referencedDocs);
			}

			if ("tool_calls".equals(finishReason)) {
				String toolCallsArray = LlmJsonUtil.getObject(message, "tool_calls");
				String firstCall = LlmJsonUtil.getFirstInArray(toolCallsArray);
				if (firstCall == null) {
					return "Error: tool_calls array is empty";
				}

				String callId = LlmJsonUtil.getString(firstCall, "id");
				String function = LlmJsonUtil.getObject(firstCall, "function");
				String toolName = LlmJsonUtil.getString(function, "name");
				String argsJson = extractArguments(function);

				LOG.info("Tool call: {} args={}", toolName, argsJson);
				messages.add(buildAssistantToolCallMessage(callId, toolName, argsJson));

				String toolResult = executeTool(toolName, argsJson);
				LOG.info("Tool result for {}: {}", toolName, toolResult);

				if (DOC_SEARCH_TOOL.equals(toolName)) {
					collectDocumentReferences(toolResult, referencedDocs);
				}

				messages.add("{\"role\":\"tool\",\"tool_call_id\":\""
					+ LlmJsonUtil.escape(callId) + "\",\"content\":\""
					+ LlmJsonUtil.escape(toolResult) + "\"}");
			} else {
				LOG.warn("Unexpected finish_reason: {}", finishReason);
				break;
			}
		}

		return "Error: maximum turns (" + MAX_TURNS + ") reached without a final answer";
	}

	private String executeTool(String toolName, String argsJson) {
		IMcpTool tool = registry.getTool(toolName);
		if (tool == null) {
			return "Error: unknown tool '" + toolName + "'";
		}
		try {
			Map<String, String> args = LlmJsonUtil.parseFlat(argsJson);
			return tool.execute(args);
		} catch (Exception e) {
			LOG.error("Tool execution failed: {}", toolName, e);
			return "Error executing tool '" + toolName + "': " + e.getMessage();
		}
	}

	/**
	 * Pull source document names out of a document_search result and add them to
	 * {@code out} (deduped, insertion-ordered). The tool emits one citation line per
	 * hit: {@code "[1] report.pdf (chunk 2)  (score 0.812)"}. We strip the
	 * {@code "[n] "} prefix, then strip the trailing {@code "  (score …)"} and the
	 * trailing {@code " (location)"} group, leaving the source filename.
	 *
	 * <p>Coupled to {@code DocumentSearchTool.execute}'s output format. The stripping
	 * is deliberately tolerant (it removes trailing parenthesised groups rather than
	 * matching "chunk"/"score" literals), so wording tweaks there won't break it. If
	 * that citation line is ever redesigned, update this method.</p>
	 */
	private void collectDocumentReferences(String toolResult, Set<String> out) {
		if (toolResult == null || toolResult.isEmpty()) {
			return;
		}
		for (String line : toolResult.split("\n")) {
			Matcher m = CITATION_LINE.matcher(line);
			if (!m.matches()) {
				continue;
			}
			String rest = m.group(1).trim();                 // "report.pdf (chunk 2)  (score 0.812)"
			rest = rest.replaceFirst("\\s*\\([^()]*\\)\\s*$", ""); // drop "  (score 0.812)"
			rest = rest.replaceFirst("\\s*\\([^()]*\\)\\s*$", ""); // drop " (chunk 2)"
			String source = rest.trim();
			if (!source.isEmpty()) {
				out.add(source);
			}
		}
	}

	/**
	 * Append a References footer to the answer. Lists the cited document names, or
	 * "none" when the answer used no documents. Added to the returned text only —
	 * never to the conversation history.
	 */
	private String appendReferences(String answer, Set<String> docs) {
		StringBuilder sb = new StringBuilder(answer == null ? "" : answer);
		sb.append("\n\n---\n**References:** ");
		if (docs.isEmpty()) {
			sb.append("none");
		} else {
			sb.append("\n");
			for (String doc : docs) {
				sb.append("- ").append(doc).append("\n");
			}
		}
		return sb.toString();
	}

	private String buildToolsJson() {
		StringBuilder sb = new StringBuilder("[");
		List<IMcpTool> tools = registry.getTools();
		for (int i = 0; i < tools.size(); i++) {
			if (i > 0) sb.append(",");
			IMcpTool tool = tools.get(i);
			sb.append("{\"type\":\"function\",\"function\":{");
			sb.append("\"name\":\"").append(LlmJsonUtil.escape(tool.getName())).append("\",");
			sb.append("\"description\":\"").append(LlmJsonUtil.escape(tool.getDescription())).append("\",");
			sb.append("\"parameters\":").append(tool.getInputSchema());
			sb.append("}}");
		}
		sb.append("]");
		return sb.toString();
	}

	private String buildRequest(String model, List<String> messages, String toolsJson) {
		StringBuilder sb = new StringBuilder("{");
		sb.append("\"model\":\"").append(LlmJsonUtil.escape(model)).append("\",");
		sb.append("\"messages\":[");
		for (int i = 0; i < messages.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append(messages.get(i));
		}
		sb.append("],");
		sb.append("\"tools\":").append(toolsJson).append(",");
		sb.append("\"tool_choice\":\"auto\"");
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Extract tool call arguments, handling both string and object formats.
	 * Some models return "arguments":"{}" (string), others return "arguments":{} (object).
	 * Some return "arguments":"" (empty string). Normalizes all to valid JSON.
	 */
	private String extractArguments(String functionJson) {
		// Try as string first (OpenAI standard: "arguments":"{...}")
		String args = LlmJsonUtil.getString(functionJson, "arguments");
		if (args != null && !args.trim().isEmpty()) {
			return args;
		}
		// Try as object (some models: "arguments":{...})
		args = LlmJsonUtil.getObject(functionJson, "arguments");
		if (args != null && !args.trim().isEmpty()) {
			return args;
		}
		return "{}";
	}

	private String buildAssistantToolCallMessage(String callId, String toolName, String argsJson) {
		// Ensure argsJson is valid JSON for the arguments field
		if (argsJson == null || argsJson.trim().isEmpty()) {
			argsJson = "{}";
		}
		return "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{"
			+ "\"id\":\"" + LlmJsonUtil.escape(callId) + "\","
			+ "\"type\":\"function\","
			+ "\"function\":{\"name\":\"" + LlmJsonUtil.escape(toolName) + "\","
			+ "\"arguments\":\"" + LlmJsonUtil.escape(argsJson) + "\"}"
			+ "}]}";
	}

	/**
	 * Resolve the API key from system property or environment variable.
	 * Priority: -Dopenrouter.api.key > OPENROUTER_API_KEY env var.
	 */
	private String resolveApiKey() {
		if (apiKeyOverride != null && !apiKeyOverride.isEmpty()) {
			return apiKeyOverride;
		}
		String key = System.getProperty("openrouter.api.key", "");
		if (key.isEmpty()) {
			String envKey = System.getenv("OPENROUTER_API_KEY");
			if (envKey != null && !envKey.isEmpty()) {
				key = envKey;
			}
		}
		return key;
	}

	/**
	 * Resolve the API base URL. Priority: programmatic override (set by ChatService)
	 * &rarr; {@code -Dopenrouter.base.url} system property &rarr; built-in default.
	 * Mirrors the embeddings client and ChatConfig so the REST endpoint honours the
	 * same base URL as the Swing chatbot. A trailing slash is tolerated.
	 */
	private String resolveBaseUrl() {
		String base = (baseUrlOverride != null && !baseUrlOverride.isEmpty())
			? baseUrlOverride
			: System.getProperty("openrouter.base.url", DEFAULT_BASE_URL);
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	private String post(String apiKey, String jsonBody) {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(resolveBaseUrl() + "/chat/completions");
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "Bearer " + apiKey);
			conn.setRequestProperty("HTTP-Referer", "http://localhost:8080");
			conn.setRequestProperty("X-Title", "OSGi MCP Bridge");
			conn.setDoOutput(true);
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(60000);

			OutputStream os = conn.getOutputStream();
			os.write(jsonBody.getBytes("UTF-8"));
			os.flush();
			os.close();

			int status = conn.getResponseCode();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
				status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream(),
				"UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) sb.append(line);
			reader.close();
			return sb.toString();

		} catch (IOException e) {
			LOG.error("HTTP POST to OpenRouter failed: {}", e.getMessage());
			return null;
		} finally {
			if (conn != null) conn.disconnect();
		}
	}
}
