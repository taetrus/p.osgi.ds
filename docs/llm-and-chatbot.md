# LLM Integration and Chatbot UI

This document describes the LLM bridge (`com.kk.pde.ds.mcp.llm`) and the Swing chatbot
(`com.kk.pde.ds.chatbot`). It gives the agent loop, the `/llm/chat` endpoint, the
configuration, and the user interface.

- [Overview](#overview)
- [Service wiring](#service-wiring)
- [Run the chatbot](#run-the-chatbot)
- [Configuration](#configuration)
- [The default model](#the-default-model)
- [Agent loop](#agent-loop)
- [References footer](#references-footer)
- [HTTP endpoint /llm/chat](#http-endpoint-llmchat)
- [Chatbot window](#chatbot-window)
- [Settings dialog](#settings-dialog)
- [Markdown in answers](#markdown-in-answers)
- [Conversation history](#conversation-history)
- [Logging and privacy](#logging-and-privacy)
- [Troubleshooting](#troubleshooting)
- [Related documents](#related-documents)

## Overview

The LLM bridge connects the MCP tool registry to an OpenAI-compatible chat API, for
example OpenRouter or LM Studio. An agent loop lets the model call the registered MCP
tools during a conversation. The tools run in the same JVM. The bridge does not call
the `/mcp` servlet.

| Bundle | Class | Role |
|--------|-------|------|
| `com.kk.pde.ds.mcp.llm` | `OpenRouterAgent` | DS service. It runs the agent loop and calls the tools through `IMcpToolRegistry`. |
| `com.kk.pde.ds.mcp.llm` | `LlmChatServlet` | HTTP Whiteboard servlet at `/llm/chat`. It gives the agent to HTTP clients. |
| `com.kk.pde.ds.chatbot` | `ChatService` | DS service. It keeps the conversation history and the settings. |
| `com.kk.pde.ds.chatbot` | `ChatConfig` | Reads and writes `settings/chatbot.properties`. |
| `com.kk.pde.ds.chatbot` | `ModelFetcher` | Gets the list of models that support tools. |
| `com.kk.pde.ds.chatbot` | `ChatFrame`, `ChatPanel`, `InputPanel`, `SettingsDialog` | The Swing window and its parts. |
| `com.kk.pde.ds.chatbot` | `MarkdownStyler`, `DarkTheme` | Markdown text and colors. |

For the tools, refer to [mcp-server.md](mcp-server.md).

## Service wiring

```
IMcpToolRegistry
        │  @Reference
OpenRouterAgent ─────────────┐
        │  @Reference        │  @Reference
ChatService              LlmChatServlet (/llm/chat)
        │  @Reference
App (com.kk.pde.ds.app) → creates ChatFrame on the EDT
```

1. `OpenRouterAgent` gets `IMcpToolRegistry` through `@Reference`.
2. `ChatService` gets `OpenRouterAgent` through `@Reference`.
3. `App` gets `ChatService` through `@Reference`.
4. `App.start()` applies `DarkTheme`, then opens the clock window and `ChatFrame` on the
   event dispatch thread (EDT).
5. `App.stop()` closes the two windows when the component stops.

`ChatService` and `LlmChatServlet` use the same `OpenRouterAgent` instance. The chatbot
gives its key and base URL as arguments to each call. Thus a change in the Settings
dialog does not change the requests of `/llm/chat`.

## Run the chatbot

1. Get an API key from OpenRouter.
2. Set the key and start the product.

   macOS and Linux:

   ```bash
   export OPENROUTER_API_KEY=your_key_here
   ./distribution/scripts/run.sh
   ```

   Windows `cmd.exe`:

   ```cmd
   set OPENROUTER_API_KEY=your_key_here
   distribution\scripts\run.bat
   ```

   Windows PowerShell:

   ```powershell
   $env:OPENROUTER_API_KEY = "your_key_here"
   distribution\scripts\run.bat
   ```

3. Wait for the window "OSGi Chatbot". It shows `Connected! Loaded N tool-capable models.`
4. Select a model in the list, or keep the default model.
5. Ask a question that needs a tool, for example *"what bundles are running?"*.
6. Make sure that the answer contains the bundle list. The model calls `bundle_list`.

You can also give the key as a system property:
`./distribution/scripts/run.sh -Dopenrouter.api.key=your_key`. The scripts put all
`-D` flags before `-jar`, so the JVM receives them.

To use a local LM Studio server, add `-Dopenrouter.base.url=http://localhost:1234/v1`.
Port 1234 is the LM Studio default. Start the LM Studio server first, for example with
`lms server start`. Checked against LM Studio 0.4.21 and
https://lmstudio.ai/docs/developer/openai-compat.

`OpenRouterAgent` stops with `Error: No API key found` when the key is empty. Thus give
a key also for LM Studio. If LM Studio authentication is off (the default), any value
is correct, for example `-Dopenrouter.api.key=lm-studio`.

## Configuration

### Keys and defaults

| File key (`settings/chatbot.properties`) | System property | Environment variable | Default |
|------------------------------------------|-----------------|----------------------|---------|
| `api.key` | `openrouter.api.key` | `OPENROUTER_API_KEY` | none. You must supply a key. |
| `model` | `openrouter.model` | none | `minimax/minimax-m3.1-flash:free` |
| `base.url` | `openrouter.base.url` | none | `https://openrouter.ai/api/v1` |

The agent sends chat requests to `{base.url}/chat/completions`. `ModelFetcher` sends
`GET {base.url}/models`. The code accepts a trailing `/` on the base URL.

The RAG embeddings client also reads `openrouter.api.key` and `openrouter.base.url`. It
does not read `chatbot.properties`. Refer to [rag-reference.md](rag-reference.md).

### Precedence in the chatbot

For each setting, the chatbot uses the first non-empty value in this sequence:

1. The value in `settings/chatbot.properties`
2. The system property (`-D`)
3. The environment variable (API key only)
4. The default in the code

### Precedence in /llm/chat

`/llm/chat` does not read `chatbot.properties`.

| Setting | Sequence |
|---------|----------|
| API key | `-Dopenrouter.api.key`, then `OPENROUTER_API_KEY` |
| Model | the `model` field of the request, then `-Dopenrouter.model`, then the default |
| Base URL | `-Dopenrouter.base.url`, then the default |

### The settings file

`ChatConfig` reads `settings/chatbot.properties` relative to the working directory. The
run scripts change the working directory to the product directory before they start
Java:

| Platform | File location |
|----------|---------------|
| macOS | `<product>/macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse/settings/chatbot.properties` |
| Linux | `<product>/linux/gtk/x86_64/settings/chatbot.properties` |
| Windows | `<product>\win32\win32\x86_64\settings\chatbot.properties` |

`<product>` is `distribution/target/products/com.kk.pde.ds.product`.

If the file does not exist, `ChatConfig` makes it with three empty keys. An empty value
does not hide the `-D` property or the default. Only a value that you write has
precedence.

**Caution:** A value in the file has precedence over `-D` on each later start. To use
`-D` again, delete the value from the file.

## The default model

The default model is `minimax/minimax-m3.1-flash:free`. Commit `276823f` set this
default. The old default `google/gemini-flash-1.5` is not available on OpenRouter now.

These free models completed the full tool loop through `/llm/chat` in a test:

- `minimax/minimax-m3.1-flash:free`
- `nvidia/nemotron-3.5-lightning:free`
- `nvidia/nemotron-3-ultra-550b-a55b:free`

`poolside/laguna-s-2.1:free` returned a provider error in the same test.

Free models can disappear from OpenRouter. If the default model stops, use one of these
methods to change the model:

- Start with `-Dopenrouter.model=<model-id>`. This changes the chatbot and `/llm/chat`.
- In the chatbot, select a model in the list. Click **Set Default** to keep it.
- In the chatbot, enter the model in the **Settings** dialog and click **Save**.
- For one `/llm/chat` request, send the field `model`.

## Agent loop

`OpenRouterAgent.chatWithHistory()` runs the loop. Each turn sends one request to
`{base}/chat/completions`.

1. The agent resolves the API key. If there is no key, it returns
   `Error: No API key found. ...` and sends no request.
2. The agent makes the tool list from the registry. Each tool becomes
   `{"type":"function","function":{"name","description","parameters"}}`. The input
   schema of the tool is the value of `parameters`.
3. The agent sends `model`, `messages`, `tools` and `"tool_choice":"auto"`.
4. The agent reads `choices[0].finish_reason`:
   - `stop`, or no value: the agent adds the answer to the history. It returns the
     answer with the [References footer](#references-footer).
   - `tool_calls`: the agent continues with step 5.
   - Another value, for example `length`: the agent logs a warning and stops the loop.
5. The agent adds one assistant message that contains **all** the tool calls of the
   turn. Strict providers reject a history that echoes only the first call.
6. For each tool call, the agent does these steps:
   1. It reads the arguments. A model can send them as a JSON string or as a JSON
      object. The agent accepts the two forms.
   2. It changes each argument value to a string, as the MCP servlet does.
   3. It finds the tool by name in the registry and calls `execute`.
   4. It adds one message `{"role":"tool","tool_call_id":...,"content":...}` with the
      result.
7. The agent sends the next request with the longer history (step 3).

The loop has a limit of 10 turns (`MAX_TURNS`). At the limit, or after an unexpected
`finish_reason`, the agent returns
`Error: maximum turns (10) reached without a final answer`.

A tool error does not stop the loop. The agent gives the error text to the model as the
tool result:

| Condition | Tool result |
|-----------|-------------|
| No tool has that name | `Error: unknown tool '<name>'` |
| The tool throws an exception | `Error executing tool '<name>': <message>` |

Other errors stop the loop and return a text:

| Condition | Returned text |
|-----------|---------------|
| No connection, or an HTTP error with no body | `Error: failed to reach OpenRouter API` |
| The response is not JSON | `Error: unexpected response format from OpenRouter` |
| The response has `error` and no `choices` | `Error from OpenRouter: <message>` |
| `tool_calls` is empty | `Error: tool_calls array is empty` |

Each request has a connect timeout of 10 s and a read timeout of 60 s. The agent sends
the headers `HTTP-Referer: http://localhost:8080` and `X-Title: OSGi MCP Bridge`.

## References footer

The agent adds a **References** footer to each final answer:

```
---
**References:** none
```

When the model called `document_search`, the footer lists the source documents:

```
---
**References:**
- report.pdf
- notes.docx
```

The agent reads each citation line of the `document_search` result, for example
`[1] report.pdf (chunk 2)  (score 0.812)`. It deletes the number, the location and the
score, and keeps the file name. Each name occurs one time, in the sequence of the first
citation.

The agent adds the footer only to the returned text. The history does not contain it.

**Note:** `collectDocumentReferences()` depends on the citation format of
`DocumentSearchTool`. If you change that format, also change this method.

## HTTP endpoint /llm/chat

`POST /llm/chat` gives the agent to HTTP clients. Each request is one new conversation
with no history.

Request:

```json
{"message": "What tools do you have?", "model": "nvidia/nemotron-3.5-lightning:free"}
```

| Field | Required | Description |
|-------|----------|-------------|
| `message` | yes | The user message. It must not be empty. |
| `model` | no | A model ID for this request only. It has precedence over `-Dopenrouter.model`. |

Response (HTTP 200):

```json
{"response": "I have these tools: ...\n\n---\n**References:** none"}
```

An agent error, for example a missing API key, also gives HTTP 200. The text of
`response` then starts with `Error`.

| Condition | HTTP status |
|-----------|-------------|
| The body is larger than 1 MB | 413 `Request too large` |
| The body is not valid JSON | 400 |
| `message` is missing or empty | 400 |

The 413 and 400 errors use the default Jetty error page, not JSON.

Example, macOS and Linux:

```bash
curl -s -X POST http://localhost:8080/llm/chat -H 'Content-Type: application/json' \
  -d '{"message":"hello"}'
```

Example, Windows `cmd.exe`:

```cmd
curl -s -X POST http://localhost:8080/llm/chat -H "Content-Type: application/json" -d "{\"message\":\"hello\"}"
```

For the full test procedure, refer to [mcp-server-testing.md](mcp-server-testing.md).

**Warning:** `/llm/chat` has no authentication. Each call uses your OpenRouter credits.
Refer to [`../SECURITY.md`](../SECURITY.md).

## Chatbot window

The window "OSGi Chatbot" has three areas.

| Area | Controls |
|------|----------|
| Top bar | **Model** list (editable), **Refresh Models**, **Set Default**, **Settings** |
| Center | The conversation. `You:` messages are blue, `Assistant:` messages use Markdown, and status messages are gray. |
| Bottom | A text area with more than one line, **Clear** and **Send** |

### Startup

1. The window shows `Testing connection to <base URL>...`.
2. A `SwingWorker` calls `ModelFetcher` on a background thread.
3. If the list has models, the window shows `Connected! Loaded N tool-capable models.`
4. If the list is empty, the window shows `Connection failed — no models returned.`
   Examine the API key and the base URL in **Settings**.

### Model list

`ModelFetcher` sends `GET {base}/models` with the API key as a bearer token. It keeps
only the models whose `supported_parameters` contains `tools`. If a model has no
`supported_parameters` field (for example in LM Studio), `ModelFetcher` keeps it. The
list is in alphabetical sequence. On an error, the list is empty and the log shows the
cause.

- Type in the **Model** field to filter the list. The filter finds the text in any part
  of the model ID. Case is not important.
- Select a model in the list to use it in this session. The selection does not go into
  the file.
- Click **Set Default** to write the selected model to `settings/chatbot.properties`.
- Click **Refresh Models** to get the list again.

**Note:** The **Model** field applies only a model that is in the list from `ModelFetcher`. To use a
model that is not in the list, enter it in the **Settings** dialog.

### Send a message

1. Type the message.
2. Push **Enter** or click **Send**. To start a new line, push **Shift+Enter**.
3. The window shows `Thinking...`. The window disables **Send** and **Clear** until the answer
   comes.
4. The answer shows below `Assistant:`.

The LLM call runs in a `SwingWorker`. Thus the window continues to respond during the
call.

## Settings dialog

Click **Settings** to open the modal dialog.

| Field | Contents |
|-------|----------|
| **API Key** | A password field. It shows the current key, from any source. |
| **Model** | The current model ID. |
| **Base URL** | The current base URL. |

| Button | Action |
|--------|--------|
| **Test Connection** | Calls `ModelFetcher` with the values in the fields. It shows the number of tool-capable models, or `Connection failed`. It does not save. |
| **Cancel** | Closes the dialog. It does not save. |
| **Save** | Writes each non-empty field to `settings/chatbot.properties` on a background thread. Then it closes the dialog. |

**Caution:** The dialog fills each field with the current value, also when the value
comes from `-D` or from `OPENROUTER_API_KEY`. When you click **Save**, the dialog writes
all three values to the file. The file then has precedence over `-D` on each later
start. The file keeps the API key in plaintext. An empty field does not delete a value
from the file. Edit the file to delete a value.

## Markdown in answers

`MarkdownStyler` changes the Markdown of an answer into styled text in a `JTextPane`. It
uses commonmark-java 0.21.0 and its GFM tables extension. The two jars are in the
`lib/` folder of `com.kk.pde.ds.chatbot`, and `Bundle-ClassPath` includes them.

| Markdown | Result in the window |
|----------|---------|
| Headings, bold, italic | Larger or styled text |
| Inline code, code blocks | Monospaced font with a different color |
| Block quotes | A different color |
| Bullet and numbered lists | Indented items |
| Thematic break (`---`) | A horizontal line of characters |
| Links | Link style. You cannot click them. |
| Images | The text `[<title>]`. The window does not load images. |
| HTML | Plain text |
| GFM tables | Monospaced columns with ` \| ` and `-+-` separators |

The parse runs on a background thread. Only the insertion into the document runs on the
EDT.

`DarkTheme` sets the colors of the window and the `UIManager` defaults. `App` applies it
before it makes the first component.

## Conversation history

- `ChatService` keeps the history in memory, as a list of JSON messages.
- The history contains the user messages, the assistant tool-call messages, the tool
  results, and the final answers.
- The history does not contain the References footer.
- The agent sends the full history in each request. The history has no size limit.
- **Clear** deletes the history and the text in the window. The window then shows
  `Conversation cleared.`
- The history is lost when the product stops.
- `/llm/chat` does not use this history.

## Logging and privacy

The code logs these items at `INFO`, the default level:

- each chatbot message and each full answer (`ChatService`)
- each `/llm/chat` message (`LlmChatServlet`)
- each tool call with its arguments, and each tool result (`OpenRouterAgent`)

A tool result can contain document text from `document_search`. At `DEBUG`, the agent
also logs each raw provider response. The code does not log the API key.

Refer to [`../SECURITY.md`](../SECURITY.md), items 4 and 5.

## Troubleshooting

| Symptom | Cause | Correction |
|---------|-------|------------|
| `Error: No API key found. ...` | No key in the file, in `-D`, or in the environment. | Set `OPENROUTER_API_KEY`, or use the **Settings** dialog. |
| `Error from OpenRouter: No endpoints found for <model>.` | OpenRouter does not supply that model now. | Select a different model. Refer to [The default model](#the-default-model). |
| `Connection failed — no models returned.` | The key or the base URL is incorrect, or the endpoint is not available. | Examine the values in **Settings**. Click **Test Connection**. |
| The chatbot ignores `-Dopenrouter.model`. | `settings/chatbot.properties` has a `model` value. | Delete the value from the file. |
| `Error: maximum turns (10) reached without a final answer` | The model called tools 10 times, or sent an unexpected `finish_reason`. | Ask a more specific question, or use a different model. |
| `Error: failed to reach OpenRouter API` | No network connection, or an HTTP error with no body. | Examine the base URL and the network. Read the log. |

## Related documents

| Document | Contents |
|----------|----------|
| [mcp-server.md](mcp-server.md) | The MCP server, the tool registry and the 9 tools. |
| [mcp-server-testing.md](mcp-server-testing.md) | The manual test procedure, also for `/llm/chat`. |
| [rag-reference.md](rag-reference.md) | `document_search`, `ingest_documents` and the embeddings settings. |
| [`../SECURITY.md`](../SECURITY.md) | The known exposures of `/llm/chat`, the settings file and the logs. |
