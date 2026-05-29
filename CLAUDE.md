# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an OSGi Declarative Services (DS) project built with Maven Tycho 4.0.13. It demonstrates service-oriented architecture with clear separation between API contracts, implementations, and consumers. The build produces a p2 repository and platform-specific product archives.

## Additional Instructions

For every project, write a detailed FOR[kerem].md file that explains the whole project in plain language.

Explain the technical architecture, the structure of the codebase and how the various parts are connected, the technologies used, why we made these technical decisions, and lessons I can learn from it (this should include the bugs we ran into and how we fixed them, potential pitfalls and how to avoid them in the future, new technologies used, how good engineers think and work, best practices, etc).

It should be very engaging to read; don't make it sound like boring technical documentation/textbook. Where appropriate, use analogies and anecdotes to make it more understandable and memorable.

## Build Commands

```bash
# Full build (recommended for CI/CD)
mvn clean verify

# Quick build without tests
mvn clean package -DskipTests

# Build specific module
mvn clean package -pl com.kk.pde.ds.api

# Resume failed build from distribution
mvn verify -rf :distribution
```

## Build Outputs

After a successful build:
- **p2 Repository:** `distribution/target/repository/`
- **Product Archives:**
  - `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz`
  - `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip`
  - `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz`

## Running the Application

> **Java requirement**: Java 8+ required at runtime.

**Simplest — run directly from the source tree (scripts auto-detect OS and product path):**

```bash
# macOS / Linux (set OPENROUTER_API_KEY env var for chatbot/LLM features):
export OPENROUTER_API_KEY=your_key_here
./distribution/scripts/run.sh

# Windows:
set OPENROUTER_API_KEY=your_key_here
distribution\scripts\run.bat
```

The API key can also be passed as a system property: `./distribution/scripts/run.sh -Dopenrouter.api.key=your_key`

**Or from inside the built product directory:**

macOS product is wrapped in an `.app` bundle:
```bash
cd distribution/target/products/com.kk.pde.ds.product/macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse
java -jar plugins/org.eclipse.osgi_*.jar -configuration configuration -console -consoleLog
```

Linux:
```bash
cd distribution/target/products/com.kk.pde.ds.product/linux/gtk/x86_64
java -jar plugins/org.eclipse.osgi_*.jar -configuration configuration -console -consoleLog
```

**With remote debugging:**
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar plugins/org.eclipse.osgi_*.jar -configuration configuration -console -consoleLog
```

The `-configuration configuration` flag is required to point the OSGi framework to the config.ini file.

## Felix WebConsole

Access the web console at: http://localhost:8080/system/console
- Username: `admin`
- Password: `admin`

The DS tab shows all Declarative Services components and their status.

## Module Structure

```
com.kk.pde.ds.target   → Target platform definition (Eclipse 2024-12)
com.kk.pde.ds.api      → IGreet interface (service contract)
com.kk.pde.ds.imp      → Greet implementation (service provider)
com.kk.pde.ds.app      → App consumer (@Reference injection, launches Clock + Chatbot UI)
com.kk.pde.ds.rest     → REST API via HTTP Whiteboard (@Reference injection)
com.kk.pde.ds.mcp.api  → MCP tool contract (IMcpTool, IMcpToolRegistry)
com.kk.pde.ds.mcp.server → MCP server (HTTP servlet + tool registry + built-in tools)
com.kk.pde.ds.mcp.client → MCP client (tests server via HTTP/JSON-RPC)
com.kk.pde.ds.mcp.llm  → OpenRouter LLM bridge (agent loop with tool calls)
com.kk.pde.ds.chatbot  → Swing chatbot UI (model selection, chat history, LLM integration)
com.kk.pde.ds.ecf.api      → IRemoteGreet contract (ECF remote service interface)
com.kk.pde.ds.ecf.host     → RemoteGreetImpl exported as a remote service (ECF RSA)
com.kk.pde.ds.ecf.consumer → Imports IRemoteGreet via EDEF, @Reference injection
com.kk.pde.ds.feature  → Feature grouping all bundles
distribution           → p2 repository + product builds
```

## REST API

The `com.kk.pde.ds.rest` module exposes the IGreet service via HTTP endpoints using OSGi HTTP Whiteboard pattern.

**Endpoints:**

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/greet` | Returns JSON greeting |
| GET | `/api/greet/{name}` | Returns personalized greeting |
| POST | `/api/greet` | Echoes posted message |

**Test commands:**
```bash
# Basic greeting
curl http://localhost:8080/api/greet

# Personalized greeting
curl http://localhost:8080/api/greet/World

# Echo message
curl -X POST http://localhost:8080/api/greet -d '{"message":"Test"}'
```

**Response format:**
```json
{"message":"Hello from OSGi HTTP Whiteboard!","timestamp":1737734567890}
```

## Chatbot UI

The `com.kk.pde.ds.chatbot` bundle provides a Swing-based chat interface for interacting with LLMs.

