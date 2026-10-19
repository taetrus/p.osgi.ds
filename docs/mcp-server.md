# MCP Server

This document describes the MCP (Model Context Protocol) server and client bundles. It
gives the protocol, the 9 tools, the limits, and the procedure to add a tool.

- [Overview](#overview)
- [Architecture](#architecture)
- [Protocol](#protocol)
- [Tools](#tools)
- [Request limits](#request-limits)
- [MCP client bundle](#mcp-client-bundle)
- [settings/mcp.json](#settingsmcpjson)
- [Try it](#try-it)
- [Add a custom tool](#add-a-custom-tool)
- [Related documents](#related-documents)

**Warning:** The HTTP surface has no authentication. Use it on localhost only. The tools
`http_fetch` and `ingest_documents` let a caller read URLs and local files. Read
[`../SECURITY.md`](../SECURITY.md) before you expose port 8080.

## Overview

The MCP bundles make a tool-call server that follows the
[Model Context Protocol](https://modelcontextprotocol.io/) specification. The server
speaks JSON-RPC 2.0 over HTTP at `http://localhost:8080/mcp`. Most MCP servers use Python
or Node. This server runs in Equinox, and each tool is an OSGi DS component.

| Bundle | Contents |
|--------|----------|
| `com.kk.pde.ds.mcp.api` | The contract: `IMcpTool`, `IMcpToolRegistry`, and the hand-written `Json` class. |
| `com.kk.pde.ds.mcp.server` | `McpServlet`, `McpToolRegistry`, and 7 tools. |
| `com.kk.pde.ds.rag` | 2 more tools: `document_search` and `ingest_documents`. |
| `com.kk.pde.ds.mcp.client` | `McpClient`, a demo client that calls the server at startup. |
| `com.kk.pde.ds.mcp.llm` | `OpenRouterAgent`. It gives the same tools to an LLM. Refer to [llm-and-chatbot.md](llm-and-chatbot.md). |

## Architecture

```
IMcpTool (service interface, com.kk.pde.ds.mcp.api)
    ├── echo, greet, system_info,          ─┐  com.kk.pde.ds.mcp.server/.../tools/
    │   calculator, date_time,              │
    │   bundle_list, http_fetch             ├── each tool is a @Component(service = IMcpTool.class)
    └── document_search, ingest_documents  ─┘  com.kk.pde.ds.rag/.../tool/
            │
    McpToolRegistry  (@Reference, cardinality 0..n, policy dynamic)
            │
            ├── McpServlet       (HTTP Whiteboard servlet at /mcp, JSON-RPC 2.0)
            └── OpenRouterAgent  (in-process tool calls for the LLM)
```

- `McpToolRegistry` collects all `IMcpTool` services. Its reference has the cardinality
  `0..n` and the policy `dynamic`. Thus a tool can start or stop at runtime, and the
  registry does not restart.
- The registry finds a tool by the value of `getName()`. If two tools have the same name,
  the registry returns the first tool that it received.
- `McpServlet` registers through the OSGi HTTP Whiteboard with the pattern `/mcp`. Felix
  HTTP Jetty serves it on port 8080.
- `OpenRouterAgent` reads the same registry. It calls the tools in-process, not through
  HTTP.

## Protocol

The servlet accepts only HTTP POST with a JSON body. Other HTTP methods get the default
servlet error (HTTP 405). The server identifies itself as `osgi-mcp-server`, version
`1.0.0`.

| Method | Result |
|--------|--------|
| `initialize` | `protocolVersion` `2024-11-05`, `capabilities` `{"tools":{}}`, and `serverInfo`. |
| `notifications/initialized` | HTTP 204 with no body. |
| `tools/list` | `tools`: an array with `name`, `description` and `inputSchema` for each tool. |
| `tools/call` | `content`: one item `{"type":"text","text":"..."}`, and `isError`. |

The response keeps the JSON type of the request `id` (string, number or `null`).

For `tools/call`, send `params.name` and `params.arguments`. The servlet changes each
argument value to a string before it calls `IMcpTool.execute(Map<String, String>)`:

- A number becomes its text. `5` and `"5"` give the same value `"5"`.
- A nested object or array becomes its JSON text.

If the tool throws an exception, the result has `isError: true` and the text
`Error: <message>`. Most tools do not throw. They return a text that starts with `Error:`,
and then `isError` is `false`.

### Error codes

| Code | Message | Cause |
|------|---------|-------|
| `-32700` | `Parse error: ...` | The body is not valid JSON. |
| `-32600` | `Invalid request: not a JSON object` | The body is valid JSON, but not an object (for example, a batch array). |
| `-32600` | `Invalid request: missing method` | The object has no `method`. |
| `-32600` | `Request too large` | The body is larger than 1 MB. The HTTP status is 413. |
| `-32601` | `Method not found: <method>` | The method is not in the table above. |
| `-32602` | `Missing tool name in params` | `tools/call` has no `params.name`. |
| `-32602` | `Unknown tool: <name>` | No registered tool has that name. |

All other errors use HTTP status 200 with a JSON-RPC `error` object.

## Tools

The server has 9 tools when all bundles of the main product are active.

| Tool | Bundle | Arguments | Result and limits |
|------|--------|-----------|-------------------|
| `echo` | `mcp.server` | `message` (required) | `Echo: <message>`. Without `message`: `Echo: (empty)`. |
| `greet` | `mcp.server` | `name` (optional) | Calls `IGreet.greet()`, which writes a log line. Returns `Hello, <name>!`, or `Hello from OSGi MCP!` without `name`. |
| `system_info` | `mcp.server` | none | Java version, OS name and version, CPU architecture, total heap and free heap in MB. |
| `calculator` | `mcp.server` | `a`, `b`, `operation` (all required) | `operation` is `add`, `subtract`, `multiply` or `divide`. A whole result shows no decimal point. Division by zero gives `Error: division by zero`. |
| `date_time` | `mcp.server` | `format` (optional) | The current date and time. The default pattern is `yyyy-MM-dd HH:mm:ss z`. `format` is a `SimpleDateFormat` pattern. |
| `bundle_list` | `mcp.server` | `filter` (optional) | One line for each bundle: ID, symbolic name, version and state. `filter` is a substring of the symbolic name. Case is not important. |
| `http_fetch` | `mcp.server` | `url` (required) | Sends a GET request. The tool accepts only `http://` and `https://` URLs. The connect and read timeouts are 5 s each. The tool returns the status and a maximum of 4096 characters of the body. |
| `document_search` | `rag` | `query` (required), `top_k` (optional) | The best passages with citation and score. `top_k` is 5 by default. A value outside 1 to 20 gives 5. Each passage is a maximum of 800 characters. |
| `ingest_documents` | `rag` | `path` (required) | Ingests a file, or a folder and its subfolders. Returns the result and the new chunk count. |

For the RAG tools, refer to [rag-reference.md](rag-reference.md). For a test procedure for
each tool, refer to [mcp-server-testing.md](mcp-server-testing.md).

## Request limits

- `/mcp` accepts a request body of 1 MB (1,048,576 characters) or less. A larger body
  gets HTTP 413 and the JSON-RPC error `-32600` `Request too large`.
- `http_fetch` returns a maximum of 4096 characters of the response body.
- `document_search` returns a maximum of 20 passages.

## MCP client bundle

`com.kk.pde.ds.mcp.client` contains `McpClient`, a DS component. It shows the client side
of the protocol from inside the framework.

1. At activation, `McpClient` reads the server URL from `-Dmcp.server.url`. The default
   is `http://localhost:8080/mcp`.
2. It starts the daemon thread `mcp-client`. The thread waits 3 seconds, so that the
   HTTP server can register the servlet.
3. The thread sends `initialize`, then `notifications/initialized`, then `tools/list`.
4. The thread calls only 3 tools: `echo`, `greet` (with `name` `OSGi`) and
   `system_info`.
5. It writes each response to the log. The last line is
   `=== MCP Client Protocol Run Complete ===`.

If the server does not answer `initialize`, the client logs
`Failed to connect to MCP server at <url>` and stops.

## settings/mcp.json

`settings/mcp.json` is a configuration file for an external MCP client, for example
Claude Code or an IDE. It tells that client where the server is:

```json
{
  "mcpServers": {
    "osgi-mcp": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

No code in this project reads the file. `distribution/pom.xml` copies it into each
product:

- On Linux and Windows, it goes to `<product>/settings/`.
- On macOS, it goes to `macosx/cocoa/x86_64/settings/`. This folder is outside
  `Eclipse.app/Contents/Eclipse`.

## Try it

1. Start the main product. Refer to [running.md](running.md).
2. List the tools.

   macOS and Linux:

   ```bash
   curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
   ```

   Windows `cmd.exe`:

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"
   ```

3. Call the tool `echo`.

   macOS and Linux:

   ```bash
   curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}'
   ```

   Windows `cmd.exe`:

   ```cmd
   curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"hi\"}}}"
   ```

4. Make sure that the result contains `Echo: hi`.
5. Make sure that the OSGi console shows the `McpClient` lines approximately 3 seconds
   after startup.

## Add a custom tool

The build does not make DS descriptors from the annotations. Each bundle keeps a
hand-written XML file in `OSGI-INF/` for each component. The jar contains these files
without change. Thus a new tool needs a Java class **and** an XML file.

Use `EchoTool` in `com.kk.pde.ds.mcp.server` as the model.

1. Select the bundle for the tool. You can use an existing bundle or a new bundle.
2. Open `META-INF/MANIFEST.MF` of the bundle.
3. Make sure that `Import-Package` contains these packages:

   ```
   com.kk.pde.ds.mcp.api,
   org.osgi.service.component.annotations;version="1.5.1";resolution:=optional
   ```

   If the class writes log lines, also import `org.slf4j;version="1.7.32"`.

4. Make sure that the `Service-Component` header includes the new XML file. The value
   `OSGI-INF/*.xml` includes all files. Some bundles list each file by name.
5. Make sure that `bin.includes` in `build.properties` contains `OSGI-INF/`.
6. Write the class. Use a unique tool name. Write the input schema as a JSON Schema
   string:

   ```java
   package com.example.tools;

   import java.util.Map;

   import org.osgi.service.component.annotations.Component;

   import com.kk.pde.ds.mcp.api.IMcpTool;

   @Component(service = IMcpTool.class)
   public class UpperCaseTool implements IMcpTool {

       @Override
       public String getName() {
           return "upper_case";
       }

       @Override
       public String getDescription() {
           return "Returns the text in upper case";
       }

       @Override
       public String getInputSchema() {
           return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\","
               + "\"description\":\"Text to change\"}},\"required\":[\"text\"]}";
       }

       @Override
       public String execute(Map<String, String> arguments) {
           String text = arguments.get("text");
           if (text == null || text.isEmpty()) {
               return "Error: text is required";
           }
           return text.toUpperCase();
       }
   }
   ```

7. Write the file `OSGI-INF/com.example.tools.UpperCaseTool.xml`:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.1.0" name="com.example.tools.UpperCaseTool">
      <service>
         <provide interface="com.kk.pde.ds.mcp.api.IMcpTool"/>
      </service>
      <implementation class="com.example.tools.UpperCaseTool"/>
   </scr:component>
   ```

8. If the class has a `@Reference`, add a `<reference>` element to the XML file. Use
   `com.kk.pde.ds.mcp.server.tools.GreetTool.xml` as the model.
9. If the bundle is new, add it to `com.kk.pde.ds.feature/feature.xml` and to
   `distribution/p2.product`. Also add the module to the parent `pom.xml`.
10. Build the project with `./mvnw clean verify`.
11. Start the product.
12. Send `tools/list` to `/mcp`. Make sure that the result contains `upper_case`.

**Note:** `OpenRouterAgent` reads the same registry. Thus the LLM can also call the new
tool. No other change is necessary.

**Caution:** Validate each argument in `execute`. The LLM and each HTTP client can send
any value. Do not give a tool access to files or network addresses without a limit.
Refer to [`../SECURITY.md`](../SECURITY.md).

## Related documents

| Document | Contents |
|----------|----------|
| [mcp-server-testing.md](mcp-server-testing.md) | The manual JSON-RPC test procedure for each method and each tool. |
| [llm-and-chatbot.md](llm-and-chatbot.md) | The LLM agent loop and the Swing chatbot that use these tools. |
| [rag-reference.md](rag-reference.md) | The RAG bundle and its two tools. |
| [`../SECURITY.md`](../SECURITY.md) | The known exposures of `/mcp`, `http_fetch` and `ingest_documents`. |
