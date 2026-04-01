# Swing Chatbot Bundle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Swing-based chatbot bundle (`com.kk.pde.ds.chatbot`) that provides a desktop chat UI for talking to OpenAI-compatible LLMs (like OpenRouter), with session history, model selection, and MCP tool integration.

**Architecture:** The chatbot is a new OSGi bundle with a Swing JFrame that uses the existing `OpenRouterAgent` for LLM communication. It adds a new `IChatService` interface to the API bundle so other bundles can programmatically send chat messages. The chatbot modifies `OpenRouterAgent` to support conversation history (multi-turn) and adds a `/v1/models` endpoint query for model selection. The UI launches alongside the existing clock from the `App` component.

**Tech Stack:** Java 8 (Swing), OSGi DS annotations, existing `LlmJsonUtil` for JSON, `HttpURLConnection` for HTTP, OpenAI-compatible `/v1/models` and `/v1/chat/completions` endpoints.

---

## File Structure

### New Bundle: `com.kk.pde.ds.chatbot`

| File | Responsibility |
|------|---------------|
| `META-INF/MANIFEST.MF` | Bundle metadata, imports for Swing/OSGi/LLM |
| `build.properties` | Tycho build configuration |
| `pom.xml` | Maven module (eclipse-plugin packaging) |
| `src/.../chatbot/ChatService.java` | DS component: holds OpenRouterAgent ref, manages conversation history, fetches models |
| `src/.../chatbot/ChatFrame.java` | JFrame: top-level window with model selector + chat panel |
| `src/.../chatbot/ChatPanel.java` | JPanel: message display area (HTML-rendered messages) |
| `src/.../chatbot/InputPanel.java` | JPanel: text input + send/clear buttons |
| `src/.../chatbot/ModelFetcher.java` | Static utility: queries `/v1/models` endpoint, parses response |

### Modified Files

| File | Change |
|------|--------|
| `com.kk.pde.ds.mcp.llm/.../OpenRouterAgent.java` | Add `chatWithHistory(List<String> messages, String model)` method + expose `buildToolsJson()` |
| `com.kk.pde.ds.mcp.llm/.../LlmJsonUtil.java` | Add `getAllInArray(String arrayJson)` for parsing model list |
| `com.kk.pde.ds.mcp.llm/META-INF/MANIFEST.MF` | Export `com.kk.pde.ds.mcp.llm` package |
| `pom.xml` (root) | Add `com.kk.pde.ds.chatbot` module |
| `com.kk.pde.ds.feature/feature.xml` | Add chatbot plugin entry |
| `distribution/p2.product` | Add chatbot plugin + start level config |
| `com.kk.pde.ds.app/.../App.java` | Launch ChatFrame alongside ClockFrame |
| `com.kk.pde.ds.app/META-INF/MANIFEST.MF` | Add import for `com.kk.pde.ds.chatbot` package |

---

## Task 1: Export LLM Package and Add History Support to OpenRouterAgent

**Files:**
- Modify: `com.kk.pde.ds.mcp.llm/META-INF/MANIFEST.MF`
- Modify: `com.kk.pde.ds.mcp.llm/src/com/kk/pde/ds/mcp/llm/OpenRouterAgent.java`
- Modify: `com.kk.pde.ds.mcp.llm/src/com/kk/pde/ds/mcp/llm/LlmJsonUtil.java`

The chatbot bundle needs to import `OpenRouterAgent` and `LlmJsonUtil` from the LLM bundle. Currently the LLM bundle doesn't export its package. We also need `OpenRouterAgent` to support multi-turn conversations (the chatbot maintains history externally and passes it in).

- [ ] **Step 1: Export the LLM package in MANIFEST.MF**

Edit `com.kk.pde.ds.mcp.llm/META-INF/MANIFEST.MF` to add an `Export-Package` header:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: com.kk.pde.ds.mcp.llm
Bundle-SymbolicName: com.kk.pde.ds.mcp.llm
Bundle-Version: 1.0.0.qualifier
Export-Package: com.kk.pde.ds.mcp.llm
Import-Package: com.kk.pde.ds.mcp.api,
 javax.servlet;version="3.1.0",
 javax.servlet.http;version="3.1.0",
 org.osgi.service.component.annotations;version="1.5.1";resolution:=optional,
 org.osgi.service.http.whiteboard;version="1.1.0",
 org.slf4j;version="1.7.32"