**Features:**
- Multi-turn conversation with session history
- Model selection via editable dropdown (queries `/v1/models` endpoint)
- Works with any OpenAI-compatible API (OpenRouter, LM Studio, etc.)
- MCP tool integration — LLM can call registered tools during conversations
- Clear history button to reset conversation

**Configuration (environment variables or system properties):**

| Env Var | System Property | Default | Purpose |
|---------|----------------|---------|---------|
| `OPENROUTER_API_KEY` | `openrouter.api.key` | — | API key (required) |
| — | `openrouter.model` | `google/gemini-flash-1.5` | Default model ID |
| — | `openrouter.base.url` | `https://openrouter.ai/api/v1` | API base URL |

**Service binding flow:**
1. `OpenRouterAgent` injects `IMcpToolRegistry` for tool access
2. `ChatService` injects `OpenRouterAgent` for LLM communication
3. `App` injects `ChatService` and launches `ChatFrame` on the EDT
4. `ChatFrame` uses `SwingWorker` for non-blocking LLM calls

## ECF Remote Services

The `com.kk.pde.ds.ecf.*` bundles showcase **OSGi Remote Services** via Eclipse
Communication Framework (ECF). A service exported in one OSGi process (the *host*)
is consumed via DS `@Reference` in a separate process (the *consumer*) — the same
`@Reference` pattern as the local demo, stretched across a network socket.

**Topology:** two separate JVMs (two dedicated products), communicating over ECF's
**Generic provider** transport on `ecftcp://localhost:3288/server`. Discovery is
**file-based EDEF** (no ZooKeeper/mDNS daemon).

| Bundle | Role |
|--------|------|
| `com.kk.pde.ds.ecf.api` | `IRemoteGreet { String greet(String name); }` |
| `com.kk.pde.ds.ecf.host` | `RemoteGreetImpl` — `@Component` with `service.exported.interfaces=*`, `service.exported.configs=ecf.generic.server`, `ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server` |
| `com.kk.pde.ds.ecf.consumer` | `RemoteGreetConsumer` (`@Reference`) + `GreetCommand` (Gogo `ecf:greet`) + EDEF file `OSGI-INF/remote-service/remote-greet-endpoint.xml` referenced from the `Remote-Service` manifest header |

**Run it (two terminals):**
```bash
mvn clean verify
./distribution/scripts/run-ecf-host.sh       # terminal 1 — exports IRemoteGreet
./distribution/scripts/run-ecf-consumer.sh   # terminal 2 — imports + invokes greet("ECF")
```
The consumer logs `Remote response: Hello, ECF! (served remotely by host)`. At the
consumer's `osgi>` prompt, `ecf:greet World` re-invokes on demand.

**Flow:**
1. Host `RemoteGreetImpl` registers `IRemoteGreet` with `service.exported.*` properties
2. ECF RSA exports it via the Generic server (TCP socket on :3288)
3. Consumer's `Remote-Service` header → RSA reads the EDEF → connects → registers a proxy `IRemoteGreet`
4. Consumer's `@Reference` binds the proxy; the method call round-trips to the host JVM

**Key facts / pitfalls:**
- ECF is pinned to the **3.14.x line** (last with `JavaSE-1.8` BREEs); newer ECF needs Java 11/17. Bundles come from Maven Central (groupId `org.eclipse.ecf`). The runtime here is actually Java 21, so even Java-17 Equinox bundles resolve.
- The ECF bundle set must be enumerated **explicitly** in the target platform — OSGi `Require-Bundle`/`Import-Package` are invisible to Maven's transitive resolver.
- The static EDEF's `ecf.endpoint.id.ns` must be `org.eclipse.ecf.core.identity.StringID` (the Generic provider's identity namespace class), and `ecf.rsvc.id=1` (deterministic for a single exported service). Capture these from the host's exported `ECFEndpointDescription` log if they change.
- The two products exclude Felix HTTP/WebConsole so the two JVMs don't both grab port 8080.

## Architecture

**Service binding flow:**
1. `Greet` component registers as `IGreet` service
2. `App` component has `@Reference` on `setApi(IGreet)`
3. Felix SCR injects `Greet` into `App`
4. `App.start()` calls `api.greet()` → prints "Hello world!"

## Key Configuration Files

| File | Purpose |
|------|---------|
| `com.kk.pde.ds.target/*.target` | Target platform with p2 repository URLs |
| `distribution/p2.product` | Product definition (bundles, start levels) |
| `distribution/category.xml` | p2 repository category structure |
| `*/META-INF/MANIFEST.MF` | OSGi bundle metadata |
| `*/OSGI-INF/*.xml` | DS component declarations |

## Technology Stack

- **Java 8+** (all bundles and the fat JAR launcher target Java 8 bytecode)
- **Tycho 4.0.13** (Maven OSGi build)
- **Eclipse 2024-12** target platform
- **OSGi Declarative Services** via annotations
- **Felix SCR** runtime
- **Felix HTTP Jetty** with HTTP Whiteboard for REST API
- **Felix WebConsole** with DS plugin
- **Eclipse Communication Framework (ECF) 3.14.x** for OSGi Remote Services (RSA) with the Generic provider
- **Logback** for logging (default level: INFO)
