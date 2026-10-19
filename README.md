# OSGi Declarative Services Application

A Java 8 OSGi application that shows Declarative Services, HTTP Whiteboard, health checks, and product builds with Maven Tycho.

This README is the entry page. All documentation is in [`docs/`](docs/). Each feature has one document that owns it.

---

## Contents

1. [Overview](#overview)
2. [Requirements](#requirements)
3. [Quick Start](#quick-start)
4. [Documentation](#documentation)
5. [Security](#security)
6. [License](#license)
7. [Contributing](#contributing)

---

## Overview

This repository is a **showcase**. It is a Java 8 OSGi application built with **Maven Tycho 4.0.13**. Each module shows one technique from end to end.

| Feature | What it shows | Read more |
|---------|---------------|-----------|
| **Tycho CI/CD build** | A Maven Tycho build that makes a p2 repository and runnable products for Linux, Windows and macOS. GitHub Actions runs it on pushes and pull requests to `main` | [`docs/build.md`](docs/build.md) |
| **Declarative Services** | API, implementation and consumer in separate bundles, with `@Component`, `@Reference`, `@Activate` and `@Deactivate` (Felix SCR) | [`docs/declarative-services.md`](docs/declarative-services.md), [`docs/architecture.md`](docs/architecture.md) |
| **HTTP Whiteboard (REST API)** | REST endpoints for an OSGi service through the HTTP Whiteboard servlet pattern | [`docs/rest-api.md`](docs/rest-api.md) |
| **Felix WebConsole and Health Checks** | Web administration with the DS plugin, plus custom and file-configured health checks | [`docs/webconsole-and-health-checks.md`](docs/webconsole-and-health-checks.md) |
| **MCP Server and Client** | A Model Context Protocol server (JSON-RPC 2.0 over HTTP) with 9 tools in an `IMcpTool` registry. A new tool is a new DS component | [`docs/mcp-server.md`](docs/mcp-server.md), [`docs/mcp-server-testing.md`](docs/mcp-server-testing.md) |
| **LLM Integration and Chatbot UI** | A bridge to OpenAI-compatible APIs with an agent loop that calls MCP tools, and a Swing chat window | [`docs/llm-and-chatbot.md`](docs/llm-and-chatbot.md) |
| **RAG (Document Q&A)** | Parse, chunk, embed and store documents. The LLM searches them with the `document_search` tool | [`docs/rag-reference.md`](docs/rag-reference.md), [`docs/rag-design.md`](docs/rag-design.md) |
| **OCR** | PDFBox renders scanned PDF pages, and the Tesseract CLI reads them and image files. No new Java dependencies | [`docs/rag-reference.md`](docs/rag-reference.md) |
| **ECF Remote Services (RSA)** | A DS `@Reference` in one JVM that a service in a second JVM satisfies over `ecftcp://`, with file-based EDEF discovery | [`docs/ecf-remote-services.md`](docs/ecf-remote-services.md) |
| **Spike: multi-frame, multi-JVM UI** | Two JVMs show one combined UI. They share only a few integers (`DockLayout`) over ECF | [`docs/spike-isolation-results.md`](docs/spike-isolation-results.md) |
| **Fat JAR launcher** | The full application as one `java -jar` file, next to the p2 product | [`docs/fat-jar.md`](docs/fat-jar.md) |
| **Two-tier testing** | Tests inside the bundles. `*Test` classes run in a plain JVM. `*IT` classes run inside a live Equinox. JUnit 5 and Mockito, 54 tests | [`docs/testing.md`](docs/testing.md) |
| **PDE to Tycho migration** | `tools/pde2tycho`, a CLI that moves an Eclipse PDE workspace to a Tycho build | [`docs/pde-to-tycho-migration.md`](docs/pde-to-tycho-migration.md) |
| **Few dependencies** | A hand-written JSON parser, OOXML read as a zip (no POI or Tika), and Tesseract as a subprocess (no Tess4J) | [`docs/rag-design.md`](docs/rag-design.md) |

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **JDK to build** | 17 or newer. CI uses Temurin 21 |
| **Java to run** | 8 or newer |
| **Maven** | Use the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows). It pins Maven 3.9.16. Tycho 4.0.13 does not start on Maven 3.10 |
| **Internet** | Maven must download the target platform dependencies |

For more detail, see [`docs/build.md`](docs/build.md).

---

## Quick Start

1. Build the product and run all tests:

   ```bash
   ./mvnw clean verify          # Windows: mvnw.cmd clean verify
   ```

2. Optional: set an OpenRouter API key to enable the chatbot and the LLM features:

   ```bash
   export OPENROUTER_API_KEY=your_key_here   # macOS/Linux
   set OPENROUTER_API_KEY=your_key_here      # Windows cmd.exe
   ```

   The default model is `minimax/minimax-m3.1-flash:free`. To change it, see [`docs/llm-and-chatbot.md`](docs/llm-and-chatbot.md).

3. Start the application:

   ```bash
   ./distribution/scripts/run.sh             # macOS/Linux
   distribution\scripts\run.bat              # Windows
   ```

   The clock window and the chatbot window open.

4. Open the services:
   - WebConsole: http://localhost:8080/system/console (user `admin`, password `admin`)
   - REST API: http://localhost:8080/api/greet

5. To stop the application, type `exit` at the `osgi>` prompt. Then type `y`.

For all start methods, the fat JAR, debugging and IDE setup, see [`docs/running.md`](docs/running.md). If a step fails, see [`docs/troubleshooting.md`](docs/troubleshooting.md).

---

## Documentation

All documentation is in [`docs/`](docs/). It follows ASD-STE100 Simplified Technical English.

**Build and run**

| Document | Contents |
|----------|----------|
| [`docs/build.md`](docs/build.md) | Requirements, build commands, CI, target platform, product definition, project structure |
| [`docs/running.md`](docs/running.md) | Start methods, run scripts, configuration, logging, debugging, IDE setup, OSGi console commands |
| [`docs/fat-jar.md`](docs/fat-jar.md) | The single-file launcher: how it works, build, run, known gaps |
| [`docs/troubleshooting.md`](docs/troubleshooting.md) | Symptoms, causes and fixes |

**Features**

| Document | Contents |
|----------|----------|
| [`docs/architecture.md`](docs/architecture.md) | Component and service wiring of all bundles |
| [`docs/declarative-services.md`](docs/declarative-services.md) | DS components, annotations, hand-written `OSGI-INF` XML, binding flow |
| [`docs/rest-api.md`](docs/rest-api.md) | REST endpoints, limits and errors |
| [`docs/webconsole-and-health-checks.md`](docs/webconsole-and-health-checks.md) | Felix WebConsole and health checks |
| [`docs/mcp-server.md`](docs/mcp-server.md) | MCP server, the 9 tools, MCP client, how to add a tool |
| [`docs/mcp-server-testing.md`](docs/mcp-server-testing.md) | Manual JSON-RPC test procedure for the MCP server |
| [`docs/llm-and-chatbot.md`](docs/llm-and-chatbot.md) | LLM agent loop, `/llm/chat`, chatbot UI, configuration |
| [`docs/rag-reference.md`](docs/rag-reference.md) | RAG and OCR configuration (all `rag.*` properties) and verification record |
| [`docs/rag-design.md`](docs/rag-design.md) | RAG design: why and how, with the problems found on the way |
| [`docs/ecf-remote-services.md`](docs/ecf-remote-services.md) | ECF remote services: design, run steps and lessons |
| [`docs/spike-isolation-results.md`](docs/spike-isolation-results.md) | Results of the multi-frame, multi-JVM UI spike |
| [`docs/multi-jvm-isolation-journey.md`](docs/multi-jvm-isolation-journey.md) | The steps that led to the multi-JVM design |
| [`docs/monolith-to-multi-jvm-migration.md`](docs/monolith-to-multi-jvm-migration.md) | How to move a single-JVM UI to the multi-JVM design |

**Tests and tools**

| Document | Contents |
|----------|----------|
| [`docs/testing.md`](docs/testing.md) | How to select, add and run tests in this repository |
| [`docs/adding-tests-to-a-tycho-project.md`](docs/adding-tests-to-a-tycho-project.md) | How to apply the two-tier test layout to a different Tycho or PDE project |
| [`docs/pde-to-tycho-migration.md`](docs/pde-to-tycho-migration.md) | How to move an Eclipse PDE workspace to Tycho with `tools/pde2tycho` |
| [`docs/claude-automation-recommendations.md`](docs/claude-automation-recommendations.md) | Recommended Claude Code automations for this repository |
| [`docs/superpowers/`](docs/superpowers/) | Design specs and implementation plans, with a status banner on each |

---

## Security

The HTTP endpoints on port 8080 (`/mcp`, `/llm/chat`, `ingest_documents`, `http_fetch`) have no authentication by design. Use them **on localhost only**. [`SECURITY.md`](SECURITY.md) lists each exposure. It also gives the checklist to complete before you bind the HTTP surface to an address other than localhost.

---

## License

[Add your license information here]

---

## Contributing

[Add contributing guidelines here]