Service-Component: OSGI-INF/*.xml
Bundle-Vendor: KK
Automatic-Module-Name: com.kk.pde.ds.mcp.llm
Bundle-ActivationPolicy: lazy
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
```

- [ ] **Step 2: Add `chatWithHistory` method to OpenRouterAgent**

Add a new public method to `OpenRouterAgent.java` that accepts externally-managed conversation history. Insert after the existing `chat()` method (after line 139):

```java
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
	String apiKey = System.getProperty("openrouter.api.key", "");
	if (apiKey.isEmpty()) {
		return "Error: openrouter.api.key system property not set. "
			+ "Start the app with -Dopenrouter.api.key=your_key";
	}

	if (model == null || model.isEmpty()) {
		model = System.getProperty("openrouter.model", DEFAULT_MODEL);
	}

	String toolsJson = buildToolsJson();
	LOG.info("Chat with history: model={}, messages={}, tools={}",
		model, messages.size(), registry.getTools().size());

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
			return content != null ? content : "(no content in response)";
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
			String argsJson = LlmJsonUtil.getString(function, "arguments");

			LOG.info("Tool call: {} args={}", toolName, argsJson);
			messages.add(buildAssistantToolCallMessage(callId, toolName, argsJson));

			String toolResult = executeTool(toolName, argsJson);
			LOG.info("Tool result for {}: {}", toolName, toolResult);

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
```

Also change `LlmJsonUtil` visibility from package-private to public (line 12):

```java
public final class LlmJsonUtil {
```

And change all `static` methods in `LlmJsonUtil` to `public static`:
- `getString` (line 20)
- `getObject` (line 49)
- `getFirstInArray` (line 81)
- `parseFlat` (line 104)
- `escape` (line 144)

- [ ] **Step 3: Add `getAllInArray` method to LlmJsonUtil**

Insert after the `getFirstInArray` method (after line 98) in `LlmJsonUtil.java`:

```java
/**
 * Returns all elements of a JSON array as a list of raw strings.
 * e.g. "[{...}, {...}]" returns a list with each object's raw text.
 */
public static java.util.List<String> getAllInArray(String arrayJson) {
	java.util.List<String> results = new java.util.ArrayList<String>();
	if (arrayJson == null) return results;
	String trimmed = arrayJson.trim();
	if (!trimmed.startsWith("[")) return results;

	int i = 1;
	while (i < trimmed.length()) {
		while (i < trimmed.length() && (Character.isWhitespace(trimmed.charAt(i))
			|| trimmed.charAt(i) == ',')) i++;
		if (i >= trimmed.length() || trimmed.charAt(i) == ']') break;

		char c = trimmed.charAt(i);
		String element = null;
		if (c == '{') {
			element = extractBracketed(trimmed, i, '{', '}');
		} else if (c == '[') {
			element = extractBracketed(trimmed, i, '[', ']');
		} else if (c == '"') {
			element = extractQuotedString(trimmed, i);
			if (element != null) {
				results.add(element);
				i += element.length() + 2;
				continue;
			}
		} else {
			int end = i;
			while (end < trimmed.length() && trimmed.charAt(end) != ','
				&& trimmed.charAt(end) != ']') end++;
			element = trimmed.substring(i, end).trim();
			results.add(element);
			i = end;
			continue;
		}
		if (element != null) {
			results.add(element);
			i += element.length();
		} else {
			break;
		}
	}
	return results;
}
```

- [ ] **Step 4: Build to verify changes compile**

Run:
```bash
mvn clean package -pl com.kk.pde.ds.mcp.llm -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add com.kk.pde.ds.mcp.llm/META-INF/MANIFEST.MF \
       com.kk.pde.ds.mcp.llm/src/com/kk/pde/ds/mcp/llm/OpenRouterAgent.java \
       com.kk.pde.ds.mcp.llm/src/com/kk/pde/ds/mcp/llm/LlmJsonUtil.java
