# Architecture

An architectural overview of the OSGi Declarative Services project, with emphasis on the MCP and LLM bundles.

## High-Level Bundle Map

```
                    ┌───────────────────────────────────────────────────────────┐
                    │                   OSGi Runtime (Felix SCR)                │
                    └───────────────────────────────────────────────────────────┘

  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
  │ ds.api       │    │ ds.imp       │    │ ds.app       │     ← Classic "hello
  │              │    │              │    │              │       world" DS demo
  │  IGreet      │◄───│  Greet       │◄───│  App         │       (shows the DS
  │ (contract)   │    │ (service)    │    │ (@Reference) │       injection idea
  └──────────────┘    └──────────────┘    └──────┬───────┘       in its simplest
                             ▲                   │                form)
                             │                   ▼ launches
                             │         ┌──────────────────────┐
                             │         │  Swing UI:           │
  ┌──────────────┐           │         │  Clock + ChatFrame   │
  │ ds.rest      │───────────┘         └──────────────────────┘
  │              │  exposes IGreet
  │ GreetServlet │  at /api/greet via
  │              │  HTTP Whiteboard
  └──────────────┘
```

## MCP + LLM Core

```
  ┌────────────────────────────────────────────────────────────────────────────┐
  │                              com.kk.pde.ds.mcp.api                         │
  │  Pure contract — no implementation, no dependencies on other bundles.      │
  │                                                                            │
  │    interface IMcpTool {                  interface IMcpToolRegistry {      │
  │      String getName();                     List<IMcpTool> getTools();     │
  │      String getDescription();              IMcpTool getTool(String n);    │
  │      String getInputSchema();   // JSON  }                                 │
  │      String execute(Map args);  // work                                    │
  │    }                                                                       │
  └────────────────────────────────────────────────────────────────────────────┘
                   ▲                                        ▲
                   │ implements IMcpTool                    │ implements
                   │ (MULTIPLE + DYNAMIC)                   │ IMcpToolRegistry
                   │                                        │
  ┌────────────────┴───────────────────────────────┐  ┌─────┴────────────────────┐
  │            com.kk.pde.ds.mcp.server            │  │                          │
  │                                                │  │ McpToolRegistry          │
  │  tools/ package — six @Component IMcpTools:    │  │                          │
  │    • EchoTool          • BundleListTool        │  │ CopyOnWriteArrayList     │
  │    • GreetTool         • SystemInfoTool        │  │ dynamically collects     │
  │    • CalculatorTool    • DateTimeTool          │  │ every IMcpTool service   │
  │    • HttpFetchTool                             │  │ in the framework.        │
  │                                                │  │                          │
  │  McpServlet  (@Component → Servlet at /mcp)    │  │ addTool()/removeTool()   │
  │    ├─ initialize  (protocol handshake)         │◄─┤ fired by SCR as bundles  │
  │    ├─ tools/list  ──► registry.getTools()      │  │ start/stop.              │
  │    └─ tools/call  ──► registry.getTool(name)   │  │                          │
  │                         .execute(args)         │  └──────────────────────────┘
  │                                                │           ▲
  │  Speaks JSON-RPC 2.0 over HTTP.                │           │ @Reference
  │  Used by *external* MCP clients only.          │           │ IMcpToolRegistry
  └────────────────────────────────────────────────┘           │
                   ▲                                            │
                   │ HTTP                                       │
                   │ (no OSGi imports                           │
                   │  from server)                              │
                   │                                            │
  ┌────────────────┴───────────────────┐       ┌────────────────┴────────────────────┐
  │      com.kk.pde.ds.mcp.client      │       │        com.kk.pde.ds.mcp.llm        │
  │                                    │       │                                     │
  │  Standalone smoke-test bundle.     │       │  OpenRouterAgent   @Component       │
  │  On activation (daemon thread):    │       │  ────────────────                   │
  │    1. POST initialize              │       │  Agent loop (max 10 turns):         │
  │    2. POST tools/list              │       │    1. buildToolsJson() from reg     │
  │    3. POST tools/call for each     │       │    2. POST /chat/completions        │
  │       tool, logs the response      │       │       to OpenRouter                 │
  │                                    │       │    3. if finish_reason=tool_calls:  │
  │  Proves the server works over      │       │         registry.getTool(n)         │
  │  HTTP end-to-end.                  │       │           .execute(args)            │
  └────────────────────────────────────┘       │         append tool msg, loop      │
                                               │    4. if finish_reason=stop:        │
                                               │         return content              │
                                               │                                     │
                                               │  Two entry methods:                 │
                                               │    chat(msg, model)  — stateless    │
                                               │    chatWithHistory(list, model)     │
                                               │      — caller owns message list     │
                                               │                                     │
                                               │  LlmChatServlet   @Component        │
                                               │    POST /llm/chat  {message,model}  │
                                               │    → agent.chat(...) → JSON reply   │
                                               └──────────┬──────────────────────────┘
                                                          ▲
                                                          │ @Reference
                                                          │ OpenRouterAgent
                                                          │
                                               ┌──────────┴──────────────────────────┐
                                               │       com.kk.pde.ds.chatbot         │
                                               │                                     │
                                               │  ChatService  @Component            │
                                               │    • owns List<String> history      │
                                               │    • ChatConfig → settings/         │
                                               │        chatbot.properties           │
                                               │    • send(txt) →                    │
                                               │        agent.chatWithHistory(...)   │
                                               │    • fetchModels() via              │
                                               │        ModelFetcher (/v1/models)    │
                                               │                                     │
                                               │  Swing UI:                          │
                                               │    ChatFrame                        │
                                               │      ├─ ChatPanel  (message list,   │
                                               │      │   MarkdownStyler renders)    │
                                               │      ├─ InputPanel (prompt + send)  │
                                               │      ├─ SettingsDialog (key/model   │
                                               │      │   /base URL, persisted)      │
                                               │      └─ DarkTheme                   │
                                               │    SwingWorker keeps LLM call       │
                                               │    off the EDT.                     │
                                               └─────────────────────────────────────┘
```

