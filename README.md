# OSGi Declarative Services Application

A Java 8 OSGi application that shows Declarative Services, HTTP Whiteboard, health checks, and product builds with Maven Tycho.

---

## Table of Contents

1. [Overview](#overview)
2. [Documentation](#documentation)
3. [Requirements](#requirements)
4. [Quick Start](#quick-start)
5. [Feature Showcase — Run Guide (macOS & Windows)](#feature-showcase--run-guide-macos--windows)
6. [Project Structure](#project-structure)
7. [Build System (Maven Tycho)](#build-system-maven-tycho)
8. [Target Platform](#target-platform)
9. [Declarative Services (DS)](#declarative-services-ds)
10. [HTTP Whiteboard (REST API)](#http-whiteboard-rest-api)
11. [Felix WebConsole](#felix-webconsole)
12. [Health Checks](#health-checks)
13. [MCP Server (Model Context Protocol)](#mcp-server-model-context-protocol)
14. [LLM Integration (OpenRouter / OpenAI-Compatible)](#llm-integration-openrouter--openai-compatible)
15. [Chatbot UI](#chatbot-ui)
16. [ECF Remote Services (OSGi RSA)](#ecf-remote-services-osgi-rsa)
17. [File-Based Configuration](#file-based-configuration)
18. [Logging](#logging)
19. [Product Definition](#product-definition)
20. [Running the Application](#running-the-application)
21. [Fat JAR (Single Executable)](#fat-jar-single-executable)
22. [Remote Debugging](#remote-debugging)
23. [IDE Integration](#ide-integration)
24. [OSGi Console Commands](#osgi-console-commands)
25. [Troubleshooting](#troubleshooting)
26. [File Reference](#file-reference)

---

## Overview

This repository is a **showcase**. It is a Java 8 OSGi application built with **Maven Tycho 4.0.13**. Each module shows one technique from end to end. For the run steps of each feature on macOS and Windows, see [Feature Showcase — Run Guide](#feature-showcase--run-guide-macos--windows).

| Feature | Description |
|---------|-------------|
| **Tycho CI/CD build** | Reproducible Maven Tycho build that makes a p2 repository and runnable platform-specific products (Linux/Windows/macOS). GitHub Actions runs it on pushes and pull requests to `main` |
| **Declarative Services** | The core showcase: API, implementation and consumer in separate bundles, with `@Component`, `@Reference`, `@Activate` and `@Deactivate` (Felix SCR) |
| **HTTP Whiteboard (REST API)** | REST-like endpoints for OSGi services through the HTTP Whiteboard servlet pattern |
| **Felix WebConsole** | Web-based administration with the DS plugin, for live runtime inspection |
| **Felix Health Checks** | Custom and general health checks with the WebConsole plugin |
| **MCP Server + Client** | Model Context Protocol server (JSON-RPC 2.0 over HTTP) with an `IMcpTool` service registry. A new tool is a new DS component. An MCP client bundle calls the server at startup |
| **LLM Integration** | Bridge to OpenAI-compatible APIs (OpenRouter, LM Studio, …) with an agent loop that calls MCP tools |
| **Chatbot UI** | Swing desktop chat with model selection, a Settings dialog and conversation history |
| **RAG (Document Q&A)** | Retrieval-augmented generation: parse → chunk → embed → vector store. The LLM gets it as a `document_search` tool |
| **OCR pipeline** | Gets text from pixels: PDFBox rasterizes scanned PDF pages, and the Tesseract CLI reads them and standalone or embedded images. No new Java dependencies |
| **ECF Remote Services (RSA)** | A DS `@Reference` that one JVM satisfies for another JVM over `ecftcp://`, with file-based EDEF discovery (no ZooKeeper) |
| **Spike: multi-frame / multi-JVM UI** | Two JVMs tile one screen as one combined UI. They share only a few integers (`DockLayout`) over ECF. All frames follow one draggable anchor window |
| **Fat JAR launcher** | The full OSGi application as one standalone `java -jar` executable. This is a second packaging, next to the p2 product |
| **Two-tier testing** | Tests sit inside the bundles (`src_test/`). Maven Surefire runs `*Test` classes in a plain JVM. Tycho Surefire `plugin-test` runs `*IT` classes *inside a live Equinox* and checks real DS wiring. JUnit 5 + Mockito, 54 tests written as a step-by-step tutorial |
| **Zero-dependency engineering** | A theme that repeats: a hand-written JSON parser, OOXML read as a zip (no POI/Tika), Tesseract as a subprocess (no Tess4J) |
| **Security posture** | [`SECURITY.md`](SECURITY.md) records the HTTP surface on localhost, which has no authentication by design, and the hardening checklist |
| **File Install** | Loads configuration files at runtime |
| **Target Platform** | Tycho target definition with Maven dependencies |
| **Remote Debugging** | JDWP support for debuggers |
| **Logging** | Logback with configurable levels |

---

## Documentation

This README is the reference. These documents give more detail:

| Document | Contents |
|----------|----------|
| [`docs/architecture.md`](docs/architecture.md) | Component and service wiring diagram |
| [`docs/testing.md`](docs/testing.md) | How to select, add and run tests in this repository |
| [`docs/adding-tests-to-a-tycho-project.md`](docs/adding-tests-to-a-tycho-project.md) | How to apply the two-tier test layout to a different Tycho or PDE project |
| [`docs/pde-to-tycho-migration.md`](docs/pde-to-tycho-migration.md) | How to move an Eclipse PDE workspace to Tycho with `tools/pde2tycho` or by hand |
| [`docs/mcp-server-testing.md`](docs/mcp-server-testing.md) | Manual JSON-RPC test procedure for the MCP server |
| [`docs/rag-reference.md`](docs/rag-reference.md) | RAG configuration reference (all `rag.*` properties) and verification record |
| [`docs/rag-design.md`](docs/rag-design.md) | RAG design narrative: why and how, with the problems found on the way |
| [`docs/ecf-remote-services.md`](docs/ecf-remote-services.md) | ECF remote services narrative: why and how, with the problems found on the way |
| [`docs/spike-isolation-results.md`](docs/spike-isolation-results.md) | Results of the multi-frame / multi-JVM UI spike |
| [`docs/multi-jvm-isolation-journey.md`](docs/multi-jvm-isolation-journey.md) | The steps that led to the multi-JVM design |
| [`docs/monolith-to-multi-jvm-migration.md`](docs/monolith-to-multi-jvm-migration.md) | How to move a single-JVM UI to the multi-JVM design |
| [`SECURITY.md`](SECURITY.md) | Known exposures on the HTTP surface and the checklist before you expose it beyond localhost |

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **JDK to build** | 17 or newer. Tycho 4 needs it, and the tier-2 test runtime needs `JavaSE-17`. CI uses Temurin 21 |
| **Java to run** | 8 or newer. All shipped bundles and the fat JAR launcher use Java 8 bytecode |
| **Maven** | Use the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows). It pins Maven 3.9.16 in `.mvn/wrapper/maven-wrapper.properties`. Tycho 4.0.13 does not start on Maven 3.10 |
| **Internet** | Maven must download the target platform dependencies |

---

## Quick Start

### Build
```bash
# Full build with tests (Windows: mvnw.cmd clean verify)
./mvnw clean verify

# Quick build (skip tests)
./mvnw clean package -DskipTests
```

### Run (after build)

**Option A — Launcher script** (runs the built product):

```bash
# macOS / Linux:
./distribution/scripts/run.sh

# Windows:
distribution\scripts\run.bat
```

**Option B — Fat JAR** (one executable, no product directory):

```bash
./mvnw -f fatjar clean package
java -jar fatjar/target/osgi-fatjar.jar
```

### Enable LLM Features (Chatbot)

Set your OpenRouter API key as an environment variable before you start the application:

```bash
export OPENROUTER_API_KEY=your_key_here   # macOS/Linux
set OPENROUTER_API_KEY=your_key_here      # Windows
```

The chatbot window opens automatically, together with the clock window.

### Access Services
- **WebConsole**: http://localhost:8080/system/console (admin/admin)
- **REST API**: http://localhost:8080/api/greet

---

## Feature Showcase — Run Guide (macOS & Windows)

You can run each feature below independently. All commands start at the
**repository root**. Build once with `./mvnw clean verify` (Windows: `mvnw.cmd clean verify`) before you start.

> **Windows shell note:** the commands are for **cmd.exe**. In PowerShell, use
> `curl.exe` instead of `curl`, because PowerShell uses `curl` as an alias for `Invoke-WebRequest`.
> Set environment variables with `$env:NAME = "value"` instead of `set NAME=value`.

### 1. Tycho Build & Runnable Artifacts (CI/CD)

**What it shows:** Maven Tycho builds an Eclipse PDE / OSGi DS project without an IDE.
Tycho resolves the target platform from Maven Central, makes a p2 repository, and makes
platform-specific runnable products. GitHub Actions runs the same build on each push and
each pull request to `main` ([.github/workflows/build.yml](.github/workflows/build.yml), Temurin 21).

**macOS / Windows:**
```bash
./mvnw clean verify      # macOS / Linux
mvnw.cmd clean verify    # Windows
```

Outputs:

| Artifact | Path |
|----------|------|
| p2 repository | `distribution/target/repository/` |
| macOS product | `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz` |
| Windows product | `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip` |
| Linux product | `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz` |

Each archive contains all that the application needs. Unpack it on the target machine. Then run
the launcher script in it, or `java -jar plugins/org.eclipse.osgi_*.jar -configuration configuration -console -consoleLog`.

### 2. Core OSGi Declarative Services (the greet demo)

**What it shows:** the base of all other features. `IGreet` (API bundle) →
`Greet` (implementation bundle) → `App` (consumer bundle). Felix SCR injects the services into `App` through `@Reference`.

```bash
# macOS
./distribution/scripts/run.sh

# Windows
distribution\scripts\run.bat
```

Look at the console. `App` activates, SCR injects `Greet`, and "Hello world!" appears.
`App` then opens the Clock and Chat windows. Open http://localhost:8080/system/console/components
(admin/admin) to see each DS component and the live state of its references.

### 3. REST API (HTTP Whiteboard)

**What it shows:** an OSGi service on HTTP through the Whiteboard pattern.
The servlet is a DS component. There is no `web.xml` and no servlet container configuration.

Start the product (feature 2). Then send these requests:

```bash
# macOS (Terminal)
curl http://localhost:8080/api/greet
curl http://localhost:8080/api/greet/World
curl -X POST http://localhost:8080/api/greet -d '{"message":"Test"}'

# Windows (cmd.exe — note the escaped quotes on POST)
curl http://localhost:8080/api/greet
curl http://localhost:8080/api/greet/World
curl -X POST http://localhost:8080/api/greet -d "{\"message\":\"Test\"}"
```

### 4. Felix WebConsole

**What it shows:** live administration of a running OSGi framework: bundles,
services, DS components and configuration.

Start the product. Then open **http://localhost:8080/system/console** in a browser
(both operating systems, credentials `admin`/`admin`). Study the **Components** tab.
It shows the state of each `@Component` and the `@Reference`s that are bound.

### 5. Felix Health Checks

**What it shows:** a custom health check (`GreetHealthCheck`) and general-purpose health checks with
the WebConsole plugin.

Start the product. Then open **http://localhost:8080/system/console/healthcheck**
(both operating systems). Run the checks by tag or by name, and look at the OK/WARN/CRITICAL results.
The `.cfg` files in the runtime `configuration/configs/` directory configure the general checks.
Felix File Install loads them. See [Health Checks](#health-checks).

### 6. MCP Server + Client (Model Context Protocol)

**What it shows:** an MCP tool server made of OSGi DS components. It speaks
JSON-RPC 2.0 over HTTP at `/mcp`. Because of the `IMcpTool` registry, a new tool is only a new `@Component`.
Most MCP servers use Python or Node. This one runs in Equinox.

Start the product. Then send these requests:

```bash
# macOS
curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}'

# Windows (cmd.exe)
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"hi\"}}}"
```

The `com.kk.pde.ds.mcp.client` bundle calls the same endpoint from inside the
framework. Its output appears in the OSGi console 3 seconds after startup.
For the full manual test procedure, see [`docs/mcp-server-testing.md`](docs/mcp-server-testing.md).

### 7. Chatbot UI + LLM Integration

**What it shows:** a Swing chat client that uses an OpenAI-compatible API. An
agent loop lets the model call the registered MCP tools during the conversation.

```bash
# macOS
export OPENROUTER_API_KEY=your_key_here
./distribution/scripts/run.sh

# Windows (cmd.exe)
set OPENROUTER_API_KEY=your_key_here
distribution\scripts\run.bat

# Windows (PowerShell)
$env:OPENROUTER_API_KEY = "your_key_here"
distribution\scripts\run.bat
```

The chatbot window opens automatically. Select a model from the list. The list comes from
`/v1/models` and shows only models that support tools. Then ask a question that needs a tool, for example
*"what bundles are running?"*. The model calls `bundle_list`. To use a local LM Studio
instead of OpenRouter, add `-Dopenrouter.base.url=http://localhost:1234/v1` to the
script command. This works on both operating systems, because the scripts give `-D` flags to the JVM.

There is also an HTTP endpoint without a UI. The optional `"model"` field overrides the default model:

```bash
# macOS
curl -s -X POST http://localhost:8080/llm/chat -H 'Content-Type: application/json' \
  -d '{"message":"hello"}'

# Windows (cmd.exe)
curl -s -X POST http://localhost:8080/llm/chat -H "Content-Type: application/json" -d "{\"message\":\"hello\"}"
```

### 8. RAG — Document Q&A

**What it shows:** retrieval-augmented generation as OSGi services. The pipeline is: parse (PDFBox /
OOXML as zip) → chunk (sliding window) → embed (`/v1/embeddings`) → in-memory vector
store. The LLM gets it as the `document_search` and `ingest_documents` MCP tools, so the
model decides when to search the documents.

```bash
# macOS — ingest a folder automatically at startup
export OPENROUTER_API_KEY=your_key_here
./distribution/scripts/run.sh -Drag.docs.dir=/path/to/your/documents

# Windows (cmd.exe)
set OPENROUTER_API_KEY=your_key_here
distribution\scripts\run.bat -Drag.docs.dir=C:\path\to\your\documents
```

Then ask the chatbot a question about your documents. The model calls `document_search`
itself. The agent adds a **References** footer with the source document names to the answer.
You can also call the tools directly over HTTP:

```bash
# macOS
curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' -d \
 '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"ingest_documents","arguments":{"path":"/path/to/docs"}}}'
curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' -d \
 '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"document_search","arguments":{"query":"your question","top_k":"5"}}}'

# Windows (cmd.exe)
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"ingest_documents\",\"arguments\":{\"path\":\"C:\\\\path\\\\to\\\\docs\"}}}"
curl -s -X POST http://localhost:8080/mcp -H "Content-Type: application/json" -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"document_search\",\"arguments\":{\"query\":\"your question\",\"top_k\":\"5\"}}}"
```

Supported formats: `.pdf`, `.docx`, `.pptx`, `.html`/`.htm`, `.txt`/`.md`, and (with
OCR, below) standalone images. The default embeddings model is `intfloat/multilingual-e5-large`.
For all `rag.*` properties, see [`docs/rag-reference.md`](docs/rag-reference.md). For the design, see [`docs/rag-design.md`](docs/rag-design.md).

### 9. OCR — Scanned PDFs & Images

**What it shows:** text recovery from pixels. PDFBox's `PDFRenderer` rasterizes scanned PDF pages,
and OCR reads them. OCR also reads images in PDFs and `.docx` files, and
standalone image files (`.png`/`.jpg`/`.jpeg`/`.tif`/`.tiff`/`.bmp`/`.gif`).
The engine runs the **Tesseract CLI** as a subprocess. It adds no Java or OSGi dependencies.

Install Tesseract one time:

```bash
# macOS
brew install tesseract

# Windows — either:
winget install UB-Mannheim.TesseractOCR
# or: choco install tesseract
# (make sure tesseract.exe is on PATH, or pass -Drag.ocr.binary=C:\path\to\tesseract.exe)
```

Then run with RAG (feature 8). OCR starts automatically when the application finds the binary.
Useful flags for both operating systems (add them to the run script command): `-Drag.ocr.lang=eng+tur`,
`-Drag.ocr.dpi=300`, and `-Drag.ocr.enabled=false` to disable OCR. If Tesseract is
not installed, ingestion reads only the text layer and logs no error. For servers without a screen, add
`-Djava.awt.headless=true`.

### 10. ECF Remote Services (OSGi RSA)

**What it shows:** the same DS `@Reference` pattern across a network. One JVM exports a
service. A component in a second JVM gets it through the Eclipse
Communication Framework's Generic provider (`ecftcp://localhost:3288`), with
file-based EDEF discovery (no ZooKeeper or mDNS daemon). It uses two dedicated products.

```bash
# macOS — terminal 1 (host: exports IRemoteGreet), then terminal 2 (consumer)
./distribution/scripts/run-ecf-host.sh
./distribution/scripts/run-ecf-consumer.sh

# Windows — two cmd windows
distribution\scripts\run-ecf-host.bat
distribution\scripts\run-ecf-consumer.bat
```

The consumer logs `Remote response: Hello, ECF! (served remotely by host)`. To call again,
type `ecf:greet World` at the consumer's `osgi>` prompt. This is a custom Gogo
shell command, which is one more small showcase.

### 11. Spike — Multi-Frame, Multi-JVM Combined UI

**What it shows:** two Swing applications in **separate JVMs** tile one screen as
one UI. They do not exchange window positions. The master publishes a
`DockLayout` (six integers) over ECF (`ecftcp://localhost:3289/catalog`), and the two
sides calculate the same geometry from the same formula. One master frame is the
*anchor*, which you can drag and minimize. All other frames, in the two JVMs, follow it.

```bash
# macOS — terminal 1 (master FIRST), then terminal 2 (detail)
./distribution/scripts/run-spike-master.sh
./distribution/scripts/run-spike-detail.sh

# Windows — two cmd windows (master first)
distribution\scripts\run-spike-master.bat
distribution\scripts\run-spike-detail.bat
```

Click a catalog row in a master frame. The detail JVM shows it on its next poll.
Drag or minimize the anchor frame, and all windows follow. To get more frames, use this command:

```bash
# macOS
SPIKE_FRAMES=3 ./distribution/scripts/run-spike-master.sh

# Windows (cmd.exe)
set SPIKE_FRAMES=3
distribution\scripts\run-spike-master.bat
```

When you close a window, **both** JVMs stop. The closing flag goes to the other JVM through the
shared `DockState`. For the design and the findings, see [`docs/spike-isolation-results.md`](docs/spike-isolation-results.md).

### 12. Fat JAR (Single Executable)

**What it shows:** the same OSGi application in one standalone JAR. `Main` starts Equinox
from code and loads the bundles from inside the JAR. This is a second delivery
model, next to the p2 product. Maven builds it outside the Tycho reactor.

```bash
# Both operating systems — build the product first, then the fat JAR
./mvnw clean verify
./mvnw -f fatjar clean package

# macOS
java -jar fatjar/target/osgi-fatjar.jar
# (or with LLM features)
OPENROUTER_API_KEY=your_key java -jar fatjar/target/osgi-fatjar.jar

# Windows (cmd.exe) — build with mvnw.cmd instead of ./mvnw
java -jar fatjar\target\osgi-fatjar.jar
rem (or with LLM features)
set OPENROUTER_API_KEY=your_key
java -jar fatjar\target\osgi-fatjar.jar
```

It has the same services as the product: WebConsole, REST, MCP and chatbot.

### 13. Tests Inside the Bundles (surefire + tycho-surefire:plugin-test, JUnit 5, Mockito)

**What it shows:** the test layout that the Tycho documentation recommends today, applied to a PDE-style
codebase. You can copy it bundle by bundle into a much larger project.
The tests sit **inside the bundle that they test**, in a `src_test/` folder. The class name selects
one of **two tiers**:

| Tier | Class name | Plugin | Phase | Runs in | Use for |
|------|-----------|--------|-------|---------|---------|
| 1 | `*Test` | `maven-surefire-plugin` | `test` | plain JVM, no OSGi | logic: everything you can reach with `new`. Fast. Most tests. |
| 2 | `*IT` | `tycho-surefire-plugin:plugin-test` | `integration-test` | a live Equinox Tycho assembles from the target platform | what only a framework can show: the bundle resolves, SCR activated the components, services carry the right properties |

There are seven tier-1 classes (**48 tests**) and two tier-2 classes (**6 tests**). The Javadoc
of each class is a short tutorial on one testing idea, for beginners.
For how to select, add and run tests, see [`docs/testing.md`](docs/testing.md).

#### What is tested

| Bundle | Class | Tier | Tests | What it covers / teaches |
|--------|-------|------|-------|--------------------------|
| `com.kk.pde.ds.imp` | `GreetTest` | 1 | 1 | **Start here.** The smallest possible unit test: Arrange–Act–Assert, `@Test`, how JUnit 5 finds test methods, where tests live and the two tiers |
| `com.kk.pde.ds.mcp.api` | `JsonTest` | 1 | 13 | The hand-rolled zero-dependency `Json` parser: round trips, numeric vs string ids, control characters and `\uXXXX` escapes, malformed input, and **JSON-RPC injection defence** |
| `com.kk.pde.ds.spike.master` | `DockLayoutTest` | 1 | 8 | Pure grid math shared by both spike JVMs — "happy path first, then the edges" |
| | `SpikeValueObjectsTest` | 1 | 4 | The two contracts a value object must honour before it can cross ECF: `equals`/`hashCode`, and a `Serializable` round trip |
| | `CatalogServiceImplTest` | 1 | 10 | A DS component's state, tested by calling its `@Activate` method directly from `@BeforeEach` |
| `com.kk.pde.ds.imp` | `GreetHealthCheckTest` | 1 | 6 | **Mockito, part 1.** Why a test needs a test double. A hand-written double vs `mock()`. `doThrow(...).when(mock).greet()` for **void** methods. `verify` / `times(1)` / `verifyNoMoreInteractions`. `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` into a **private `@Reference` field** |
| `com.kk.pde.ds.spike.master` | `MasterAppTest` | 1 | 6 | **Mockito, part 2.** `when(...).thenReturn(...)`, `ArgumentCaptor`, `InOrder`, `spy()` and its two gotchas. Headless tests of Swing code with `SwingUtilities.invokeAndWait` |
| `com.kk.pde.ds.imp` | `GreetServiceIT` | 2 | 3 | **In-framework, part 1.** The bundle is ACTIVE. SCR registered `IGreet`, and it is a `Greet`. **Real DS injection** satisfied the `@Reference` of the health check (the counterpart of the mocked tier-1 test) |
| `com.kk.pde.ds.spike.master` | `CatalogServiceIT` | 2 | 3 | **In-framework, part 2.** SCR ran `@Activate` (five seeded items) and registered `ICatalogService` with the `service.exported.*` properties the ECF export depends on |

`com.kk.pde.ds.mcp.api` has no `*IT` on purpose. An API-only bundle registers nothing, so
a framework boot can show nothing. Not every bundle needs tier 2.

Suggested reading order: `GreetTest` → `JsonTest` / `DockLayoutTest` /
`SpikeValueObjectsTest` / `CatalogServiceImplTest` → `GreetHealthCheckTest` →
`MasterAppTest` → `GreetServiceIT` → `CatalogServiceIT`. Design notes: the migration spec
[`docs/superpowers/specs/2026-09-16-tycho-plugin-test-migration-design.md`](docs/superpowers/specs/2026-09-16-tycho-plugin-test-migration-design.md)
(with the six things that the spike had to find) and the Mockito spec
[`docs/superpowers/specs/2026-07-28-mockito-osgi-test-mocking-design.md`](docs/superpowers/specs/2026-07-28-mockito-osgi-test-mocking-design.md).

#### How it works

1. **The test sources sit in the bundle, in `src_test/`.** The bundle keeps its PDE layout
   (`build.properties`: `source.. = src/`). The `.classpath` file declares the test folder
   with `<attribute name="test" value="true"/>`. Tycho reads that attribute and
   compiles the folder in the `test-compile` phase into `target/test-classes`, separate
   from the bundle's own classes. `build.properties` does not list the folder, so no file from it
   goes into the jar. Tests in `src/test/java` do **not** work with this layout.
   That folder is inside `src/`, so Tycho compiles the tests into the bundle.
2. **The test libraries are usual Maven test-scope dependencies.** The parent pom manages
   the versions (JUnit 5, `junit-platform-launcher`, Mockito, ByteBuddy,
   `org.osgi.framework`). Each bundle lists the libraries that it uses. `pomDependencies=consider` in the
   parent's `target-platform-configuration` adds them to the target platform of that module.
   They are not in a manifest, `feature.xml` or `p2.product`, so they never ship.
3. **Tier 1 is plain Surefire.** Tycho's `eclipse-plugin` lifecycle does not bind
   `maven-surefire-plugin` by default. Thus the parent's `pluginManagement` contains a `test`
   execution, and a bundle selects it when it lists the plugin. The test JVM sees the bundle's
   classes and the test dependencies on a flat classpath. There is no framework and there are no manifests.
4. **Tier 2 is `plugin-test`.** For a bundle that lists `tycho-surefire-plugin`, Tycho
   does these steps:
   1. It makes a *test fragment* from `target/test-classes`, with the bundle as host.
   2. It assembles a temporary Equinox from the target platform. The runtime contains the bundle, its requirements, JUnit and the fragment.
   3. It starts the runtime through `org.eclipse.equinox.launcher` and runs the `*IT` classes in it.

   The `verify` goal then stops the build if a test fails. A manifest error or an
   `Import-Package` that does not resolve causes a failure here, although `javac` accepted the code.
5. **The tier-2 runtime needs two settings.** A small `target-platform-configuration` block in the pom
   of each tier-2 bundle holds them:
   - `resolveWithExecutionEnvironmentConstraints` = false. The bundle's BREE is `JavaSE-1.8`, but the Tycho test harness needs `JavaSE-17`.
   - `extraRequirements` adds Felix SCR. Tycho's packaging adds a `Require-Capability: osgi.extender=osgi.component` header, and no other bundle in the runtime supplies it.

   The managed `tycho-surefire` configuration starts SCR at level 1, so the components activate.
6. **Mockito runs in a plain JVM** (tier 1 only). Its inline mock maker attaches a Java
   agent. Thus the log shows `Mockito is currently self-attaching…`. This is expected.
   The parent pins ByteBuddy, because the local JVM that runs the tests can be newer
   than the CI JVM.
7. **Swing tests run headless.** `com.kk.pde.ds.spike.master` gives
   `-Djava.awt.headless=true` to the JVMs of both tiers. `MasterAppTest` uses the
   package-private `buildPanels()` seam. `MasterApp.start()` has a headless guard, so
   no windows open when SCR activates the real component in tier 2.

#### How to run

```bash
# Everything — both tiers. CI runs the same goals (JDK 21).
./mvnw clean verify

# Tier 1 only (fast; no Equinox is booted)
./mvnw test

# One bundle. List its OSGi upstream modules explicitly — see the first gotcha below.
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.mcp.api
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master

# One tier-1 class or method: surefire's -Dtest, on the `test` phase so tier 2 never starts
./mvnw test -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dtest=GreetHealthCheckTest
./mvnw test -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master -Dtest='MasterAppTest#spyRunsTheRealServiceAndRecordsCalls'

# One tier-2 class: -Dit.test (Maven's failsafe convention; tier 1 still runs, it is fast)
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dit.test=GreetServiceIT

# Skip tests entirely
./mvnw clean package -DskipTests
```

CI runs `./mvnw --batch-mode --no-transfer-progress clean verify`. Then it runs
`./mvnw --batch-mode --no-transfer-progress -f tools/pde2tycho verify` for the migration tool, which is outside the Tycho reactor.

On Windows (`cmd` or PowerShell), use `mvnw.cmd` with the same arguments. Quote the `-Dtest` value as
shown when it contains `#`. The two properties are separate on purpose. Without them, the two plugins
read the same `-Dtest`. Then a `*Test` for tier 1 goes into Equinox, where Mockito is not available.
Or an `*IT` goes into the plain JVM, where there is no framework.

The console shows `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` for each
class and each tier. Reports: tier 1 in `<bundle>/target/surefire-reports/`, tier 2 in
`<bundle>/target/failsafe-reports/` (Tycho uses the failsafe layout). Each class gets one `*.txt` summary
and one `TEST-*.xml` file.

#### Adding tests to another bundle (or another project)

[`docs/adding-tests-to-a-tycho-project.md`](docs/adding-tests-to-a-tycho-project.md) is the full guide.
It has the parent-pom XML, the steps for each bundle, and a troubleshooting table by error message.
The short procedure for a bundle in this repository:

1. Create `src_test/`. Add the `test="true"` classpath entry. Copy it from
   `com.kk.pde.ds.imp/.classpath`.
2. In the bundle's `pom.xml`, add the test dependencies that the bundle needs (no versions). List
   `maven-surefire-plugin`. Tier 1 is then complete.
3. Do this step only if the bundle has DS components or services to check:
   1. List `tycho-surefire-plugin`.
   2. Add the `target-platform-configuration` block from `com.kk.pde.ds.imp/pom.xml`.
   3. Add the `org.osgi.framework` test dependency.
   4. Write an `*IT` class.

To move an Eclipse PDE workspace into a Tycho build like this one, see
[`docs/pde-to-tycho-migration.md`](docs/pde-to-tycho-migration.md).

#### Gotchas

- **`-pl <module> -am` does not work.** Maven's `--also-make` follows *pom* dependencies.
  A bundle's dependencies are OSGi manifest headers, and the Maven reactor cannot see them. Thus
  `-am` builds only the parent and fails with `requires osgi.bundle ... but it could not
  be found`. Always list `com.kk.pde.ds.target` and the upstream bundles manually.
- **A `*Test` class never sees OSGi.** If a test calls `FrameworkUtil.getBundle(...)`,
  it must be an `*IT`. An `*IT` that only calls `new` starts a
  framework that it does not need. Make it a `*Test`.
- **Your local Maven can use a different JDK than `java -version` shows.** If `JAVA_HOME` is not set,
  Homebrew's `mvn` uses its own OpenJDK (26 at the time of writing).
  CI uses Temurin 21. For this reason, the parent pins ByteBuddy to `1.18.13-jdk5`. To do the same build as CI:
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify`.
- **`ERROR … Greet Service Health Check failed` lines in the log are expected.** Three
  `GreetHealthCheckTest` tests force the `catch` branch on purpose. Use the `Tests run:` line to count failures.
  Do not search the log for "ERROR".
- **The report for nested classes can be confusing.** Surefire gives all six `GreetHealthCheckTest`
  tests to the `GreetHealthCheckTest$WithAnnotations` report and shows `Tests run: 0` for
  the outer class. Use the total for the bundle (7 for `imp`).
- **The test libraries must never ship.** After a full build, `distribution/target/products/**`
  and `distribution/target/repository/plugins/` must not contain an `org.mockito.*`,
  `net.bytebuddy.*`, `org.objenesis` or `junit-*` bundle. `unzip -l` of a bundle jar
  must not show a `*Test.class` or `*IT.class`. If one appears, a test folder is in
  `build.properties`, or a test dependency does not have the `test` scope.

### 14. Security Posture

**What it shows:** documentation of a demo surface that has no authentication by design.
The HTTP endpoints on port 8080 (`/mcp`, `/llm/chat`, `ingest_documents`,
`http_fetch`) are **for localhost only**. [`SECURITY.md`](SECURITY.md) lists
each exposure and the checklist to complete before you bind the surface to an address other than localhost.
Read it before you deploy this repository to a public location. There are no commands to run.

---

## Project Structure

```
p.osgi.ds/
├── pom.xml                           # Parent POM (Tycho build, 19 modules)
├── mvnw, mvnw.cmd                    # Maven Wrapper (use these, not the system mvn)
├── .mvn/wrapper/                     # Wrapper config (pins Maven 3.9.16)
├── .github/workflows/build.yml       # CI: ./mvnw clean verify on Temurin 21
├── CLAUDE.md                         # IDE guidance
├── README.md                         # This file
├── SECURITY.md                       # Known exposures of the localhost HTTP surface
├── docs/                             # Design notes, how-to guides, references
├── settings/
│   └── mcp.json                      # MCP client config (copied into the product, not read by the app)
│
├── com.kk.pde.ds.target/             # Target Platform Definition
│   ├── pom.xml
│   └── com.kk.pde.ds.target.target   # Maven dependencies for OSGi
│
├── com.kk.pde.ds.api/                # Service API Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   └── src/com/kk/pde/ds/api/
│       └── IGreet.java               # Service interface
│
├── com.kk.pde.ds.imp/                # Service Implementation Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/                     # DS component descriptors
│   │   ├── com.kk.pde.ds.imp.Greet.xml
│   │   └── com.kk.pde.ds.imp.GreetHealthCheck.xml
│   ├── src/com/kk/pde/ds/imp/
│   │   ├── Greet.java                # IGreet implementation
│   │   └── GreetHealthCheck.java     # Custom health check
│   └── src_test/                     # GreetTest, GreetHealthCheckTest, GreetServiceIT
│
├── com.kk.pde.ds.app/                # Service Consumer Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   │   └── com.kk.pde.ds.app.App.xml
│   └── src/com/kk/pde/ds/app/
│       └── App.java                  # Consumer component (opens Clock + Chat windows)
│
├── com.kk.pde.ds.rest/               # REST API Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   │   └── com.kk.pde.ds.rest.GreetServlet.xml
│   └── src/com/kk/pde/ds/rest/
│       ├── GreetServlet.java         # HTTP Whiteboard servlet
│       └── GreetResponse.java        # Response DTO
│
├── com.kk.pde.ds.mcp.api/            # MCP Tool API Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── src/com/kk/pde/ds/mcp/api/
│   │   ├── IMcpTool.java             # Tool contract interface
│   │   ├── IMcpToolRegistry.java     # Tool registry interface
│   │   └── Json.java                 # Shared hand-rolled JSON parser
│   └── src_test/                     # JsonTest
│
├── com.kk.pde.ds.mcp.server/         # MCP Server Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/                     # DS component descriptors
│   └── src/com/kk/pde/ds/mcp/server/
│       ├── McpServlet.java           # JSON-RPC 2.0 HTTP endpoint
│       ├── McpToolRegistry.java      # Dynamic tool registry
│       └── tools/
│           ├── EchoTool.java         # echo
│           ├── GreetTool.java        # greet (uses IGreet)
│           ├── SystemInfoTool.java   # system_info
│           ├── CalculatorTool.java   # calculator
│           ├── DateTimeTool.java     # date_time
│           ├── BundleListTool.java   # bundle_list
│           └── HttpFetchTool.java    # http_fetch
│
├── com.kk.pde.ds.mcp.client/         # MCP Client Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/com.kk.pde.ds.mcp.client.McpClient.xml
│   └── src/com/kk/pde/ds/mcp/client/
│       └── McpClient.java            # Protocol test client
│
├── com.kk.pde.ds.mcp.llm/            # LLM Bridge Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   └── src/com/kk/pde/ds/mcp/llm/
│       ├── OpenRouterAgent.java      # Agent loop with tool calls
│       └── LlmChatServlet.java       # HTTP endpoint for LLM chat
│
├── com.kk.pde.ds.chatbot/            # Chatbot UI Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   ├── lib/                          # Embedded commonmark 0.21.0 (+ GFM tables)
│   └── src/com/kk/pde/ds/chatbot/
│       ├── ChatService.java          # DS component (history, model)
│       ├── ChatConfig.java           # settings/chatbot.properties
│       ├── ChatFrame.java            # Main window (JFrame)
│       ├── ChatPanel.java            # Message display (styled)
│       ├── InputPanel.java           # Text input + buttons
│       ├── SettingsDialog.java       # API key / model / base URL editor
│       ├── ModelFetcher.java         # /v1/models query (tool-capable models only)
│       ├── MarkdownStyler.java       # Markdown rendering of replies
│       └── DarkTheme.java            # Swing dark theme
│
├── com.kk.pde.ds.rag/                # Document Q&A (RAG) Bundle
│   ├── OSGI-INF/
│   └── src/com/kk/pde/ds/rag/
│       ├── api/                      # DocumentParser, TextChunker, EmbeddingClient, VectorStore, OcrEngine, ...
│       ├── internal/                 # Implementations (PDFBox parser, chunker, embeddings, OCR, ...)
│       └── tool/                     # DocumentSearchTool, IngestDocumentsTool
│
├── com.kk.pde.ds.ecf.api/            # IRemoteGreet contract
├── com.kk.pde.ds.ecf.host/           # RemoteGreetImpl, exported over ECF
├── com.kk.pde.ds.ecf.consumer/       # RemoteGreetConsumer, GreetCommand, EDEF file
│
├── com.kk.pde.ds.spike.api/          # Spike contract (DockLayout, ICatalogService, ...)
├── com.kk.pde.ds.spike.master/       # Spike master (MasterApp, CatalogServiceImpl) + src_test/
├── com.kk.pde.ds.spike.detail/       # Spike detail (DetailApp) + EDEF file
│
├── com.kk.pde.ds.feature/            # Feature (bundle grouping)
│   ├── pom.xml
│   └── feature.xml
│
├── fatjar/                           # Fat JAR Launcher (standalone, not in the reactor)
│   ├── pom.xml                       # Plain Maven (not Tycho)
│   ├── run.sh                        # Build + run convenience script
│   ├── src/assembly/fatjar.xml       # Assembly descriptor
│   └── src/main/java/.../Main.java   # Embedded Equinox launcher
│
├── tools/pde2tycho/                  # PDE-to-Tycho migration CLI (plain Maven, not in the reactor)
│
└── distribution/                     # Product & Repository
    ├── pom.xml
    ├── p2.product                    # Main product definition
    ├── com.kk.pde.ds.ecf.host.product
    ├── com.kk.pde.ds.ecf.consumer.product
    ├── com.kk.pde.ds.spike.master.product
    ├── com.kk.pde.ds.spike.detail.product
    ├── category.xml                  # p2 category structure
    ├── configuration/
    │   ├── config.ini                # OSGi framework config
    │   ├── logback.xml               # Logging configuration
    │   └── configs/                  # Health check configs
    │       └── *.cfg                 # 6 health check files
    └── scripts/                      # 10 launchers (.sh and .bat for each)
        ├── run.sh / run.bat                            # Main product
        ├── run-ecf-host.sh / run-ecf-host.bat
        ├── run-ecf-consumer.sh / run-ecf-consumer.bat
        ├── run-spike-master.sh / run-spike-master.bat
        └── run-spike-detail.sh / run-spike-detail.bat
```

---

## Build System (Maven Tycho)

### Parent POM Configuration

**File**: `pom.xml`

```xml
<properties>
    <tycho.version>4.0.13</tycho.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
</properties>
```

### Build Plugins

| Plugin | Purpose |
|--------|---------|
| `tycho-maven-plugin` | Enables OSGi/PDE build support |
| `target-platform-configuration` | Configures target platform and build environments |
| `tycho-compiler-plugin` | Compiles Java 8 bytecode |
| `maven-surefire-plugin` (3.5.6) | Runs tier-1 tests (`*Test`) in a plain JVM |
| `tycho-surefire-plugin` | Runs tier-2 tests (`*IT`) inside Equinox (`plugin-test`) |
| `tycho-p2-director-plugin` | Materializes the products (no archives) |
| `maven-antrun-plugin` | Post-processes the products (config fix, file copies) and builds the main product archives |

### Build Environments

```xml
<environments>
    <environment>
        <os>linux</os><ws>gtk</ws><arch>x86_64</arch>
    </environment>
    <environment>
        <os>win32</os><ws>win32</ws><arch>x86_64</arch>
    </environment>
    <environment>
        <os>macosx</os><ws>cocoa</ws><arch>x86_64</arch>
    </environment>
</environments>
```

### Build Commands

Use `mvnw.cmd` instead of `./mvnw` on Windows.

```bash
# Full build with tests and verification
./mvnw clean verify

# Quick build (no tests)
./mvnw clean package -DskipTests

# Build specific module (always list the target module and the OSGi upstream modules)
./mvnw clean package -pl com.kk.pde.ds.target,com.kk.pde.ds.api

# Resume failed build from distribution
./mvnw verify -rf :distribution

# Build with debug output
./mvnw clean verify -X
```

### Build Outputs

| Output | Location |
|--------|----------|
| **p2 Repository** | `distribution/target/repository/` |
| **Linux Archive** | `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz` |
| **Windows Archive** | `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip` |
| **macOS Archive** | `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz` |

Only the main product has archives. The ECF and spike products stay as materialized
directories under `distribution/target/products/`. Their run scripts start them from there.

---

## Target Platform

**File**: `com.kk.pde.ds.target/com.kk.pde.ds.target.target`

The target platform defines all OSGi bundles that are available to the build and the runtime.
It has only Maven locations (no p2 repository URLs). It lists each bundle explicitly,
because Maven cannot see OSGi `Require-Bundle` and `Import-Package` requirements.
The tables below show each group. The `.target` file is the source of truth.

#### OSGi Core & Runtime
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.eclipse.osgi` | 3.23.0 | OSGi Framework (Equinox) |
| `org.eclipse.equinox.console` | 1.4.800 | Console support |
| `org.apache.felix.scr` | 2.2.12 | Declarative Services runtime |

#### Felix Gogo Shell
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.felix.gogo.runtime` | 1.1.6 | Shell runtime |
| `org.apache.felix.gogo.command` | 1.1.2 | Shell commands |
| `org.apache.felix.gogo.shell` | 1.1.4 | Interactive shell |

#### HTTP Service
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.felix.http.jetty` | 4.2.26 | Embedded Jetty server |
| `org.apache.felix.http.servlet-api` | 1.2.0 | Servlet API |

#### WebConsole
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.felix.webconsole` | 4.3.16 | Web-based administration |
| `org.apache.felix.webconsole.plugins.ds` | 2.1.0 | DS component viewer |
| `org.apache.felix.inventory` | 1.1.0 | Inventory support |

#### Configuration
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.felix.configadmin` | 1.9.26 | Configuration Admin |
| `org.apache.felix.metatype` | 1.2.4 | Metatype service |
| `org.apache.felix.fileinstall` | 3.7.4 | Auto-load configurations |

#### Health Checks
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.felix.healthcheck.api` | 2.0.4 | Health Check API |
| `org.apache.felix.healthcheck.core` | 2.0.8 | Core implementation |
| `org.apache.felix.healthcheck.generalchecks` | 2.0.6 | Built-in check types |
| `org.apache.felix.healthcheck.webconsoleplugin` | 2.0.2 | WebConsole integration |

#### Events
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.osgi.service.event` | 1.4.1 | Event API |
| `org.apache.felix.eventadmin` | 1.6.4 | Event Admin implementation |

#### OSGi Services
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.osgi.service.component` | 1.5.1 | DS service interfaces |
| `org.osgi.service.component.annotations` | 1.5.1 | DS annotations |
| `org.osgi.util.function` | 1.2.0 | Functional interfaces |
| `org.osgi.util.promise` | 1.3.0 | Promise API |

#### Logging
| Bundle | Version | Purpose |
|--------|---------|---------|
| `ch.qos.logback:logback-classic` | 1.2.11 | Logging (with transitive deps) |

#### Utilities
| Bundle | Version | Purpose |
|--------|---------|---------|
| `commons-fileupload` | 1.5 | File upload support |
| `commons-io` | 2.16.1 | IO utilities |

#### Document Parsing (RAG)
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.pdfbox:pdfbox` | 2.0.32 | PDF text extraction and rasterization |
| `org.apache.pdfbox:fontbox` | 2.0.32 | Font support for PDFBox |
| `commons-logging` | 1.3.5 | Logging backend for PDFBox (JCL, not slf4j) |

#### ECF Remote Services (3.14.x line)
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.eclipse.ecf` | 3.10.0 | ECF core |
| `org.eclipse.ecf.identity` | 3.9.402 | Identity namespaces |
| `org.eclipse.ecf.remoteservice` | 8.14.0 | Remote service API |
| `org.eclipse.ecf.remoteservice.asyncproxy` | 2.1.200 | Asynchronous proxies |
| `org.eclipse.ecf.discovery` | 5.1.1 | Discovery API |
| `org.eclipse.ecf.sharedobject` | 2.6.200 | Generic provider transport |
| `org.eclipse.ecf.provider` | 4.9.1 | Generic provider |
| `org.eclipse.ecf.provider.remoteservice` | 4.6.1 | Generic remote service provider |
| `org.eclipse.ecf.osgi.services.remoteserviceadmin` | 4.9.1 | Remote Service Admin (RSA) |
| `org.eclipse.ecf.osgi.services.remoteserviceadmin.proxy` | 1.0.101 | RSA proxies |
| `org.eclipse.ecf.osgi.services.distribution` | 2.1.600 | Topology manager |
| `org.osgi.service.remoteserviceadmin` | 1.1.0 | OSGi RSA API |
| `org.eclipse.equinox.common` | 3.20.100 | Required by ECF |
| `org.eclipse.equinox.registry` | 3.12.400 | Required by ECF |
| `org.eclipse.equinox.concurrent` | 1.3.300 | Required by ECF |
| `org.eclipse.core.jobs` | 3.15.600 | Required by ECF |

#### Test Harness (tier-2 tests only, never shipped)
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.eclipse.core.runtime` | 3.33.0 | Runtime for `tycho-surefire` (with its transitive closure) |
| `org.eclipse.equinox.launcher` | 1.6.1000 | Starts the tier-2 test runtime |

The test libraries (JUnit, Mockito, ByteBuddy) are not in the target platform. They are test-scope dependencies in the poms.

---

## Declarative Services (DS)

OSGi Declarative Services gives dependency injection for OSGi services.

### DS Annotations Used

| Annotation | Purpose |
|------------|---------|
| `@Component` | Declares a class as a DS component |
| `@Reference` | Injects a service dependency |
| `@Activate` | Lifecycle method that SCR calls when it activates the component |
| `@Deactivate` | Lifecycle method that SCR calls when it deactivates the component |

### Components Overview

#### 1. IGreet Interface (API)

**File**: `com.kk.pde.ds.api/src/com/kk/pde/ds/api/IGreet.java`

```java
package com.kk.pde.ds.api;

public interface IGreet {
    void greet();
}
```

#### 2. Greet Implementation (Provider)

**File**: `com.kk.pde.ds.imp/src/com/kk/pde/ds/imp/Greet.java`

```java
@Component
public class Greet implements IGreet {
    private static final Logger log = LoggerFactory.getLogger(Greet.class);

    @Activate
    public void start() {
        log.info("Greet.start()");
    }

    @Override
    public void greet() {
        log.info(greeting());
    }

    public String greeting() {
        return "Hello world!";
    }
}
```

**DS Descriptor**: `OSGI-INF/com.kk.pde.ds.imp.Greet.xml`
- Registers `Greet` as a service for `com.kk.pde.ds.api.IGreet`

#### 3. App Consumer

**File**: `com.kk.pde.ds.app/src/com/kk/pde/ds/app/App.java` (abbreviated)

```java
@Component
public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private IGreet greet;
    private ChatService chatService;
    private volatile ClockFrame clockFrame;
    private volatile ChatFrame chatFrame;

    @Reference
    public void setChatService(ChatService chatService) {
        log.info("App.setChatService()");
        this.chatService = chatService;
    }

    @Reference
    public void setApi(IGreet greet) {
        log.info("App.setApi()");
        this.greet = greet;
    }

    @Activate
    public void start() {
        log.info("App.start()");
        greet.greet();                       // Use injected service
        SwingUtilities.invokeLater(() -> {   // Open the Clock and Chat windows
            DarkTheme.apply();
            clockFrame = new ClockFrame();
            clockFrame.setVisible(true);
            chatFrame = new ChatFrame(chatService);
            chatFrame.setVisible(true);
        });
    }

    @Deactivate
    public void stop() {
        log.info("App.stop()");
        // Dispose the Clock and Chat windows on the EDT
    }
}
```

**DS Descriptor**: `OSGI-INF/com.kk.pde.ds.app.App.xml`
- References `IGreet` through the `setApi` bind method
- References `ChatService` through the `setChatService` bind method
- `activate="start"` and `deactivate="stop"`. When you stop the app bundle, `stop()` closes the Clock and Chat windows

#### 4. GreetServlet (HTTP Whiteboard)

**File**: `com.kk.pde.ds.rest/src/com/kk/pde/ds/rest/GreetServlet.java`

```java
@Component(
    service = Servlet.class,
    property = {
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/api/greet/*",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=GreetServlet"
    }
)
public class GreetServlet extends HttpServlet {
    private IGreet greetService;

    @Reference
    public void setGreetService(IGreet service) {
        greetService = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) { ... }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) { ... }
}
```

#### 5. GreetHealthCheck

**File**: `com.kk.pde.ds.imp/src/com/kk/pde/ds/imp/GreetHealthCheck.java`

```java
@Component(
    service = HealthCheck.class,
    property = {
        HealthCheck.NAME + "=Greet Service Health Check",
        HealthCheck.TAGS + "=application,greet",
        HealthCheck.MBEAN_NAME + "=greetServiceHealthCheck"
    }
)
public class GreetHealthCheck implements HealthCheck {

    @Reference
    private IGreet greetService;

    @Override
    public Result execute() {
        FormattingResultLog resultLog = new FormattingResultLog();
        try {
            // greetService is a mandatory (1..1) static reference — SCR guarantees it is
            // non-null while this component is active, so no null check is needed here.
            greetService.greet();
            resultLog.info("IGreet service is available and operational");
            resultLog.info("Service implementation: {}", greetService.getClass().getName());
        } catch (Exception e) {
            log.error("Greet Service Health Check failed", e);
            resultLog.critical("IGreet service invocation failed: {}", e.getMessage());
        }
        return new Result(resultLog);
    }
}
```

### Service Binding Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        OSGi Framework Startup                        │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  1. Greet component activates                                        │
│     - Registers as IGreet service                                    │
│     - @Activate start() called                                       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  2. App component references satisfied (IGreet AND ChatService)      │
│     - setApi(IGreet) called with Greet instance                      │
│     - setChatService(ChatService) called                             │
│     - @Activate start() called                                       │
│     - Calls greet.greet() → "Hello world!"                           │
│     - Opens the Clock and Chat windows on the EDT                    │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  3. GreetServlet references satisfied                                │
│     - setGreetService(IGreet) called                                 │
│     - Registered at /api/greet/* via HTTP Whiteboard                 │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  4. GreetHealthCheck references satisfied                            │
│     - IGreet injected via @Reference field                           │
│     - Available for health check execution                           │
└─────────────────────────────────────────────────────────────────────┘
```

For the full wiring of all components, see [`docs/architecture.md`](docs/architecture.md).

---

## HTTP Whiteboard (REST API)

The REST API uses the **OSGi HTTP Whiteboard** pattern (not JAX-RS).

### Base Configuration

| Setting | Value |
|---------|-------|
| **Base URL** | `http://localhost:8080/api/greet` |
| **Servlet Pattern** | `/api/greet/*` |
| **HTTP Port** | 8080 (configured in `config.ini`) |

### Endpoints

#### GET /api/greet
Returns a JSON greeting message.

```bash
curl http://localhost:8080/api/greet
```

**Response:**
```json
{"message":"Hello from OSGi HTTP Whiteboard!","timestamp":1737734567890}
```

#### GET /api/greet/{name}
Returns a personalized greeting.

```bash
curl http://localhost:8080/api/greet/World
```

**Response:**
```json
{"message":"Hello, World!","timestamp":1737734567890}
```

#### POST /api/greet
Echoes the posted message.

```bash
curl -X POST http://localhost:8080/api/greet -d '{"message":"Test"}'
```

**Response:**
```json
{"message":"Echo: {\"message\":\"Test\"}","timestamp":1737734567890}
```

### Limits and Errors

- The POST body has a limit of 64 KB. A larger body gets HTTP `413` ("Request too large").
- If the `IGreet` call fails, the servlet returns HTTP `500` with a JSON body: `{"error":"<message>"}`.

### HTTP Whiteboard Registration

```java
@Component(
    service = Servlet.class,
    property = {
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/api/greet/*",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=GreetServlet"
    }
)
public class GreetServlet extends HttpServlet { ... }
```

---

## Felix WebConsole

### Access

| Setting | Value |
|---------|-------|
| **URL** | http://localhost:8080/system/console |
| **Username** | `admin` |
| **Password** | `admin` |

### Configuration (config.ini)

```properties
felix.webconsole.username=admin
felix.webconsole.password=admin
```

### Available Tabs

| Tab | Description |
|-----|-------------|
| **Bundles** | List all bundles with states (INSTALLED, RESOLVED, ACTIVE) |
| **Services** | View registered OSGi services |
| **Components** | DS components via DS plugin (shows all @Component definitions) |
| **Health Check** | Health check results via health check WebConsole plugin |
| **Configuration** | View/edit OSGi configurations |

### Included WebConsole Bundles

- `org.apache.felix.webconsole:4.3.16`
- `org.apache.felix.webconsole.plugins.ds:2.1.0` (DS plugin)
- `org.apache.felix.inventory:1.1.0`

---

## Health Checks

### Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Health Check Framework                        │
├─────────────────────────────────────────────────────────────────────┤
│  API: org.apache.felix.healthcheck.api (2.0.4)                      │
│  Core: org.apache.felix.healthcheck.core (2.0.8)                    │
│  General Checks: org.apache.felix.healthcheck.generalchecks (2.0.6) │
│  WebConsole Plugin: org.apache.felix.healthcheck.webconsoleplugin   │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Health Check Types                            │
├─────────────────────────────────────────────────────────────────────┤
│  • Custom: GreetHealthCheck (code-based)                            │
│  • File-Based: 6 configuration files (loaded by File Install)       │
└─────────────────────────────────────────────────────────────────────┘
```

### Custom Health Check: GreetHealthCheck

**File**: `com.kk.pde.ds.imp/src/com/kk/pde/ds/imp/GreetHealthCheck.java`

| Property | Value |
|----------|-------|
| **Name** | Greet Service Health Check |
| **Tags** | application, greet |
| **MBean Name** | greetServiceHealthCheck |

**Behavior:**
1. The component has a mandatory, static (1..1) reference to `IGreet`. If no `IGreet` service is available, the component is unsatisfied and SCR does not register the check.
2. When the check runs, it calls `greet()`.
3. The result is OK if the call is successful. The result is CRITICAL only if `greet()` throws an exception.

### File-Based Health Checks

All configurations are in `distribution/configuration/configs/`. Felix File Install loads them.

#### 1. Application Bundles Check

**File**: `org.apache.felix.hc.generalchecks.BundlesStartedCheck-app.cfg`

```properties
hc.name=Application Bundles Check
hc.tags=application,bundles
bundles.regex=com.kk.pde.ds.api|com.kk.pde.ds.imp|com.kk.pde.ds.app|com.kk.pde.ds.rest
```

Checks that the listed application bundles are ACTIVE.

#### 2. Core Bundles Check

**File**: `org.apache.felix.hc.generalchecks.BundlesStartedCheck-core.cfg`

```properties
hc.name=Core Bundles Check
hc.tags=system,bundles
bundles.regex=org.apache.felix.scr|org.apache.felix.configadmin|org.apache.felix.eventadmin|org.apache.felix.http.jetty
```

Checks that the essential OSGi infrastructure bundles are ACTIVE.

#### 3. DS Components Check

**File**: `org.apache.felix.hc.generalchecks.DsComponentsCheck-app.cfg`

```properties
hc.name=DS Components Check
hc.tags=application,components
components.regex=com.kk.pde.ds.*
```

Checks that all application DS components are satisfied.

#### 4. REST API Check

**File**: `org.apache.felix.hc.generalchecks.HttpRequestsCheck-api.cfg`

```properties
hc.name=REST API Check
hc.tags=application,http
requests.json=["http://localhost:8080/api/greet"]
connectTimeoutInMs=3000
readTimeoutInMs=5000
```

Checks that the REST endpoint is accessible and responds.

#### 5. Greet Service Available

**File**: `org.apache.felix.hc.generalchecks.ServicesCheck-greet.cfg`

```properties
hc.name=Greet Service Available
hc.tags=application,services
services.list=com.kk.pde.ds.api.IGreet
```

Checks that the `IGreet` service is registered.

#### 6. Health Check Services

**File**: `org.apache.felix.hc.generalchecks.ServicesCheck-healthcheck.cfg`

```properties
hc.name=Health Check Services
hc.tags=system,services
services.list=org.apache.felix.hc.api.HealthCheck,org.osgi.service.event.EventAdmin
```

Checks that the health check infrastructure operates.

### Health Check Summary

| Check | Type | Tags | Purpose |
|-------|------|------|---------|
| Greet Service Health Check | Custom | application, greet | Checks that the IGreet service works |
| Application Bundles Check | BundlesStarted | application, bundles | App bundles ACTIVE |
| Core Bundles Check | BundlesStarted | system, bundles | System bundles ACTIVE |
| DS Components Check | DsComponents | application, components | Components satisfied |
| REST API Check | HttpRequests | application, http | REST endpoint accessible |
| Greet Service Available | Services | application, services | IGreet registered |
| Health Check Services | Services | system, services | HC infrastructure operational |

---

## MCP Server (Model Context Protocol)

The MCP bundles make a tool-call server that follows the [Model Context Protocol](https://modelcontextprotocol.io/) specification (JSON-RPC 2.0 over HTTP).

### Architecture

```
IMcpTool (service interface)
    ├── echo, greet, system_info,          ─┐
    │   calculator, date_time,              │  com.kk.pde.ds.mcp.server/.../tools/
    │   bundle_list, http_fetch             ├── Register via @Reference (dynamic, 0..n)
    └── document_search, ingest_documents  ─┘  com.kk.pde.ds.rag/.../tool/
            │
    McpToolRegistry (collects all IMcpTool services)
            │
    McpServlet (/mcp endpoint, JSON-RPC 2.0)
```

### Built-in Tools

| Tool | Bundle | Description | Input |
|------|--------|-------------|-------|
| `echo` | mcp.server | Echoes a message back | `{"message": "text"}` (required) |
| `greet` | mcp.server | Calls the IGreet service | `{"name": "optional"}` |
| `system_info` | mcp.server | Returns JVM/OS info | `{}` |
| `calculator` | mcp.server | Adds, subtracts, multiplies or divides two numbers | `{"a": "1", "b": "2", "operation": "add"}` (all required) |
| `date_time` | mcp.server | Returns the current date, time and timezone | `{"format": "optional SimpleDateFormat pattern"}` |
| `bundle_list` | mcp.server | Lists the installed OSGi bundles and their states | `{"filter": "optional substring"}` |
| `http_fetch` | mcp.server | Sends a request to an HTTP or HTTPS URL and returns the body (text, max 4 KB) | `{"url": "https://..."}` (required) |
| `document_search` | rag | Searches the ingested documents | `{"query": "text", "top_k": 5}` (`query` required) |
| `ingest_documents` | rag | Loads a file or folder into the knowledge base | `{"path": "/absolute/path"}` (required) |

### Request Limits

The `/mcp` endpoint accepts a request body of 1 MB or less. A larger body gets HTTP `413` and a JSON-RPC error (`-32600`, "Request too large").

### MCP Client

`com.kk.pde.ds.mcp.client` contains `McpClient`, a DS component. It reads the server URL from `-Dmcp.server.url`
(default `http://localhost:8080/mcp`). It starts on a daemon thread 3 seconds after activation. It sends `initialize`,
the `notifications/initialized` notification and `tools/list`. Then it calls only `echo`, `greet` and `system_info`, and logs the results.

### settings/mcp.json

`settings/mcp.json` is a configuration file for an MCP client. It sets `mcpServers.osgi-mcp.url` to `http://localhost:8080/mcp`.
The application does not read this file. `distribution/pom.xml` copies it into the product.
On Linux and Windows, it goes to `<product>/settings/`. On macOS, it goes to `macosx/cocoa/x86_64/settings`, outside `Eclipse.app/Contents/Eclipse`.

### Adding Custom Tools

Create a new `@Component` that implements `IMcpTool`, in any bundle. The dynamic `@Reference` in `McpToolRegistry` finds it automatically.

---

## LLM Integration (OpenRouter / OpenAI-Compatible)

The `com.kk.pde.ds.mcp.llm` bundle connects the MCP tool registry to OpenAI-compatible APIs.

### Agent Loop

`OpenRouterAgent` has a tool-calling agent loop:
1. It sends the user message and the available tools to the LLM.
2. If the LLM responds with `tool_calls`, the agent runs each tool through the OSGi registry and sends the result back.
3. If the LLM responds with `stop`, the agent returns the final text answer.
4. The loop repeats for a maximum of 10 turns.

The agent adds a **References** footer to each final answer. The footer lists the source document names
from the `document_search` results, or "none" when the answer used no documents. The agent adds the footer
to the returned text only, not to the conversation history.

### HTTP Endpoint

`POST /llm/chat` accepts JSON with a required `message` field and an optional `model` field. The `model` field overrides the default model.
The request body has a limit of 1 MB. A larger body gets HTTP `413`.

```bash
# Send a chat message via HTTP
curl -X POST http://localhost:8080/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What tools do you have?", "model": "google/gemini-flash-1.5"}'
```

---

## Chatbot UI

The `com.kk.pde.ds.chatbot` bundle has a Swing desktop chat interface.

### Features

- Conversation with more than one turn and a session history
- Model selection through an editable list. The list comes from the `/v1/models` endpoint and contains only models that support tools
- "Refresh Models" button to load the model list again
- "Clear" button to delete the conversation history
- "Settings" dialog to edit the API key, model and base URL, with a "Test Connection" button
- Works with all OpenAI-compatible APIs (OpenRouter, LM Studio, and others)
- MCP tool integration: the LLM can use registered tools during conversations
- Markdown in replies (embedded commonmark 0.21.0 with GFM tables)
- Non-blocking UI through `SwingWorker`. The UI continues to respond during LLM calls

### Configuration

`ChatConfig` reads `settings/chatbot.properties`, relative to the working directory. The run scripts use the product
directory as the working directory. If the file does not exist, `ChatConfig` creates it with empty values. The Settings dialog edits this file.

For each setting, the first non-empty value in this order applies:

1. The value in `settings/chatbot.properties`
2. The system property (`-D`)
3. The environment variable (API key only)
4. The default in the code

| File key | System property | Environment variable | Default |
|----------|-----------------|----------------------|---------|
| `api.key` | `openrouter.api.key` | `OPENROUTER_API_KEY` | — |
| `model` | `openrouter.model` | — | `google/gemini-flash-1.5` |
| `base.url` | `openrouter.base.url` | — | `https://openrouter.ai/api/v1` |

The `/llm/chat` endpoint does not read `chatbot.properties`. It uses the system property, then the environment variable (API key only), then the default.

### Service Wiring

```
OpenRouterAgent (@Reference IMcpToolRegistry)
        │
ChatService (@Reference OpenRouterAgent)
        │
App (@Reference ChatService) → launches ChatFrame on EDT
```

---

## ECF Remote Services (OSGi RSA)

The `com.kk.pde.ds.ecf.*` bundles show **OSGi Remote Services** with the
Eclipse Communication Framework (ECF). One OSGi process exports a service. A DS `@Reference`
in a **separate JVM** uses it. The `@Reference` pattern is the same as in
the local demo, but the method call goes over a network socket.
For the full narrative, with the problems found on the way, see [`docs/ecf-remote-services.md`](docs/ecf-remote-services.md).

### Bundles

| Bundle | Role |
|--------|------|
| `com.kk.pde.ds.ecf.api` | `IRemoteGreet { String greet(String name); }` — shared contract |
| `com.kk.pde.ds.ecf.host` | `RemoteGreetImpl`, a `@Component` exported as a remote service |
| `com.kk.pde.ds.ecf.consumer` | `RemoteGreetConsumer` (`@Reference`) + Gogo command `ecf:greet` + EDEF discovery file |

### How export/import works

The host exports the service only through DS component properties (no ECF code):

```java
@Component(property = {
    "service.exported.interfaces=*",
    "service.exported.configs=ecf.generic.server",
    "ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server"
})
public class RemoteGreetImpl implements IRemoteGreet { ... }
```

The consumer finds the endpoint through a static **EDEF** file. Its
manifest refers to the file (`Remote-Service: OSGI-INF/remote-service/*.xml`). There is no ZooKeeper or mDNS daemon.
ECF's Remote Service Admin reads the file, connects, and registers a proxy `IRemoteGreet`.
The `@Reference` binds to that proxy.

### Run it (two terminals)

```bash
./mvnw clean verify
# Terminal 1 — host (exports IRemoteGreet on ecftcp://localhost:3288/server)
./distribution/scripts/run-ecf-host.sh
# Terminal 2 — consumer (imports via EDEF, invokes greet("ECF"))
./distribution/scripts/run-ecf-consumer.sh
```

The consumer logs `Remote response: Hello, ECF! (served remotely by host)`. At its
`osgi>` prompt, `ecf:greet World` calls the remote service again.

### Notes

- The project uses **ECF 3.14.x**, the last line with `JavaSE-1.8` BREEs. The bundles come from
  Maven Central under groupId `org.eclipse.ecf`.
- The target platform lists the full ECF bundle closure **explicitly**. Maven's
  transitive resolver cannot see OSGi `Require-Bundle` and `Import-Package` requirements.
- Maven builds the two products from `distribution/com.kk.pde.ds.ecf.{host,consumer}.product`.
  They do not contain Felix HTTP or WebConsole, so the two JVMs do not both bind port 8080.

---

## File-Based Configuration

### Felix File Install

**Configuration** (`config.ini`):

```properties
felix.fileinstall.dir=configuration/configs
felix.fileinstall.poll=5000
felix.fileinstall.log.level=3
felix.fileinstall.noInitialDelay=true
```

| Setting | Value | Description |
|---------|-------|-------------|
| `dir` | configuration/configs | Directory to watch for `.cfg` files |
| `poll` | 5000 | Poll interval in milliseconds |
| `log.level` | 3 | Log level (DEBUG) |
| `noInitialDelay` | true | Process files immediately on startup |

### How It Works

1. File Install watches the `configuration/configs/` directory.
2. At startup and every 5 seconds, it scans for `.cfg` files.
3. It creates or updates OSGi configurations through Config Admin.
4. The health check bundles receive the configurations and create the checks.

### Adding New Health Checks

1. Create a new `.cfg` file in `distribution/configuration/configs/`.
2. Use the factory PID name format: `<factory.pid>-<instance>.cfg`.
3. Rebuild the product. Or copy the file directly into `configuration/configs/` of the running product.

Example for a new bundle check:
```properties
# org.apache.felix.hc.generalchecks.BundlesStartedCheck-custom.cfg
hc.name=Custom Bundle Check
hc.tags=custom
bundles.regex=my.custom.bundle
```

---

## Logging

### Configuration

**File**: `distribution/configuration/logback.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Set default logging level to INFO -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>

    <!-- Application packages at INFO level -->
    <logger name="com.kk.pde.ds" level="INFO" />

    <!-- Felix and OSGi at WARN to reduce noise -->
    <logger name="org.apache.felix" level="WARN" />
    <logger name="org.eclipse.jetty" level="WARN" />
</configuration>
```

### Log Levels

| Logger | Level | Purpose |
|--------|-------|---------|
| Root | INFO | Default for all loggers |
| `com.kk.pde.ds.*` | INFO | Application packages |
| `org.apache.felix.*` | WARN | Reduce Felix noise |
| `org.eclipse.jetty.*` | WARN | Reduce Jetty noise |

### Log Format

```
HH:mm:ss.SSS [thread] LEVEL logger - message
```

Example lines (the time and thread names change from run to run):
```
14:30:15.123 [main] INFO  com.kk.pde.ds.imp.Greet - Greet.start()
14:30:15.125 [main] INFO  com.kk.pde.ds.app.App - App.start()
14:30:15.126 [main] INFO  com.kk.pde.ds.imp.Greet - Hello world!
```

### Changing Log Levels

Edit `logback.xml` in the `configuration/` directory of the product:

```xml
<!-- Enable DEBUG for application -->
<logger name="com.kk.pde.ds" level="DEBUG" />

<!-- Enable DEBUG for specific class -->
<logger name="com.kk.pde.ds.imp.GreetHealthCheck" level="DEBUG" />
```

---

## Product Definition

**File**: `distribution/p2.product`

### Product Configuration

| Setting | Value |
|---------|-------|
| **UID** | `com.kk.pde.ds.product` |
| **Version** | `1.0.0.qualifier` |
| **Type** | bundles |
| **Include Launchers** | false (uses Equinox launcher) |
| **Auto Include Requirements** | true |

### Launcher Arguments

```xml
<launcherArgs>
    <programArgs>-consoleLog</programArgs>
    <vmArgs>-Declipse.ignoreApp=true -Dosgi.noShutdown=true
            -Dlogback.configurationFile=configuration/logback.xml</vmArgs>
    <vmArgsMac>-XstartOnFirstThread</vmArgsMac>
</launcherArgs>
```

### Bundle Start Levels

| Level | Bundles | Purpose |
|-------|---------|---------|
| **0** | http.jetty, http.servlet-api, webconsole, webconsole.plugins.ds, inventory, osgi.service.servlet | HTTP infrastructure |
| **1** | osgi, configadmin, metatype | Core framework |
| **2** | scr, service.component, service.component.annotations, util.function, util.promise, fileinstall, service.event, eventadmin, healthcheck.api, healthcheck.core | Services |
| **3** | gogo.*, equinox.console, healthcheck.generalchecks, healthcheck.webconsoleplugin | Console & plugins |
| **4** | api, imp, app, rest, mcp.api, mcp.server, mcp.client, mcp.llm, chatbot, rag | Application bundles (auto-start) |
| **4** | pdfbox, fontbox, commons-logging | RAG libraries (no auto-start) |

### Included Plugins

`p2.product` lists the plugins below. Because `autoIncludeRequirements="true"`, Tycho also adds their
requirements (for example Logback and slf4j) to the built product, so the `plugins/` directory contains more jars than the list.

**Application (10)**:
- `com.kk.pde.ds.api`, `com.kk.pde.ds.imp`, `com.kk.pde.ds.app`, `com.kk.pde.ds.rest`
- `com.kk.pde.ds.mcp.api`, `com.kk.pde.ds.mcp.server`, `com.kk.pde.ds.mcp.client`, `com.kk.pde.ds.mcp.llm`
- `com.kk.pde.ds.chatbot`, `com.kk.pde.ds.rag`

**RAG libraries**: `org.apache.pdfbox`, `org.apache.pdfbox.fontbox`, `org.apache.commons.commons-logging`

**Framework**:
- OSGi: `org.eclipse.osgi`, `org.eclipse.equinox.console`
- Felix: scr, gogo.*, http.*, webconsole.*, configadmin, metatype, fileinstall, eventadmin, healthcheck.*, inventory
- OSGi Services: service.component, service.component.annotations, util.function, util.promise, service.event
- Apache Commons: commons-fileupload, commons-io

---

## Running the Application

> **Java requirement**: Java 8 or newer to run the product. JDK 17 or newer to build it.

### Method 1: Run Scripts from Source Tree (Recommended)

After `./mvnw clean verify`, run the launcher script from the repository root. The script finds your operating system and the built product directory for it. You do not need to extract an archive.

**macOS / Linux:**
```bash
# Set API key for chatbot/LLM features (optional but recommended)
export OPENROUTER_API_KEY=your_key_here

./distribution/scripts/run.sh
```

**Windows:**
```cmd
set OPENROUTER_API_KEY=your_key_here
distribution\scripts\run.bat
```

### Method 2: Run Scripts from Inside the Product

The build copies the script into each product. The script finds `plugins/` next to it. Linux and macOS products get only `run.sh`. Windows products get only `run.bat`.

**Linux product:**
```bash
cd distribution/target/products/com.kk.pde.ds.product/linux/gtk/x86_64
./run.sh
```

**macOS product** (the macOS build puts the product inside an `.app` bundle):
```bash
cd distribution/target/products/com.kk.pde.ds.product/macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse
./run.sh
```

**Windows product:**
```cmd
cd distribution\target\products\com.kk.pde.ds.product\win32\win32\x86_64
run.bat
```

### Method 3: Manual Start

Run this command from inside a product directory (the directory that contains `plugins/` and `configuration/`):

```bash
java -jar plugins/org.eclipse.osgi_*.jar \
     -configuration configuration \
     -console \
     -consoleLog
```

**Important**: The `-configuration configuration` flag is **necessary**. It tells the OSGi framework where `config.ini` is.

### Run Script Details

The two scripts (`distribution/scripts/run.sh` and `run.bat`) do these steps:

1. If `plugins/` is next to the script, the script runs from inside a product. It uses the script directory as the product directory.
2. If not, the script runs from `distribution/scripts/` in the source tree. It uses the built product for the current operating system. `run.sh` supports macOS and Linux only.
3. It finds `plugins/org.eclipse.osgi_*.jar`. If there is no jar, it shows an error and tells you to build first.
4. It changes to the product directory and starts Java.

The Java command is the same in the two scripts:

```bash
# run.sh ("$@" carries your arguments; run.bat uses %*)
OFFLINE_XML="-Djavax.xml.accessExternalDTD= -Djavax.xml.accessExternalSchema= -Djavax.xml.accessExternalStylesheet="
java $OFFLINE_XML "$@" -jar "$OSGI_JAR" -configuration configuration -console -consoleLog
```

- The `OFFLINE_XML` flags stop the XML parsers from downloading external DTDs, schemas and stylesheets. Document parsing stays offline.
- The scripts put all your arguments **before** `-jar`. Thus `-D` flags and other JVM options get to the JVM.

### Passing Additional Arguments

```bash
# Pass custom system properties
./distribution/scripts/run.sh -Drag.docs.dir=/path/to/your/documents

# Pass JVM arguments
./distribution/scripts/run.sh -Xmx512m -Xms256m
```

### Product Directory Structure

Linux (`linux/gtk/x86_64/`) and Windows (`win32/win32/x86_64/`):

```
x86_64/
├── plugins/                    # OSGi bundles (org.eclipse.osgi_3.23.0.*.jar, ...)
├── configuration/              # Framework configuration
│   ├── config.ini              # OSGi config
│   ├── logback.xml             # Logging config
│   └── configs/                # Health check configs
│       └── *.cfg               # 6 health check files
├── settings/
│   └── mcp.json                # MCP client config (not read by the application)
├── p2/, artifacts.xml, eclipse.ini
└── run.sh                      # Linux only (Windows: run.bat)
```

macOS (`macosx/cocoa/x86_64/`):

```
x86_64/
├── Eclipse.app/Contents/Eclipse/
│   ├── plugins/
│   ├── configuration/          # config.ini, logback.xml, configs/*.cfg
│   └── run.sh
└── settings/
    └── mcp.json                # Outside Eclipse.app/Contents/Eclipse
```

The chatbot writes `settings/chatbot.properties` in the working directory. With `run.sh` on macOS, that is `Eclipse.app/Contents/Eclipse/settings/`.

---

## Fat JAR (Single Executable)

The `fatjar/` module puts the full application into one executable JAR: the Equinox framework,
all jars from the built product's `plugins/` directory, and the configuration. You do not need an installation or a product directory.

### How It Works

The fat JAR uses an **embedded Equinox that `Main` starts through reflection**:

1. `Main.java` extracts all bundle JARs from `bundles/` inside the fat JAR to a temporary directory.
2. It loads the Equinox framework JAR through a `URLClassLoader`. The OSGi classes stay off the application classpath.
3. It starts the framework with the `FrameworkFactory` SPI, through `ServiceLoader` and reflection.
4. It installs all bundles and sets the start levels from its own table (see below).
5. It starts the bundles, waits for shutdown, and deletes the temporary directory.

> **Why reflection?** If you unpack Equinox onto the flat classpath, the `org.osgi.framework.*` packages are on the application classpath and in the system bundle. This breaks the OSGi resolver. With reflection, `Main.java` has no OSGi code: there are no `org.osgi` imports.

**Start levels:** the start-level table in `Main.java` is different from `p2.product`.

- `Main` puts http.jetty, http.servlet-api, webconsole, webconsole.plugins.ds and inventory at level 4. The product puts them at level 0.
- `Main` has no entry for `com.kk.pde.ds.rag`, pdfbox, fontbox and commons-logging. These bundles keep the framework's default start level, and `Main` starts them. In the product, pdfbox, fontbox and commons-logging do not start automatically.
- `Main` installs logback, slf4j, commons-fileupload and commons-io at level 4 and does not start them.

### Build

The fat JAR is a **standalone Maven module** (not part of the Tycho reactor). Build the main project first. Then build the fat JAR:

```bash
# 1. Build all bundles + product
./mvnw clean verify

# 2. Build the fat JAR (collects bundles from the built product)
./mvnw -f fatjar clean package
```

Output: `fatjar/target/osgi-fatjar.jar`

### Run

```bash
# Basic
java -jar fatjar/target/osgi-fatjar.jar

# With API key for chatbot/LLM features
OPENROUTER_API_KEY=your_key java -jar fatjar/target/osgi-fatjar.jar

# With telnet console on port 5555
java -Dosgi.console.port=5555 -jar fatjar/target/osgi-fatjar.jar

# Or use the convenience script (builds if needed)
./fatjar/run.sh
```

### Services Available

The fat JAR gives the same services as the product build:

| Service | URL |
|---------|-----|
| REST API | http://localhost:8080/api/greet |
| WebConsole | http://localhost:8080/system/console (admin/admin) |
| MCP Server | http://localhost:8080/mcp |
| LLM Chat | http://localhost:8080/llm/chat (POST) |

### OSGi Console

By default, the Gogo shell console uses stdin and stdout. Use `ss` to list bundles and `scr:list` to examine DS components. For remote access, start with `-Dosgi.console.port=5555` and connect through telnet.

### Platform Notes

The same fat JAR runs on macOS, Linux and Windows with Java 8 or newer. The `pom.xml` has OS profiles that find the `plugins/` directory of the built product for the current operating system. The default is the macOS product.

---

## Remote Debugging

### Enable JDWP

Add the debug agent to the Java command:

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar plugins/org.eclipse.osgi_*.jar \
     -configuration configuration \
     -console \
     -consoleLog
```

**Parameters:**
| Parameter | Value | Description |
|-----------|-------|-------------|
| `transport` | dt_socket | Use socket transport |
| `server` | y | Act as debug server |
| `suspend` | n | Don't wait for debugger (use `y` to pause until attached) |
| `address` | 5005 | Debug port |

### Using Run Script with Debug

The run script gives your arguments to the JVM before `-jar`. Thus you can add the debug agent directly:

```bash
./distribution/scripts/run.sh -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```

### Connect from IDE

**IntelliJ IDEA:**
1. Run → Edit Configurations → + → Remote JVM Debug
2. Host: `localhost`
3. Port: `5005`
4. Click Debug

**Eclipse:**
1. Run → Debug Configurations → Remote Java Application → New
2. Host: `localhost`
3. Port: `5005`
4. Click Debug

**VS Code:**
Add to `launch.json`:
```json
{
    "type": "java",
    "name": "Attach to OSGi",
    "request": "attach",
    "hostName": "localhost",
    "port": 5005
}
```

---

## IDE Integration

### Eclipse PDE (Recommended for OSGi)

**Setup:**

1. **Import Projects**
   - File → Import → Existing Projects into Workspace
   - Select the repository root
   - Import all projects

2. **Configure Target Platform**
   - Open `com.kk.pde.ds.target/com.kk.pde.ds.target.target` in the Target Editor
   - Wait until Eclipse resolves the target
   - Click "Set as Active Target Platform"

3. **Create Run Configuration**
   - Run → Run Configurations → OSGi Framework → New
   - Select bundles to include
   - Program arguments: `-console -consoleLog`
   - VM arguments: `-Dlogback.configurationFile=distribution/configuration/logback.xml`

4. **Debug**
   - Use Debug on the run configuration
   - Or add the remote debug agent and attach

### IntelliJ IDEA

**Recommended procedure**: Run the built product and attach the remote debugger.

1. **Build**
   ```bash
   ./mvnw clean verify
   ```

2. **Run with Debug**
   ```bash
   ./distribution/scripts/run.sh -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
   ```

3. **Attach Debugger**
   - Run → Edit Configurations → Remote JVM Debug
   - Connect to `localhost:5005`

**Alternative: Direct Start**

1. Create a JAR Application configuration.
2. JAR path: `<platform dir>/plugins/org.eclipse.osgi_*.jar`
3. Program args: `-configuration configuration -console -consoleLog`
4. VM options: `-Dlogback.configurationFile=configuration/logback.xml`
5. Working directory: the platform directory of the product. This is `linux/gtk/x86_64`, `win32/win32/x86_64`, or `macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse` under `distribution/target/products/com.kk.pde.ds.product/`.

### VS Code

1. Install the Java Extension Pack.
2. Open the repository folder.
3. Build with the Maven Wrapper: `./mvnw clean verify`.
4. Attach the debugger with the `launch.json` configuration from [Remote Debugging](#remote-debugging).

---

## OSGi Console Commands

When you run with `-console -consoleLog`, you get the Gogo shell.

### Basic Commands

| Command | Description |
|---------|-------------|
| `lb` | List bundles with IDs and states |
| `lb -s` | List bundles sorted by state |
| `start <id>` | Start a bundle |
| `stop <id>` | Stop a bundle |
| `refresh <id>` | Refresh a bundle |
| `update <id>` | Update a bundle |
| `uninstall <id>` | Uninstall a bundle |
| `headers <id>` | Show bundle headers (MANIFEST.MF) |

### Service Commands

| Command | Description |
|---------|-------------|
| `services` | List all registered services |
| `services <filter>` | Filter services (LDAP filter) |

### DS (SCR) Commands

| Command | Description |
|---------|-------------|
| `scr:list` | List all DS components |
| `scr:info <component>` | Show component details |
| `scr:enable <component>` | Enable a component |
| `scr:disable <component>` | Disable a component |

### Example Session

The bundle IDs change with the bundle set. Use the IDs that `lb` shows.

```
osgi> lb
osgi> scr:list
osgi> scr:info com.kk.pde.ds.app.App
osgi> services "(objectClass=com.kk.pde.ds.api.IGreet)"
osgi> stop <id of com.kk.pde.ds.app>     # App.stop() closes the Clock and Chat windows
osgi> start <id of com.kk.pde.ds.app>    # App.start() opens them again
```

---

## Troubleshooting

### Bundle Not Starting

**Symptoms**: The bundle state is INSTALLED or RESOLVED, not ACTIVE

**Checks**:
1. Examine the dependencies: `headers <bundle-id>`.
2. Look for missing imports in the output.
3. Make sure that the target platform contains the necessary bundles.

### Service Not Available

**Symptoms**: A `@Reference` is not satisfied, and the component is not active

**Checks**:
1. Run `scr:list` to see the component states.
2. Run `scr:info <component>` to see the reference details.
3. Make sure that the bundle that supplies the service is ACTIVE.
4. Make sure that the service interface is exactly the same.

### WebConsole Not Accessible

**Symptoms**: You cannot connect to http://localhost:8080/system/console

**Checks**:
1. Make sure that `org.apache.felix.http.jetty` is ACTIVE.
2. Make sure that `config.ini` contains `org.osgi.service.http.port=8080`.
3. Make sure that `config.ini` contains `org.apache.felix.http.enable=true`.
4. Make sure that no other process uses port 8080.

### Health Checks Not Showing

**Symptoms**: The custom health checks do not appear in the WebConsole

**Checks**:
1. Make sure that `config.ini` contains `felix.fileinstall.dir=configuration/configs`.
2. Make sure that File Install is ACTIVE: `lb | grep fileinstall`.
3. Make sure that the `.cfg` files are in `configuration/configs/`.
4. Make sure that the file names use the format `<factory.pid>-<instance>.cfg`.

### Configuration Not Applied

**Symptoms**: Changes to `config.ini` or `logback.xml` have no effect

**Checks**:
1. Make sure that the command contains the `-configuration configuration` flag.
2. Make sure that the file is in the `configuration/` directory (not `config/`).
3. For Logback, make sure that the JVM gets `-Dlogback.configurationFile=configuration/logback.xml`.

### Common config.ini Issues

```bash
# CORRECT - Points to config directory
java -jar ... -configuration configuration ...

# WRONG - Missing flag or wrong path
java -jar ... -configuration config ...   # Wrong directory name
java -jar ... ...                         # Missing flag entirely
```

---

## File Reference

### Build Configuration

| File | Purpose |
|------|---------|
| `pom.xml` | Parent Maven Tycho POM |
| `*/pom.xml` | Module-specific POMs |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper (Maven 3.9.16) |
| `.github/workflows/build.yml` | CI workflow |
| `com.kk.pde.ds.target/*.target` | Target platform definition |

### Product Configuration

| File | Purpose |
|------|---------|
| `distribution/p2.product` | Main product definition with bundles and start levels |
| `distribution/com.kk.pde.ds.{ecf.host,ecf.consumer,spike.master,spike.detail}.product` | ECF and spike product definitions |
| `distribution/category.xml` | p2 repository category structure |
| `distribution/configuration/config.ini` | OSGi framework configuration |
| `distribution/configuration/logback.xml` | Logging configuration |
| `distribution/configuration/configs/*.cfg` | Health check configurations |

### Launcher Scripts

| File | Purpose |
|------|---------|
| `distribution/scripts/run.sh` | Linux/macOS launcher for the main product |
| `distribution/scripts/run.bat` | Windows launcher for the main product |
| `distribution/scripts/run-ecf-{host,consumer}.{sh,bat}` | ECF host and consumer launchers |
| `distribution/scripts/run-spike-{master,detail}.{sh,bat}` | Spike master and detail launchers |

### Application Sources

| File | Purpose |
|------|---------|
| `com.kk.pde.ds.api/src/.../IGreet.java` | Service interface |
| `com.kk.pde.ds.imp/src/.../Greet.java` | Service implementation |
| `com.kk.pde.ds.imp/src/.../GreetHealthCheck.java` | Custom health check |
| `com.kk.pde.ds.app/src/.../App.java` | Service consumer |
| `com.kk.pde.ds.rest/src/.../GreetServlet.java` | HTTP Whiteboard servlet |
| `com.kk.pde.ds.rest/src/.../GreetResponse.java` | Response DTO |

### OSGi Metadata

| File | Purpose |
|------|---------|
| `*/META-INF/MANIFEST.MF` | Bundle metadata (exports, imports, etc.) |
| `*/OSGI-INF/*.xml` | DS component descriptors |
| `com.kk.pde.ds.feature/feature.xml` | Feature definition |

### Runtime Settings

| File | Purpose |
|------|---------|
| `settings/mcp.json` | MCP client configuration (points to `http://localhost:8080/mcp`). The application does not read it |
| `<working dir>/settings/chatbot.properties` | Chatbot settings (API key, model, base URL), written by the Settings dialog |

---

## Statistics

| Metric | Count |
|--------|-------|
| Maven Modules | 19 in the root pom, 20 reactor projects with the parent (+ `fatjar` and `tools/pde2tycho`, built separately) |
| Application Bundles | 16 (api, imp, app, rest, mcp.api/server/client/llm, chatbot, rag, ecf.api/host/consumer, spike.api/master/detail) |
| Tests | 54 in 9 classes across 3 bundles: 48 plain-JVM (`*Test`, maven-surefire) + 6 in-framework (`*IT`, tycho-surefire plugin-test). JUnit 5 + Mockito |
| DS Components | 31 (`OSGI-INF/*.xml` descriptors) |
| MCP Tools | 9 (echo, greet, system_info, calculator, date_time, bundle_list, http_fetch + document_search, ingest_documents) |
| Health Checks | 7 (1 custom + 6 file-based) |
| Launcher Scripts | 10 (`run`, `run-ecf-host/consumer`, `run-spike-master/detail` × `.sh`/`.bat`) |
| Products | 5 (main, ecf.host, ecf.consumer, spike.master, spike.detail) for Linux, Windows, macOS |
| Start Levels | 5 (0-4) |
| Target Dependencies | 48 Maven coordinates in the `.target` file |

---

## License

[Add your license information here]

---

## Contributing

[Add contributing guidelines here]