git commit -m "feat(llm): export package, add multi-turn chat and array parsing"
```

---

## Task 2: Create Chatbot Bundle Skeleton

**Files:**
- Create: `com.kk.pde.ds.chatbot/pom.xml`
- Create: `com.kk.pde.ds.chatbot/META-INF/MANIFEST.MF`
- Create: `com.kk.pde.ds.chatbot/build.properties`
- Create: `com.kk.pde.ds.chatbot/OSGI-INF/` (empty, SCR will populate)

- [ ] **Step 1: Create pom.xml**

Create `com.kk.pde.ds.chatbot/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<parent>
		<groupId>com.kk.pde.ds</groupId>
		<artifactId>parent</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</parent>

	<artifactId>com.kk.pde.ds.chatbot</artifactId>
	<packaging>eclipse-plugin</packaging>
</project>
```

- [ ] **Step 2: Create MANIFEST.MF**

Create `com.kk.pde.ds.chatbot/META-INF/MANIFEST.MF`:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: com.kk.pde.ds.chatbot
Bundle-SymbolicName: com.kk.pde.ds.chatbot
Bundle-Version: 1.0.0.qualifier
Export-Package: com.kk.pde.ds.chatbot
Import-Package: com.kk.pde.ds.mcp.api,
 com.kk.pde.ds.mcp.llm,
 java.awt,
 java.awt.event,
 java.awt.geom,
 javax.swing,
 javax.swing.border,
 javax.swing.text,
 javax.swing.text.html,
 org.osgi.service.component.annotations;version="1.5.1";resolution:=optional,
 org.slf4j;version="1.7.32"
Service-Component: OSGI-INF/*.xml
Bundle-Vendor: KK
Automatic-Module-Name: com.kk.pde.ds.chatbot
Bundle-ActivationPolicy: lazy
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
```

- [ ] **Step 3: Create build.properties**

Create `com.kk.pde.ds.chatbot/build.properties`:

```
source.. = src/
output.. = bin/
bin.includes = META-INF/,\
               .,\
               OSGI-INF/
```

- [ ] **Step 4: Create OSGI-INF directory**

```bash
mkdir -p com.kk.pde.ds.chatbot/OSGI-INF
mkdir -p com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot
```

- [ ] **Step 5: Add module to parent pom.xml**

In `pom.xml` (root), add the chatbot module after `com.kk.pde.ds.mcp.llm` and before `com.kk.pde.ds.feature`:

```xml
<module>com.kk.pde.ds.mcp.llm</module>
<module>com.kk.pde.ds.chatbot</module>
<module>com.kk.pde.ds.feature</module>
```

- [ ] **Step 6: Commit**

```bash
git add com.kk.pde.ds.chatbot/ pom.xml
git commit -m "feat(chatbot): add bundle skeleton with pom, manifest, build.properties"
```

---

## Task 3: Implement ModelFetcher

**Files:**
- Create: `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ModelFetcher.java`

This utility queries the OpenAI-compatible `/v1/models` endpoint and returns a sorted list of model IDs. It reuses the same system properties (`openrouter.api.key`) and HTTP pattern as `OpenRouterAgent`.

- [ ] **Step 1: Create ModelFetcher.java**

Create `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ModelFetcher.java`:

```java
package com.kk.pde.ds.chatbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.llm.LlmJsonUtil;

/**
 * Queries OpenAI-compatible /v1/models endpoint to list available models.
 * Works with OpenRouter, LM Studio, and any OpenAI-compatible API.
 */
public final class ModelFetcher {

	private static final Logger LOG = LoggerFactory.getLogger(ModelFetcher.class);

	private ModelFetcher() {
	}

	/**
	 * Fetch available model IDs from the given base URL.
	 *
	 * @param baseUrl  API base URL (e.g. "https://openrouter.ai/api/v1")
	 * @param apiKey   bearer token (may be empty for local APIs like LM Studio)
	 * @return sorted list of model ID strings, empty list on error
	 */
	public static List<String> fetchModels(String baseUrl, String apiKey) {
		String modelsUrl = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";

		HttpURLConnection conn = null;
		try {
			URL url = new URL(modelsUrl);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			if (apiKey != null && !apiKey.isEmpty()) {
				conn.setRequestProperty("Authorization", "Bearer " + apiKey);
			}
			conn.setConnectTimeout(10000);
			conn.setReadTimeout(30000);

			int status = conn.getResponseCode();
			if (status < 200 || status >= 300) {
				LOG.warn("Models endpoint returned status {}", status);
				return Collections.emptyList();
			}

			BufferedReader reader = new BufferedReader(
				new InputStreamReader(conn.getInputStream(), "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) sb.append(line);
			reader.close();

			String json = sb.toString();
			String dataArray = LlmJsonUtil.getObject(json, "data");
			if (dataArray == null) {
				LOG.warn("No 'data' array in models response");
				return Collections.emptyList();
			}

			List<String> allElements = LlmJsonUtil.getAllInArray(dataArray);
			List<String> modelIds = new ArrayList<String>();
			for (String element : allElements) {
				String id = LlmJsonUtil.getString(element, "id");
				if (id != null && !id.isEmpty()) {
					modelIds.add(id);
				}
			}

			Collections.sort(modelIds);
			LOG.info("Fetched {} models from {}", modelIds.size(), modelsUrl);
			return modelIds;

		} catch (IOException e) {
			LOG.error("Failed to fetch models from {}: {}", modelsUrl, e.getMessage());
			return Collections.emptyList();
		} finally {
			if (conn != null) conn.disconnect();
		}
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ModelFetcher.java
git commit -m "feat(chatbot): add ModelFetcher for /v1/models endpoint"
```