## Request Flows

```
┌───────────────────────── External MCP client flow ─────────────────────────┐
│                                                                            │
│   external tool ──HTTP──► /mcp (McpServlet) ──► IMcpToolRegistry           │
│                             │                        │                     │
│                             │                        ▼                     │
│                             │                   IMcpTool.execute()         │
│                             ▼                                              │
│                        JSON-RPC response                                   │
└────────────────────────────────────────────────────────────────────────────┘

┌───────────────────────── Chatbot UI flow (in-process) ─────────────────────┐
│                                                                            │
│   user types  ──► ChatFrame ──► ChatService.send(msg)                      │
│                                      │                                     │
│                                      ▼                                     │
│                    OpenRouterAgent.chatWithHistory(history, model)         │
│                                      │                                     │
│                   ┌──────────────────┼───────────────────┐                 │
│                   ▼                  ▼                   ▼                 │
│            IMcpToolRegistry   HTTPS → OpenRouter   tool_call loop          │
│            (inject tools        (model chooses      (execute tool in       │
│             into request)        to call tool)      process, append        │
│                                                     result, repeat)        │
│                                      │                                     │
│                                      ▼                                     │
│                            final assistant content                         │
│                            → rendered in ChatPanel                         │
└────────────────────────────────────────────────────────────────────────────┘

┌───────────────────────── HTTP chat flow (stateless) ───────────────────────┐
│                                                                            │
│   curl ──HTTP──► /llm/chat (LlmChatServlet)                                │
│                      │                                                     │
│                      ▼                                                     │
│                OpenRouterAgent.chat(msg, model)                            │
│                      │                                                     │
│                      └── same agent loop as above, fresh history ──┐       │
│                                                                    ▼       │
│                                                            JSON response   │
└────────────────────────────────────────────────────────────────────────────┘
```

## Bundle Roles

| Bundle | Role | Key types | Exposes |
|---|---|---|---|
| `ds.api` | "Hello world" service contract | `IGreet` | — |
| `ds.imp` | `IGreet` implementation | `Greet` | `IGreet` service |
| `ds.app` | Consumer + UI launcher | `App`, `Clock`, launches `ChatFrame` | — |
| `ds.rest` | REST wrapper around `IGreet` | `GreetServlet` (HTTP Whiteboard) | `/api/greet*` |
| **`mcp.api`** | **MCP contracts** | **`IMcpTool`, `IMcpToolRegistry`** | **OSGi packages** |
| **`mcp.server`** | **MCP protocol endpoint + built-in tools** | **`McpServlet`, `McpToolRegistry`, 6× `*Tool`** | **`/mcp` + `IMcpToolRegistry`** |
| **`mcp.client`** | **Smoke-test harness (HTTP roundtrip)** | **`McpClient` (daemon thread on activate)** | **log output** |
| **`mcp.llm`** | **LLM ↔ tool registry bridge** | **`OpenRouterAgent`, `LlmChatServlet`** | **`OpenRouterAgent` service + `/llm/chat`** |
| **`chatbot`** | **Swing UI + stateful chat** | **`ChatFrame`, `ChatService`, `ChatConfig`** | **`ChatService` service** |
| `feature` / `distribution` / `target` | Packaging (p2 / product / target platform) | — | — |

## Key Design Insights

- **Contract vs. runtime separation.** `mcp.api` holds only `IMcpTool` and `IMcpToolRegistry`. `mcp.server` collects tools dynamically via `@Reference(cardinality=MULTIPLE, policy=DYNAMIC)`. Any bundle that registers an `IMcpTool` service instantly shows up in `tools/list` — no redeploy of the server needed.

- **The LLM agent skips HTTP for tool execution.** `OpenRouterAgent` depends on `IMcpToolRegistry` directly (OSGi service reference), so tool calls happen in-process. The `/mcp` servlet exists for *external* MCP clients; the internal LLM loop bypasses it entirely.

- **Two entry points into one agent.** HTTP (`LlmChatServlet` at `/llm/chat`, stateless) and Swing (`ChatService` → `OpenRouterAgent.chatWithHistory`, stateful multi-turn) share one `OpenRouterAgent` singleton but manage history independently. `chatWithHistory` takes a caller-owned `List<String>` messages — that's why `ChatService` can hold multi-turn state while `LlmChatServlet` stays stateless.

- **Three pathways, one registry.** External MCP JSON-RPC (`McpServlet`), HTTP chat (`LlmChatServlet`), and Swing chat (`ChatService`) all converge on `IMcpTool.execute`. The registry is the single source of truth — adding one tool service lights it up on all three channels simultaneously.

- **`mcp.client` is deliberately decoupled.** It only speaks HTTP and has *zero* compile-time references to `mcp.server` or `mcp.api`. This is the real-world MCP client shape — it proves the server actually implements the public protocol rather than cheating via shared Java interfaces.
