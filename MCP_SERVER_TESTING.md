# MCP Server Testing Guide

The MCP server is a JSON-RPC 2.0 endpoint mounted at `http://localhost:8080/mcp`.
It accepts HTTP **POST** requests with `Content-Type: application/json`.

## Prerequisites

1. Build the project: `mvn clean verify`
2. Start the application (see CLAUDE.md for platform-specific run instructions)
3. Verify the server is up by checking the Felix WebConsole: `http://localhost:8080/system/console`

---

## Available Tools

| Tool | Arguments | Description |
|------|-----------|-------------|
| `echo` | `message` (required, string) | Echoes the message back |
| `greet` | `name` (optional, string) | Returns a greeting |
| `system_info` | *(none)* | Returns JVM + OS stats |

---

## macOS / Linux (zsh / bash)

All commands use `curl`. The `-s` flag silences the progress meter.
Pipe through `python3 -m json.tool` for pretty-printed output.

### Step 1 — Handshake: initialize

```zsh
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' \
  | python3 -m json.tool
```

**Expected response:**
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

### Step 2 — Acknowledge: notifications/initialized

```zsh
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

**Expected output:** `204` (No Content — the server sends no body for notifications)

### Step 3 — List all tools

```zsh
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
  | python3 -m json.tool
```

**Expected response:** JSON listing `echo`, `greet`, and `system_info` with their input schemas.

### Step 4 — Call the `echo` tool

```zsh
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello from mac"}}}' \
  | python3 -m json.tool
```

**Expected response:**
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

### Step 5 — Call the `greet` tool (with name)

```zsh
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"greet","arguments":{"name":"Kerem"}}}' \
  | python3 -m json.tool
```

**Expected response text:** `Hello, Kerem!`

### Step 6 — Call the `system_info` tool

```zsh
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"system_info","arguments":{}}}' \
  | python3 -m json.tool
```

**Expected response text:** Something like `Java: 17.0.x, OS: Mac OS X 14.x, Arch: aarch64, Heap: 256MB, Free: 200MB`

### Step 7 — Test error handling (unknown method)

```zsh
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":6,"method":"does/not/exist","params":{}}' \
  | python3 -m json.tool
```

**Expected response:**
```json
{
    "jsonrpc": "2.0",
    "id": 6,
    "error": { "code": -32601, "message": "Method not found: does/not/exist" }
}
```

### Step 8 — Test error handling (unknown tool)

```zsh
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"nonexistent","arguments":{}}}' \
  | python3 -m json.tool
```

**Expected response:**
```json
{
    "jsonrpc": "2.0",
    "id": 7,
    "error": { "code": -32602, "message": "Unknown tool: nonexistent" }
}
```

---

## Windows (Command Prompt / cmd.exe)

Windows `cmd.exe` requires escaping internal double-quotes with `\"`.
The `curl` shown below ships with Windows 10 1803+ (`C:\Windows\System32\curl.exe`).

> **Note:** If `curl` is not available, install it from https://curl.se/windows/ or use PowerShell (see section below).

### Step 1 — Handshake: initialize

```cmd
curl -s -X POST http://localhost:8080/mcp ^
  -H "Content-Type: application/json" ^
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
```

*(Use `^` for line continuation in cmd.exe)*

One-liner version:
```cmd
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
```

**Expected response:** same JSON structure as macOS above.

### Step 2 — Acknowledge: notifications/initialized

```cmd
curl -s -o NUL -w "%%{http_code}" -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"
```

**Expected output:** `204`

### Step 3 — List all tools

```cmd
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
```

### Step 4 — Call the `echo` tool

```cmd
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"hello from windows\"}}}"
```

**Expected text in response:** `Echo: hello from windows`

### Step 5 — Call the `greet` tool

```cmd
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"greet\",\"arguments\":{\"name\":\"Kerem\"}}}"
```

**Expected text in response:** `Hello, Kerem!`

### Step 6 — Call the `system_info` tool

```cmd
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"system_info\",\"arguments\":{}}}"
```

**Expected text in response:** OS name will show `Windows 10` or similar.

---

## Windows — PowerShell Alternative

PowerShell handles JSON more cleanly because you can use single-quoted strings:

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

`Invoke-RestMethod` automatically parses the JSON response — output is already a PowerShell object.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `Connection refused` | App not running | Start via `./distribution/scripts/run.sh` (or `.bat`) |
| `404 Not Found` | MCP bundle not started | Check Felix WebConsole → Bundles; look for `com.kk.pde.ds.mcp.server` |
| `"isError": true` in response | Tool threw an exception | Check the OSGi console log for `ERROR` lines |
| `{"error":{"code":-32601}}` | Wrong method name | Method names are case-sensitive; verify against the list above |
| Empty `tools` array in list | DS components not satisfied | Check Felix WebConsole → Components; ensure `EchoTool`, `GreetTool`, `SystemInfoTool` are **active** |

