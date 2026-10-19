# MCP Server Test Procedure

This procedure tests the MCP server of the application. The MCP server is a JSON-RPC 2.0
endpoint at `http://localhost:8080/mcp`. It accepts HTTP **POST** requests with
`Content-Type: application/json`.

**Warning:** The HTTP surface has no authentication. Use it on localhost only. The tools
`http_fetch` and `ingest_documents` let a caller read URLs and local files. Read
[`../SECURITY.md`](../SECURITY.md) before you expose port 8080.

## Prerequisites

1. Build the project. In the project root, enter `./mvnw clean verify`.
2. Start the application. Refer to [running.md](running.md) for the run commands for each platform.
3. Open the Felix WebConsole at `http://localhost:8080/system/console`.
   The user name is `admin` and the password is `admin`.
4. Make sure that the WebConsole page opens. If it opens, the HTTP server operates.

---

## Available Tools

The server has 9 tools. The bundle `com.kk.pde.ds.mcp.server` supplies 7 tools. The bundle
`com.kk.pde.ds.rag` supplies 2 tools.

| Tool | Bundle | Arguments | Result |
|------|--------|-----------|--------|
| `echo` | `mcp.server` | `message` (required, string) | `Echo: <message>` |
| `greet` | `mcp.server` | `name` (optional, string) | `Hello, <name>!`. Without `name`: `Hello from OSGi MCP!` |
| `system_info` | `mcp.server` | none | The Java version, the OS, the CPU architecture, the heap size and the free memory |
| `calculator` | `mcp.server` | `a`, `b`, `operation` (all required, string) | The result of `add`, `subtract`, `multiply` or `divide` |
| `date_time` | `mcp.server` | `format` (optional, string) | The current date and time. The default format is `yyyy-MM-dd HH:mm:ss z` (a `SimpleDateFormat` pattern). |
| `bundle_list` | `mcp.server` | `filter` (optional, string) | The installed bundles with their states. `filter` is a substring of the symbolic name. |
| `http_fetch` | `mcp.server` | `url` (required, string) | The HTTP status and the first 4 KB of the response body. Only `http://` and `https://` URLs. |
| `document_search` | `rag` | `query` (required, string), `top_k` (optional, integer) | The best passages from the ingested documents, with citations. The default `top_k` is 5. The maximum is 20. |
| `ingest_documents` | `rag` | `path` (required, string) | Loads a file or a folder into the knowledge base. The tool reads folders recursively. |

For the RAG tools, refer to [`rag-reference.md`](rag-reference.md).

---

## macOS and Linux (zsh or bash)

All commands use `curl`. The flag `-s` stops the progress meter. The pipe to
`python3 -m json.tool` formats the JSON output.

The responses below come from a live run on macOS with Java 21. Your values for Java, OS,
memory, time and bundle versions will be different.

### Step 1: Send `initialize`

1. Enter this command:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' \
     | python3 -m json.tool
   ```

2. Make sure that the response is:

   ```json
   {
       "jsonrpc": "2.0",
       "id": 1,
       "result": {
           "protocolVersion": "2024-11-05",
           "capabilities": { "tools": {} },
           "serverInfo": { "name": "osgi-mcp-server", "version": "1.0.0" }
       }
   }
   ```

### Step 2: Send `notifications/initialized`

1. Enter this command:

   ```zsh
   curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
   ```

2. Make sure that the output is `204` (No Content). The server sends no body for a
   notification.

### Step 3: List the tools

1. Enter this command:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
     | python3 -m json.tool
   ```

2. Make sure that `result.tools` contains 9 tools, each with an `inputSchema`. The
   sequence can change. The live run gave this sequence:
   `system_info`, `bundle_list`, `date_time`, `http_fetch`, `calculator`, `greet`, `echo`,
   `ingest_documents`, `document_search`.

   One entry of the list:

   ```json
   {
       "name": "echo",
       "description": "Echoes back the provided message",
       "inputSchema": {
           "type": "object",
           "properties": {
               "message": { "type": "string", "description": "Message to echo back" }
           },
           "required": ["message"]
       }
   }
   ```