---

## Task 4: Implement ChatService (DS Component)

**Files:**
- Create: `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatService.java`

This is the core DS component. It holds the `OpenRouterAgent` reference, manages conversation history, and provides the send/clear API that the UI calls.

- [ ] **Step 1: Create ChatService.java**

Create `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatService.java`:

```java
package com.kk.pde.ds.chatbot;

import java.util.ArrayList;
import java.util.List;

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
	private final List<String> history = new ArrayList<String>();
	private String currentModel;
	private String baseUrl;

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

	/** Get the API key from system properties. */
	public String getApiKey() {
		return System.getProperty("openrouter.api.key", "");
	}

	/**
	 * Fetch available models from the configured API endpoint.
	 * Runs synchronously — caller should invoke from a background thread.
	 */
	public List<String> fetchModels() {
		return ModelFetcher.fetchModels(getBaseUrl(), getApiKey());
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatService.java
git commit -m "feat(chatbot): add ChatService DS component with history management"
```

---

## Task 5: Implement ChatPanel (Message Display)

**Files:**
- Create: `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatPanel.java`

This panel renders the chat conversation using a JTextPane with styled text. User messages appear right-aligned in blue, assistant messages left-aligned in dark gray. Tool calls are shown as dimmed status lines.

- [ ] **Step 1: Create ChatPanel.java**

Create `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatPanel.java`:

```java
package com.kk.pde.ds.chatbot;

import java.awt.Color;
import java.awt.Font;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

/**
 * Panel that displays chat messages with styled formatting.
 * User messages are blue, assistant messages are dark, system/tool messages are gray.
 */
public class ChatPanel extends JScrollPane {

	private static final long serialVersionUID = 1L;
	private final JTextPane textPane;
	private final StyledDocument doc;
	private final Style userStyle;
	private final Style assistantStyle;
	private final Style systemStyle;
	private final Style labelStyle;

	public ChatPanel() {
		textPane = new JTextPane();
		textPane.setEditable(false);
		textPane.setBackground(new Color(30, 30, 40));
		textPane.setFont(new Font("SansSerif", Font.PLAIN, 14));

		doc = textPane.getStyledDocument();

		// User message style (light blue)
		userStyle = doc.addStyle("user", null);
		StyleConstants.setForeground(userStyle, new Color(130, 200, 255));
		StyleConstants.setFontSize(userStyle, 14);

		// Assistant message style (white/light gray)
		assistantStyle = doc.addStyle("assistant", null);
		StyleConstants.setForeground(assistantStyle, new Color(220, 220, 230));
		StyleConstants.setFontSize(assistantStyle, 14);

		// System/tool message style (dimmed)
		systemStyle = doc.addStyle("system", null);
		StyleConstants.setForeground(systemStyle, new Color(120, 120, 140));
		StyleConstants.setFontSize(systemStyle, 12);
		StyleConstants.setItalic(systemStyle, true);

		// Label style (role indicators)
		labelStyle = doc.addStyle("label", null);
		StyleConstants.setFontSize(labelStyle, 11);
		StyleConstants.setBold(labelStyle, true);

		setViewportView(textPane);
		setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
	}

	/** Append a user message to the display. */
	public void addUserMessage(String message) {
		Style label = doc.addStyle("userLabel", labelStyle);
		StyleConstants.setForeground(label, new Color(80, 160, 220));
		appendText("\nYou:\n", label);
		appendText(message + "\n", userStyle);
		scrollToBottom();
	}

	/** Append an assistant message to the display. */
	public void addAssistantMessage(String message) {
		Style label = doc.addStyle("assistantLabel", labelStyle);
		StyleConstants.setForeground(label, new Color(100, 200, 120));
		appendText("\nAssistant:\n", label);
		appendText(message + "\n", assistantStyle);
		scrollToBottom();
	}

	/** Append a system/status message (tool calls, errors, etc.). */
	public void addSystemMessage(String message) {
		appendText("\n" + message + "\n", systemStyle);
		scrollToBottom();
	}

	/** Clear all displayed messages. */
	public void clear() {
		try {
			doc.remove(0, doc.getLength());
		} catch (BadLocationException e) {
			// ignore
		}
	}

	private void appendText(String text, Style style) {
		try {
			doc.insertString(doc.getLength(), text, style);
		} catch (BadLocationException e) {
			// ignore
		}
	}

	private void scrollToBottom() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				textPane.setCaretPosition(doc.getLength());
			}
		});
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatPanel.java
git commit -m "feat(chatbot): add ChatPanel with styled message display"
```