### Check bundle status (OSGi console)

After starting the app you should see an `osgi>` prompt. Type:

```
ss mcp
```

All MCP bundles should show `ACTIVE`.

```
ds:list
```

Should show `McpServlet`, `McpToolRegistry`, `EchoTool`, `GreetTool`, `SystemInfoTool` all in **satisfied** state.

---

## Connecting LLMs to the MCP Server

### LM Studio (local, native MCP support)

LM Studio ≥ v0.3.17 can act as an MCP host and connect directly to the OSGi server.

1. Open **LM Studio** → **Settings** → **MCP**
2. Click **Add server** and enter:
   ```json
   {
     "osgi-mcp": {
       "type": "http",
       "url": "http://localhost:8080/mcp"
     }
   }
   ```
   (The `settings/mcp.json` file in the project root already contains this config — copy and paste from there.)
3. Load a tool-capable model (e.g. Llama 3.1 8B, Qwen2.5, Mistral Nemo)
4. Start chatting. LM Studio will automatically discover `echo`, `greet`, and `system_info`.

**Test prompts:**
- *"What is the system info?"* → LM Studio calls `system_info` and shows JVM output
- *"Echo the phrase 'hello from LM Studio'"* → returns `Echo: hello from LM Studio`
- *"Greet me as Kerem"* → calls `greet` with `name=Kerem`

---

### OpenCode (terminal coding agent, native MCP support)

OpenCode uses MCP servers registered in its config file.

**Config location:**
- macOS/Linux: `~/.config/opencode/config.json`
- Windows: `%APPDATA%\opencode\config.json`

**Add the following entry to your config:**
```json
{
  "mcpServers": {
    "osgi-mcp": {
      "type": "remote",
      "url": "http://localhost:8080/mcp",
      "headers": []
    }
  }
}
```

Note: OpenCode uses `"type": "remote"` for HTTP servers (vs LM Studio's `"type": "http"`).

**Usage:** Start `opencode`, then reference the tools naturally in conversation:
- *"Use the system_info tool to check what Java version is running"*
- *"Echo back the string 'hello from opencode'"*

---

### OpenRouter (via the `/llm/chat` bridge)

OpenRouter has no native MCP support. The `com.kk.pde.ds.mcp.llm` bundle provides a bridge that:
1. Reads all tools directly from the OSGi service registry
2. Converts them to OpenAI function-calling format
3. Runs the agent loop against OpenRouter's API
4. Returns the final LLM answer

**Setup:**

```bash
# Get your API key from https://openrouter.ai/settings/keys
# Start the app with the key as a system property:

# macOS / Linux
./distribution/scripts/run.sh -Dopenrouter.api.key=sk-or-v1-your-key-here

# Windows
distribution\scripts\run.bat -Dopenrouter.api.key=sk-or-v1-your-key-here
```

**Optional: choose a different model (default is `google/gemini-flash-1.5`):**
```bash
./distribution/scripts/run.sh \
  -Dopenrouter.api.key=sk-or-v1-your-key-here \
  -Dopenrouter.model=openai/gpt-4o-mini
```

**Test — macOS / Linux:**
```zsh
curl -s -X POST http://localhost:8080/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the system info and echo hello back to me?"}' \
  | python3 -m json.tool
```

**Test — Windows cmd.exe:**
```cmd
curl -s -X POST http://localhost:8080/llm/chat -H "Content-Type: application/json" -d "{\"message\":\"What is the system info and echo hello back to me?\"}"
```

**Test — Windows PowerShell:**
```powershell
Invoke-RestMethod -Method POST -Uri http://localhost:8080/llm/chat `
  -ContentType "application/json" `
  -Body '{"message":"What is the system info and echo hello back to me?"}'
```

**Expected response:**
```json
{
  "response": "Here is what I found:\n\nSystem info: Java: 17.0.x, OS: Mac OS X 14.x, Arch: aarch64, Heap: 256MB, Free: 200MB\n\nEcho: hello"
}
```

The LLM will call `system_info` and `echo` automatically, then compose a natural-language answer.

**Override the model per-request:**
```zsh
curl -s -X POST http://localhost:8080/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What tools do you have?","model":"anthropic/claude-3.5-haiku"}' \
  | python3 -m json.tool
```

**Troubleshooting the bridge:**

| Symptom | Fix |
|---------|-----|
| `"openrouter.api.key system property not set"` | Add `-Dopenrouter.api.key=...` to the start command |
| `Error from OpenRouter: ...` | Check the key, model name, and your OpenRouter credit balance |
| `LlmChatServlet` not in `ds:list` | Ensure `com.kk.pde.ds.mcp.llm` bundle is ACTIVE (`ss mcp.llm` in OSGi console) |