### Step 4: Call each tool

Each request uses `tools/call`. A successful response has this form:

```json
{
    "jsonrpc": "2.0",
    "id": 3,
    "result": {
        "content": [{ "type": "text", "text": "Echo: hello from mac" }],
        "isError": false
    }
}
```

The table after each command shows only the `text` value.

1. Call `echo`:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello from mac"}}}' \
     | python3 -m json.tool
   ```

   Text: `Echo: hello from mac`

2. Call `greet`:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"greet","arguments":{"name":"Kerem"}}}' \
     | python3 -m json.tool
   ```

   Text: `Hello, Kerem!`

3. Call `system_info`:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"system_info","arguments":{}}}' \
     | python3 -m json.tool
   ```

   Text (example): `Java: 21.0.12.1, OS: Mac OS X 27.0.1, Arch: aarch64, Heap: 72MB, Free: 39MB`

4. Call `calculator`:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"calculator","arguments":{"a":"6","b":"7","operation":"multiply"}}}' \
     | python3 -m json.tool
   ```

   Text: `42`. If the result is a whole number, the tool shows no decimal part.

5. Call `date_time`:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"date_time","arguments":{"format":"yyyy-MM-dd"}}}' \
     | python3 -m json.tool
   ```

   Text (example): `2026-10-19`

6. Call `bundle_list`:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"bundle_list","arguments":{"filter":"mcp"}}}' \
     | python3 -m json.tool
   ```

   Text (example):

   ```
   4 bundles matching 'mcp':
   [  7] com.kk.pde.ds.mcp.api                              1.0.0.202610191557 ACTIVE
   [  8] com.kk.pde.ds.mcp.client                           1.0.0.202610191557 ACTIVE
   [  9] com.kk.pde.ds.mcp.llm                              1.0.0.202610191557 ACTIVE
   [ 10] com.kk.pde.ds.mcp.server                           1.0.0.202610191557 ACTIVE
   ```

7. Call `http_fetch`. This step uses the local REST endpoint, not an external host:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"http_fetch","arguments":{"url":"http://localhost:8080/api/greet"}}}' \
     | python3 -m json.tool
   ```

   Text (example):

   ```
   HTTP 200:
   {"message":"Hello from OSGi HTTP Whiteboard!","timestamp":1792425697982}
   ```

8. Call `document_search` before you ingest documents:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"document_search","arguments":{"query":"OSGi"}}}' \
     | python3 -m json.tool
   ```

   Text: `The document knowledge base is empty. Ingest documents first (set -Drag.docs.dir at startup, or use the ingest_documents tool).`

9. Call `ingest_documents`. This step needs an embeddings endpoint. Set
   `OPENROUTER_API_KEY` before you start the application. Replace the path with an
   absolute path to a folder of documents:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"ingest_documents","arguments":{"path":"/absolute/path/to/ragdocs"}}}' \
     | python3 -m json.tool
   ```

   Text (example, one `.txt` file):

   ```
   Ingested 1 file(s), 0 failed, 1 chunk(s) added.
   + note.txt -> 1 chunk(s)
   Knowledge base now holds 1 chunk(s).
   ```

10. Call `document_search` again:

    ```zsh
    curl -s -X POST http://localhost:8080/mcp \
      -H "Content-Type: application/json" \
      -d '{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"document_search","arguments":{"query":"OSGi","top_k":2}}}' \
      | python3 -m json.tool
    ```

    Text (example):

    ```
    Found 1 relevant passage(s):

    [1] note.txt (chunk 1)  (score 0.798)
    The OSGi framework uses Declarative Services.
    ```

### Step 5: Test an unknown method

1. Enter this command:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":13,"method":"does/not/exist","params":{}}' \
     | python3 -m json.tool
   ```

2. Make sure that the response is:

   ```json
   {
       "jsonrpc": "2.0",
       "id": 13,
       "error": { "code": -32601, "message": "Method not found: does/not/exist" }
   }
   ```

### Step 6: Test an unknown tool