---

## Task 6: Implement InputPanel

**Files:**
- Create: `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/InputPanel.java`

The input panel has a multi-line text area (3 rows), a Send button, and a Clear button. Enter sends the message (Shift+Enter for newline).

- [ ] **Step 1: Create InputPanel.java**

Create `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/InputPanel.java`:

```java
package com.kk.pde.ds.chatbot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Input panel with multi-line text area, Send button, and Clear History button.
 * Enter sends the message; Shift+Enter inserts a newline.
 */
public class InputPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Callback interface for when the user submits a message. */
	public interface SendListener {
		void onSend(String message);
	}

	/** Callback interface for when the user clears history. */
	public interface ClearListener {
		void onClear();
	}

	private final JTextArea textArea;
	private final JButton sendButton;
	private final JButton clearButton;

	public InputPanel(final SendListener sendListener, final ClearListener clearListener) {
		setLayout(new BorderLayout(5, 5));

		textArea = new JTextArea(3, 40);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);

		// Enter sends, Shift+Enter adds newline
		textArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
					e.consume();
					doSend(sendListener);
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scrollPane, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

		clearButton = new JButton("Clear");
		clearButton.setToolTipText("Clear conversation history");
		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (clearListener != null) {
					clearListener.onClear();
				}
			}
		});
		buttonPanel.add(clearButton);

		sendButton = new JButton("Send");
		sendButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				doSend(sendListener);
			}
		});
		buttonPanel.add(sendButton);

		add(buttonPanel, BorderLayout.EAST);
	}

	private void doSend(SendListener listener) {
		String text = textArea.getText().trim();
		if (!text.isEmpty() && listener != null) {
			textArea.setText("");
			listener.onSend(text);
		}
	}

	/** Enable or disable the send button (e.g. while waiting for response). */
	public void setSendEnabled(boolean enabled) {
		sendButton.setEnabled(enabled);
		textArea.setEnabled(enabled);
	}

	/** Request focus on the text area. */
	public void focusInput() {
		textArea.requestFocusInWindow();
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/InputPanel.java
git commit -m "feat(chatbot): add InputPanel with text area and send/clear buttons"
```

---

## Task 7: Implement ChatFrame (Main Window)

**Files:**
- Create: `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatFrame.java`

The top-level JFrame that combines the model selector combo box, chat panel, and input panel. It handles the send/clear flow on a background thread to keep the UI responsive.

- [ ] **Step 1: Create ChatFrame.java**

Create `com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatFrame.java`:

```java
package com.kk.pde.ds.chatbot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main chat window. Contains a model selector bar at the top,
 * a scrollable chat display in the center, and an input panel at the bottom.
 */
public class ChatFrame extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(ChatFrame.class);

	private final ChatService chatService;
	private final ChatPanel chatPanel;
	private final InputPanel inputPanel;
	private final JComboBox<String> modelCombo;
	private final DefaultComboBoxModel<String> modelComboModel;
	private final JButton refreshModelsButton;

	public ChatFrame(ChatService chatService) {
		this.chatService = chatService;

		setTitle("OSGi Chatbot");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setPreferredSize(new Dimension(700, 500));

		JPanel contentPane = new JPanel(new BorderLayout(5, 5));
		contentPane.setBorder(new EmptyBorder(8, 8, 8, 8));
		setContentPane(contentPane);

		// --- Top bar: model selector ---
		JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		topBar.add(new JLabel("Model:"));

		modelComboModel = new DefaultComboBoxModel<String>();
		String defaultModel = System.getProperty("openrouter.model", "google/gemini-flash-1.5");
		modelComboModel.addElement(defaultModel);
		modelCombo = new JComboBox<String>(modelComboModel);
		modelCombo.setEditable(true);
		modelCombo.setPreferredSize(new Dimension(350, 26));
		modelCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String selected = (String) modelCombo.getSelectedItem();
				if (selected != null && !selected.isEmpty()) {
					chatService.setModel(selected);
				}
			}
		});
		topBar.add(modelCombo);

		refreshModelsButton = new JButton("Refresh Models");
		refreshModelsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				fetchModelsAsync();
			}
		});
		topBar.add(refreshModelsButton);

		contentPane.add(topBar, BorderLayout.NORTH);

		// --- Center: chat display ---
		chatPanel = new ChatPanel();
		contentPane.add(chatPanel, BorderLayout.CENTER);

		// --- Bottom: input panel ---
		inputPanel = new InputPanel(
			new InputPanel.SendListener() {
				@Override
				public void onSend(String message) {
					sendMessageAsync(message);
				}
			},
			new InputPanel.ClearListener() {
				@Override
				public void onClear() {
					chatService.clearHistory();
					chatPanel.clear();
					chatPanel.addSystemMessage("Conversation cleared.");
				}
			}
		);
		contentPane.add(inputPanel, BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(null);
		inputPanel.focusInput();
	}

	private void sendMessageAsync(final String message) {
		chatPanel.addUserMessage(message);
		inputPanel.setSendEnabled(false);
		chatPanel.addSystemMessage("Thinking...");

		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() {
				return chatService.send(message);
			}

			@Override
			protected void done() {
				try {
					String response = get();
					chatPanel.addAssistantMessage(response);
				} catch (Exception e) {
					LOG.error("Chat error", e);
					chatPanel.addSystemMessage("Error: " + e.getMessage());
				} finally {
					inputPanel.setSendEnabled(true);
					inputPanel.focusInput();
				}
			}
		}.execute();
	}

	private void fetchModelsAsync() {
		refreshModelsButton.setEnabled(false);
		refreshModelsButton.setText("Loading...");
		chatPanel.addSystemMessage("Fetching models from " + chatService.getBaseUrl() + "...");

		new SwingWorker<List<String>, Void>() {
			@Override
			protected List<String> doInBackground() {
				return chatService.fetchModels();
			}

			@Override
			protected void done() {
				try {
					List<String> models = get();
					if (models.isEmpty()) {
						chatPanel.addSystemMessage("No models returned. Check API key and base URL.");
					} else {
						String currentSelection = (String) modelCombo.getSelectedItem();
						modelComboModel.removeAllElements();
						for (String model : models) {
							modelComboModel.addElement(model);
						}
						if (currentSelection != null) {
							modelCombo.setSelectedItem(currentSelection);
						}
						chatPanel.addSystemMessage("Loaded " + models.size() + " models.");
					}
				} catch (Exception e) {
					LOG.error("Failed to fetch models", e);
					chatPanel.addSystemMessage("Error fetching models: " + e.getMessage());
				} finally {
					refreshModelsButton.setEnabled(true);
					refreshModelsButton.setText("Refresh Models");
				}
			}
		}.execute();
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add com.kk.pde.ds.chatbot/src/com/kk/pde/ds/chatbot/ChatFrame.java
git commit -m "feat(chatbot): add ChatFrame with model selector, chat display, and input"
```

---

## Task 8: Wire Chatbot into App and Product

**Files:**
- Modify: `com.kk.pde.ds.app/src/com/kk/pde/ds/app/App.java`
- Modify: `com.kk.pde.ds.app/META-INF/MANIFEST.MF`
- Modify: `com.kk.pde.ds.feature/feature.xml`
- Modify: `distribution/p2.product`

- [ ] **Step 1: Add ChatService reference to App component**