1. Enter this command:

   ```zsh
   curl -s -X POST http://localhost:8080/mcp \
     -H "Content-Type: application/json" \
     -d '{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"nonexistent","arguments":{}}}' \
     | python3 -m json.tool
   ```

2. Make sure that the response is:

   ```json
   {
       "jsonrpc": "2.0",
       "id": 14,
       "error": { "code": -32602, "message": "Unknown tool: nonexistent" }
   }
   ```

**Note:** The server accepts a request body of 1 MB maximum. For a larger body, it sends
HTTP 413 and the JSON-RPC error `-32600` `Request too large`.

---

## Windows (Command Prompt, cmd.exe)

In `cmd.exe`, put `\` before each double quotation mark in the JSON. Windows 10 1803 and
later contain `curl` (`C:\Windows\System32\curl.exe`).

**Note:** If `curl` is not available, install it from https://curl.se/windows/. Or use
PowerShell (refer to the next section).

### Step 1: Send `initialize`

1. Enter this command. The character `^` continues the command on the next line:

   ```cmd
   curl -s -X POST http://localhost:8080/mcp ^
     -H "Content-Type: application/json" ^
     -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
   ```

   The same command on one line:

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
   ```

2. Make sure that the response is the same as in the macOS section.

### Step 2: Send `notifications/initialized`

1. Enter this command:

   ```cmd
   curl -s -o NUL -w "%{http_code}" -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"
   ```

2. Make sure that the output is `204`.

**Note:** In a `.bat` file, write `%%{http_code}` instead of `%{http_code}`.

### Step 3: List the tools

1. Enter this command:

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
   ```

2. Make sure that the response contains the 9 tools.

### Step 4: Call the tools

1. Call `echo`. Make sure that the text is `Echo: hello from windows`.

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"hello from windows\"}}}"
   ```

2. Call `greet`. Make sure that the text is `Hello, Kerem!`.

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"greet\",\"arguments\":{\"name\":\"Kerem\"}}}"
   ```

3. Call `system_info`. Make sure that the text shows a Windows OS name, for example
   `Windows 10`.

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"system_info\",\"arguments\":{}}}"
   ```

4. Call `calculator`. Make sure that the text is `42`.

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"calculator\",\"arguments\":{\"a\":\"6\",\"b\":\"7\",\"operation\":\"multiply\"}}}"
   ```

5. For the other tools, use the JSON of the macOS section. Put `\` before each double
   quotation mark.

---

## Windows: PowerShell

In PowerShell, you can put the JSON in single quotation marks without changes:

```powershell
# Initialize
Invoke-RestMethod -Method POST -Uri http://localhost:8080/mcp `
  -ContentType "application/json" `
  -Body '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# List tools
Invoke-RestMethod -Method POST -Uri http://localhost:8080/mcp `
  -ContentType "application/json" `
  -Body '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# Echo tool
Invoke-RestMethod -Method POST -Uri http://localhost:8080/mcp `
  -ContentType "application/json" `
  -Body '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello from powershell"}}}'

# System info
Invoke-RestMethod -Method POST -Uri http://localhost:8080/mcp `
  -ContentType "application/json" `
  -Body '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"system_info","arguments":{}}}'
```

`Invoke-RestMethod` parses the JSON response. The output is a PowerShell object.
PowerShell does not show the nested fields, for example the text `Echo: hello from powershell`.
To see the full response as JSON, add `| ConvertTo-Json -Depth 10` after the command:

```powershell
Invoke-RestMethod -Method POST -Uri http://localhost:8080/mcp `
  -ContentType "application/json" `
  -Body '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello from powershell"}}}' |
  ConvertTo-Json -Depth 10
```

---

## Troubleshooting

| Symptom | Possible cause | Correction |
|---------|----------------|------------|
| `Connection refused` | The application does not run. | Start it with `./distribution/scripts/run.sh` (or `run.bat`). |
| `404 Not Found` | The MCP bundle is not active. | In the Felix WebConsole, open **Bundles**. Find `com.kk.pde.ds.mcp.server`. |
| `"isError": true` in the response | The tool threw an exception. | Find the `ERROR` lines in the OSGi console log. |
| `{"error":{"code":-32601}}` | The method name is incorrect. | Method names are case-sensitive. Compare the name with the steps above. |
| A tool is not in the `tools` list. | A DS component is not satisfied. | Do the component check below. |
| `document_search` or `ingest_documents` is not in the list. | The RAG components are not active. | Make sure that `com.kk.pde.ds.rag` is `ACTIVE`. Refer to [`rag-reference.md`](rag-reference.md). |

### Check the components in the Felix WebConsole

1. Open `http://localhost:8080/system/console`.
2. Open the **Components** tab.
3. Make sure that these 9 components of `com.kk.pde.ds.mcp.server` are active:
   - `McpServlet`
   - `McpToolRegistry`
   - `tools.EchoTool`
   - `tools.GreetTool`
   - `tools.SystemInfoTool`
   - `tools.CalculatorTool`
   - `tools.DateTimeTool`
   - `tools.BundleListTool`
   - `tools.HttpFetchTool`
4. Make sure that these 2 components of `com.kk.pde.ds.rag` are active:
   - `tool.DocumentSearchTool`
   - `tool.IngestDocumentsTool`

### Check the bundles in the OSGi console

The application shows an `osgi>` prompt after it starts.

1. Enter this command:

   ```
   ss mcp
   ```

2. Make sure that each MCP bundle shows `ACTIVE`.
3. Enter this command:

   ```
   scr:list
   ```

4. Make sure that the components of step 3 and step 4 above are active or satisfied.

---

## Connect LLMs to the MCP Server

### LM Studio (local, with MCP support)