Edit `com.kk.pde.ds.app/META-INF/MANIFEST.MF` to add the chatbot import:

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: com.kk.pde.ds.app
Bundle-SymbolicName: com.kk.pde.ds.app
Bundle-Version: 1.0.0.qualifier
Import-Package: com.kk.pde.ds.api,
 com.kk.pde.ds.chatbot,
 java.awt,
 java.awt.event,
 javax.swing,
 org.osgi.service.component.annotations;version="1.5.1";resolution:=optional,
 org.slf4j;version="1.7.32"
Service-Component: OSGI-INF/com.kk.pde.ds.app.App.xml
Bundle-Vendor: KK
Automatic-Module-Name: com.kk.pde.ds.app
Bundle-ActivationPolicy: lazy
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
```

Edit `com.kk.pde.ds.app/src/com/kk/pde/ds/app/App.java` to inject `ChatService` and launch `ChatFrame`:

Add import at the top:
```java
import com.kk.pde.ds.chatbot.ChatFrame;
import com.kk.pde.ds.chatbot.ChatService;
```

Add field and setter after the existing `greet` field (after line 33):
```java
private ChatService chatService;

@Reference
public void setChatService(ChatService chatService) {
	log.info("App.setChatService()");
	this.chatService = chatService;
}
```

In the `start()` method, add chatbot launch after the clock launch (inside the `invokeLater` block, after line 56):
```java
ChatFrame chatFrame = new ChatFrame(chatService);
chatFrame.setVisible(true);
```

- [ ] **Step 2: Add chatbot to feature.xml**

Add after the `com.kk.pde.ds.mcp.llm` plugin entry in `com.kk.pde.ds.feature/feature.xml`:

```xml
<plugin
	id="com.kk.pde.ds.chatbot"
	download-size="0"
	install-size="0"
	version="0.0.0"
	unpack="false"/>
```

- [ ] **Step 3: Add chatbot to p2.product**

In `distribution/p2.product`, add in the `<plugins>` section (after `com.kk.pde.ds.mcp.llm`):
```xml
<plugin id="com.kk.pde.ds.chatbot"/>
```

Add in the `<configurations>` section (after the `com.kk.pde.ds.mcp.llm` config):
```xml
<plugin id="com.kk.pde.ds.chatbot" autoStart="true" startLevel="4" />
```

- [ ] **Step 4: Commit**

```bash
git add com.kk.pde.ds.app/src/com/kk/pde/ds/app/App.java \
       com.kk.pde.ds.app/META-INF/MANIFEST.MF \
       com.kk.pde.ds.feature/feature.xml \
       distribution/p2.product
git commit -m "feat(chatbot): wire chatbot into App component and product build"
```

---

## Task 9: Full Build and Verification

- [ ] **Step 1: Run full build**

```bash
mvn clean verify
```

Expected: BUILD SUCCESS for all modules including `com.kk.pde.ds.chatbot`.

- [ ] **Step 2: Run the product**

```bash
./distribution/scripts/run.sh
```

Expected:
- Clock window appears (existing behavior)
- Chatbot window appears alongside it
- Console logs show `ChatService: OpenRouterAgent injected`
- Typing a message and hitting Send contacts the LLM (requires `-Dopenrouter.api.key=...`)
- "Refresh Models" button fetches and populates the model dropdown
- "Clear" button resets conversation

- [ ] **Step 3: Verify model selection works**

1. Click "Refresh Models" — dropdown populates with available models
2. Select a different model from dropdown
3. Send a message — response should come from the selected model
4. Type a custom model ID in the editable combo box — should work too

- [ ] **Step 4: Verify conversation history**

1. Send "My name is Kerem"
2. Send "What is my name?" — assistant should recall "Kerem"
3. Click "Clear"
4. Send "What is my name?" — assistant should NOT know the name

- [ ] **Step 5: Commit any fixes needed**

If build or runtime issues are found, fix and commit with descriptive message.

---

## Dependency Graph

```
com.kk.pde.ds.chatbot
    imports ──> com.kk.pde.ds.mcp.llm (OpenRouterAgent, LlmJsonUtil)
    imports ──> com.kk.pde.ds.mcp.api  (IMcpToolRegistry — transitively via agent)

com.kk.pde.ds.app
    imports ──> com.kk.pde.ds.chatbot  (ChatService, ChatFrame)
    imports ──> com.kk.pde.ds.api      (IGreet — existing)
```

The chatbot bundle sits between the LLM bridge and the App — it consumes the LLM agent and provides the UI that App launches.