LM Studio 0.3.17 and later can operate as an MCP host and connect to the OSGi server.
Checked against LM Studio 0.4.21 and the LM Studio MCP page
(https://lmstudio.ai/docs/app/mcp).

1. In LM Studio, open the **Program** tab in the right sidebar.
2. Click **Install** → **Edit mcp.json**. The file opens in the LM Studio editor. On
   macOS and Linux, the file is `~/.lmstudio/mcp.json`.
3. Add the `osgi-mcp` entry in `mcpServers`:

   ```json
   {
     "mcpServers": {
       "osgi-mcp": {
         "url": "http://localhost:8080/mcp"
       }
     }
   }
   ```

   LM Studio uses the Cursor `mcp.json` format. A remote server needs only `url`. The file
   `settings/mcp.json` in the project root also has a `"type": "http"` field for Claude
   Code. The LM Studio page does not show `type`, thus do not copy it.

4. Save the file.
5. Load a model that can use tools, for example Llama 3.1 8B, Qwen2.5 or Mistral Nemo.
6. Start a chat. LM Studio finds the 9 tools automatically.

Test prompts:

- *"What is the system info?"*: LM Studio calls `system_info` and shows the JVM output.
- *"Echo the phrase 'hello from LM Studio'"*: the result is `Echo: hello from LM Studio`.
- *"Greet me as Kerem"*: LM Studio calls `greet` with `name=Kerem`.

---

### OpenCode (terminal coding agent, with MCP support)

OpenCode reads its MCP servers from the `mcp` key of its configuration file. Checked
against the OpenCode MCP servers page (https://opencode.ai/docs/mcp-servers/) and
configuration page (https://opencode.ai/docs/config/), October 2026.

Configuration file. Use one of these:

- Global: `~/.config/opencode/opencode.json`
- One project only: `opencode.json` in the project root

1. Add this entry to the configuration file:

   ```json
   {
     "$schema": "https://opencode.ai/config.json",
     "mcp": {
       "osgi-mcp": {
         "type": "remote",
         "url": "http://localhost:8080/mcp",
         "enabled": true
       }
     }
   }
   ```

   **Note:** OpenCode uses `"type": "remote"` for HTTP servers. The key is `mcp`, not
   `mcpServers`. The optional `headers` field is an object, for example
   `{"Authorization": "Bearer <token>"}`. The OSGi server has no authentication, thus do not
   add `headers`.

2. Start `opencode`.
3. Name the tools in the conversation. Examples:
   - *"Use the system_info tool to check what Java version is running"*
   - *"Echo back the string 'hello from opencode'"*

---

### OpenRouter (through the `/llm/chat` bridge)

OpenRouter has no MCP support. The bundle `com.kk.pde.ds.mcp.llm` supplies a bridge. The
bridge does these steps:

1. It reads all tools from the OSGi service registry.
2. It changes them to the OpenAI function-calling format.
3. It runs the agent loop with the OpenRouter API.
4. It sends the final LLM answer.

**Warning:** Each `/llm/chat` request sends data to OpenRouter and uses your OpenRouter
credit.

#### Set the API key

Get your API key from https://openrouter.ai/settings/keys. Then use one of these two
methods:

1. Set the environment variable `OPENROUTER_API_KEY` before you start the application:

   ```bash
   # macOS / Linux
   export OPENROUTER_API_KEY=sk-or-v1-your-key-here
   ./distribution/scripts/run.sh

   # Windows cmd.exe
   set OPENROUTER_API_KEY=sk-or-v1-your-key-here
   distribution\scripts\run.bat
   ```

2. Or give the key as a system property:

   ```bash
   # macOS / Linux
   ./distribution/scripts/run.sh -Dopenrouter.api.key=sk-or-v1-your-key-here

   # Windows
   distribution\scripts\run.bat -Dopenrouter.api.key=sk-or-v1-your-key-here
   ```

If you set the two, the system property has priority.

#### Select a model (optional)

The default model is `minimax/minimax-m3.1-flash:free`. To use a different model, add
`-Dopenrouter.model`:

```bash
./distribution/scripts/run.sh \
  -Dopenrouter.api.key=sk-or-v1-your-key-here \
  -Dopenrouter.model=openai/gpt-4o-mini
```

#### Test the bridge

The request body has the key `message` and the optional key `model`. The response has the
key `response`. The request body is 1 MB maximum.

macOS and Linux:

```zsh
curl -s -X POST http://localhost:8080/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the system info and echo hello back to me?"}' \
  | python3 -m json.tool
```

Windows `cmd.exe`:

```cmd
curl -s -X POST http://localhost:8080/llm/chat -H "Content-Type: application/json" -d "{\"message\":\"What is the system info and echo hello back to me?\"}"
```

Windows PowerShell:

```powershell
Invoke-RestMethod -Method POST -Uri http://localhost:8080/llm/chat `
  -ContentType "application/json" `
  -Body '{"message":"What is the system info and echo hello back to me?"}'
```

Example response:

```json
{
  "response": "Here is what I found:\n\nSystem info: Java: 17.0.x, OS: Mac OS X 14.x, Arch: aarch64, Heap: 256MB, Free: 200MB\n\nEcho: hello"
}
```

The LLM calls `system_info` and `echo` automatically. Then it writes an answer in natural
language.

To use a different model for one request, add the key `model`:

```zsh
curl -s -X POST http://localhost:8080/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What tools do you have?","model":"anthropic/claude-3.5-haiku"}' \
  | python3 -m json.tool
```

#### Troubleshooting the bridge

| Symptom | Correction |
|---------|------------|
| `Error: No API key found. Set OPENROUTER_API_KEY env var or start with -Dopenrouter.api.key=your_key` | Set the API key (refer to "Set the API key"). Then start the application again. |
| `Error from OpenRouter: No endpoints found for <model>.` | OpenRouter does not supply that model now. Free models can disappear. Set `-Dopenrouter.model`, or send the key `model` in the request. Refer to [llm-and-chatbot.md](llm-and-chatbot.md#the-default-model). |
| `Error from OpenRouter: ...` (other message) | Examine the key, the model name and your OpenRouter credit balance. |
| `LlmChatServlet` is not in `scr:list`. | Make sure that the bundle `com.kk.pde.ds.mcp.llm` is `ACTIVE`. Enter `ss mcp.llm` in the OSGi console. |
