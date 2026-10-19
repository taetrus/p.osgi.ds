# CLAUDE.md

This file gives guidance to Claude Code (claude.ai/code) for the code in this repository.

## Project Overview

This project is an OSGi Declarative Services (DS) demo. Maven Tycho 4.0.13 builds it. It shows a
service-oriented architecture with separate API contracts, implementations and consumers. The
build makes a p2 repository and a product archive for each platform.

> **Security:** The HTTP surface (port 8080) has no authentication by design. Use it on **localhost only**. [`SECURITY.md`](SECURITY.md) lists the known exposures (`/mcp`, `/llm/chat`, `ingest_documents`, `http_fetch`). It also gives the checklist to complete before you expose any part of it outside localhost.

## Documentation Map

**Rule:** All documentation goes in `docs/`. Only `README.md`, `CLAUDE.md` and `SECURITY.md` stay
in the root. All documentation follows ASD-STE100 Simplified Technical English. Use the
`anthropic-skills:asd-ste100` skill and its `ste-lint.py` linter.

| File | Read it for |
|------|-------------|
| `README.md` | Full reference: run guide for each feature (macOS and Windows), REST, chatbot, RAG, OCR, tests, troubleshooting |
| `SECURITY.md` | Known exposures on the unauthenticated localhost HTTP surface |
| `docs/architecture.md` | Component and service wiring diagram |
| `docs/ecf-remote-services.md` | Narrative "how and why" of the ECF remote services, with the bugs and the lessons |
| `docs/rag-design.md` | Narrative "how and why" of RAG, with the bugs and the lessons |
| `docs/rag-reference.md` | RAG configuration reference and verification record |
| `docs/mcp-server-testing.md` | Manual JSON-RPC test procedure for the MCP server |
| `docs/spike-isolation-results.md`, `docs/multi-jvm-isolation-journey.md`, `docs/monolith-to-multi-jvm-migration.md` | The spike: multi-frame / multi-JVM UI design and findings |
| `docs/multi-jvm-isolation-journey.html` | Visual version of the journey, in Turkish. It has a chapter that the `.md` does not have (the tiled grid). |
| `docs/testing.md` | How to select tests, add them to a bundle, and run them (STE, with diagrams) |
| `docs/adding-tests-to-a-tycho-project.md` | How to apply the two-tier test layout to a different Tycho/PDE project (parent pom XML, steps for each bundle, troubleshooting by error message) |
| `docs/pde-to-tycho-migration.md` | How to migrate an Eclipse PDE workspace to Tycho with `tools/pde2tycho` (or by hand). Troubleshooting by error message. |
| `docs/claude-automation-recommendations.md` | Recommended Claude Code automations (hooks, subagents, skills, MCP servers) for this repository |
| `docs/superpowers/specs/` | Design specs written before implementation: ECF remote services, spike multi-frame grid, Mockito in the test suites, two-tier test layout migration, pde2tycho |
| `docs/superpowers/plans/` | Implementation plans: Swing chatbot bundle, fat JAR, pde2tycho |
| `.claude/skills/pde2tycho/` | Project skill: migrate a PDE workspace to Tycho with `tools/pde2tycho` |

## Build Commands

Use the Maven Wrapper `./mvnw` for all builds. It pins the Maven version that CI uses (see Conventions).

```bash
# Full build with tests (CI adds --batch-mode --no-transfer-progress)
./mvnw clean verify

# Quick build without tests
./mvnw clean package -DskipTests

# Build specific modules. For a module with OSGi dependencies, also list its upstream modules (see Testing)
./mvnw clean package -pl com.kk.pde.ds.target,com.kk.pde.ds.api

# Continue a failed build from the distribution module
./mvnw verify -rf :distribution

# Test the pde2tycho tool (plain Maven, not in the reactor). CI runs this as a second step.
./mvnw -f tools/pde2tycho verify
```

The build needs JDK 17+ (Tycho 4). The product runs on Java 8+.

## Testing

Tests are **inside the bundles**, in a `src_test/` folder. The `.classpath` of the bundle declares
this folder `test="true"`. `build.properties` does not include it, so no test ships. The class
name selects the tier:

- `*Test` is tier 1. A plain JVM runs it through maven-surefire (phase `test`).
- `*IT` is tier 2. A live Equinox runs it through `tycho-surefire:plugin-test` (phase `integration-test`).

There are 48 tier-1 tests and 6 tier-2 tests. The Javadoc of each test class is a short tutorial.
This layout is the reference for a larger Tycho project. Keep it clean and easy to copy.

```bash
./mvnw clean verify                                  # both tiers. CI runs this (JDK 21).
./mvnw test                                          # tier 1 only, no Equinox boot

# One bundle: list the target module and its OSGi upstream bundles explicitly
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.mcp.api
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.spike.api,com.kk.pde.ds.spike.master

# One class: tier 1 with surefire -Dtest on the test phase. Tier 2 with -Dit.test.
./mvnw test   -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dtest=GreetHealthCheckTest
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp -Dit.test=GreetServiceIT
```

- **Add tier-1 tests to a bundle:**
  1. Create `src_test/`. Add the `test="true"` entry to `.classpath`.
  2. Add the test dependencies to the pom with no versions. The parent pom manages the versions.
  3. List `maven-surefire-plugin` in the pom.
- **Add tier-2 tests to a bundle** (after the tier-1 steps):
  1. List `tycho-surefire-plugin` in the pom.
  2. Copy the `target-platform-configuration` block from `com.kk.pde.ds.imp/pom.xml` (EE constraints off + Felix SCR extraRequirement).
  3. Add the `org.osgi.framework` and `org.junit.platform:junit-platform-launcher` test dependencies. Tier 2 cannot resolve without the launcher.
  4. Write a `*IT` class.
  5. Do not add executions. The parent pom manages them.
- **Never use `src/test/java`** here. The bundles use `source.. = src/`. Thus `src/test/java` is
  inside the production source root, and Tycho compiles the tests into the bundle.
- **`-pl <module> -am` does not work.** Maven cannot see OSGi manifest dependencies. Thus `-am`
  builds only the parent and fails with `requires osgi.bundle ... but it could not be found`.
- **Local Maven can fork a different JDK than `java -version` shows.** Homebrew `mvn` uses its own
  OpenJDK (26) when `JAVA_HOME` is not set. CI uses Temurin 21. To get the CI JDK, run
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify`. This JDK difference is why the parent pom pins ByteBuddy.
- Expected log noise: a Mockito "self-attaching" warning. Also `ERROR ... Health Check failed`
  lines from tests that force the catch branch on purpose. Trust the `Tests run:` totals.
- `com.kk.pde.ds.spike.master` gives `-Djava.awt.headless=true` to both tiers. `MasterApp.start()`
  has a headless guard, because SCR activates the real component in the tier-2 runtime.
- Test libraries (JUnit, Mockito, ByteBuddy) are test-scope dependencies in the pom. They are not
  target-platform entries. They must never be in `distribution/target/products/**` or in a bundle jar.
- Tier-2 reports go to `target/failsafe-reports/`. Tier-1 reports go to `target/surefire-reports/`.

Full detail: `docs/testing.md` and `docs/superpowers/specs/2026-09-16-tycho-plugin-test-migration-design.md`.
To apply the layout to a different project: `docs/adding-tests-to-a-tycho-project.md`.

## Conventions

- Commits: conventional subjects with a scope. Examples: `feat(rag): ...`, `fix(ecf): ...`,
  `test(spike): ...`, `docs(claude): ...`, `chore(serena): ...`.
- Work goes to `main` through PRs from `feat/*`, `fix/*` or `docs/*` branches.
- CI: `.github/workflows/build.yml` runs two steps on Temurin 21:
  1. `./mvnw --batch-mode --no-transfer-progress clean verify`
  2. `./mvnw --batch-mode --no-transfer-progress -f tools/pde2tycho verify`
- The Maven Wrapper pins Maven 3.9.16. Tycho 4.0.13 does not start on Maven 3.10 (`No
  implementation for MavenTargetLocationFactory was bound`). Do not use the system `mvn` of the runner.
- Dependency minimalism is a deliberate theme (hand-written JSON, OOXML read as a zip, Tesseract
  as a subprocess). Prefer this approach to a new library. Never add a dependency that needs slf4j 2.x.
- The DS component XML in `OSGI-INF/` is hand-written. SCR reads the XML, not the annotations. When
  you change `@Activate` or `@Deactivate`, also change the XML (see `tasks/lessons.md`).
- Documentation: see the rule in the Documentation Map.

## Build Outputs

After a successful build:
- **p2 Repository:** `distribution/target/repository/`
- **Product Archives:**
  - `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz`
  - `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip`
  - `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz`

## Running the Application

> **Java requirement:** Java 8+ at runtime. The run scripts use the `java` on your `PATH`. Only CI pins Java 21.

**Simplest: run from the source tree. The scripts find the OS and the product path automatically.**

```bash
# macOS / Linux (set OPENROUTER_API_KEY env var for chatbot/LLM features):
export OPENROUTER_API_KEY=your_key_here
./distribution/scripts/run.sh

# Windows:
set OPENROUTER_API_KEY=your_key_here
distribution\scripts\run.bat
```

You can also give the API key as a system property: `./distribution/scripts/run.sh -Dopenrouter.api.key=your_key`

`run.sh` and `run.bat` send all `-D` flags to the JVM. They put the flags **before** `-jar`,
because JVM system properties must be there. Example: enable Document Q&A and ingest a folder
of documents at startup:

```bash
export OPENROUTER_API_KEY=your_key_here
./distribution/scripts/run.sh \
    -Dopenrouter.base.url=https://your-embeddings-proxy/v1 \
    -Dopenrouter.embeddings.model=intfloat/multilingual-e5-large \
    -Drag.docs.dir=/path/to/your/documents
```

**Or run from the built product directory:**

The macOS product is in an `.app` bundle:
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

The `-configuration configuration` flag is necessary. It points the OSGi framework to the `config.ini` file.

## Felix WebConsole

Open the web console at http://localhost:8080/system/console
- Username: `admin`
- Password: `admin`

The DS tab shows all Declarative Services components and their status.

## Module Structure

```
com.kk.pde.ds.target   → Target platform definition (Maven-sourced; Equinox 3.23 / 2025-03 line)
com.kk.pde.ds.api      → IGreet interface (service contract)
com.kk.pde.ds.imp      → Greet implementation (service provider)
com.kk.pde.ds.app      → App consumer (@Reference injection, starts Clock + Chatbot UI)
com.kk.pde.ds.rest     → REST API via HTTP Whiteboard (@Reference injection)
com.kk.pde.ds.mcp.api  → MCP tool contract (IMcpTool, IMcpToolRegistry)
com.kk.pde.ds.mcp.server → MCP server (HTTP servlet + tool registry + 7 built-in tools)
com.kk.pde.ds.mcp.client → MCP client (one-shot daemon thread: calls echo, greet, system_info via HTTP/JSON-RPC)
com.kk.pde.ds.mcp.llm  → OpenRouter LLM bridge (agent loop with tool calls)
com.kk.pde.ds.chatbot  → Swing chatbot UI (model selection, chat history, LLM integration)
com.kk.pde.ds.rag      → Document Q&A (RAG): parse/chunk/embed/store + 2 tools
com.kk.pde.ds.ecf.api      → IRemoteGreet contract (ECF remote service interface)
com.kk.pde.ds.ecf.host     → RemoteGreetImpl exported as a remote service (ECF RSA)
com.kk.pde.ds.ecf.consumer → Imports IRemoteGreet via EDEF, @Reference injection
com.kk.pde.ds.spike.api    → Spike/experiment contract bundle
com.kk.pde.ds.spike.master → Spike master component
com.kk.pde.ds.spike.detail → Spike detail component
com.kk.pde.ds.feature  → Feature grouping all bundles
distribution           → p2 repository + product builds
settings/mcp.json      → MCP client config. The build copies it into the product. No code reads it.
fatjar                 → Standalone fat-JAR launcher (built separately, not in the reactor)
tools/pde2tycho        → PDE-workspace-to-Tycho migration CLI (plain Maven, not in the reactor)
```

The MCP tool registry has 9 tools: 7 in `mcp.server` (`echo`, `greet`, `system_info`, `calculator`,
`date_time`, `bundle_list`, `http_fetch`) and 2 in `rag` (`document_search`, `ingest_documents`).

Tests (not shipped) are in `src_test/` of `imp` (`GreetTest`, `GreetHealthCheckTest`,
`GreetServiceIT`), `mcp.api` (`JsonTest`) and `spike.master` (`DockLayoutTest`,
`SpikeValueObjectsTest`, `CatalogServiceImplTest`, `MasterAppTest`, `CatalogServiceIT`).
See Testing above.

## REST API

The `com.kk.pde.ds.rest` module exposes the IGreet service as HTTP endpoints. It uses the OSGi HTTP Whiteboard pattern.

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

The `com.kk.pde.ds.chatbot` bundle gives a Swing chat interface for LLMs.

**Features:**
- Multi-turn conversation with session history
- Model selection in an editable dropdown (it queries the `/v1/models` endpoint)
- Works with all OpenAI-compatible APIs (OpenRouter, LM Studio, and others)
- MCP tool integration: the LLM can call registered tools during a conversation
- A Clear History button resets the conversation

**Configuration:**
- `OPENROUTER_API_KEY` or `-Dopenrouter.api.key` (required)
- `-Dopenrouter.model` (default `google/gemini-flash-1.5`)
- `-Dopenrouter.base.url` (default `https://openrouter.ai/api/v1`). All OpenAI-compatible endpoints work.

The RAG embeddings client reads the same `openrouter.*` properties. Full table: README "Chatbot UI".

**Service binding flow:**
1. `OpenRouterAgent` injects `IMcpToolRegistry` for tool access.
2. `ChatService` injects `OpenRouterAgent` for LLM communication.
3. `App` injects `ChatService` and starts `ChatFrame` on the EDT.
4. `ChatFrame` uses `SwingWorker`, so LLM calls do not block the UI.

## Document Q&A (RAG)

The `com.kk.pde.ds.rag` bundle adds **retrieval-augmented generation** (RAG). It ingests local
documents. It gives document search to the LLM **as a tool** (`document_search`). Thus the
chat/tool loop decides when to search. No pipeline injects context before each answer. The two RAG
tools register through the same `IMcpToolRegistry` as the built-in tools. Thus **no existing module changed**.

**Pipeline:** each stage is an OSGi service behind an interface in `com.kk.pde.ds.rag.api`.

| Component | Role |
|-----------|------|
| `MultiFormatDocumentParser` | PDF: Apache PDFBox. `.docx`/`.pptx`: reads the OOXML zip directly. `.html`/`.htm`: removes the tags. `.txt`/`.md`: reads UTF-8. |
| `SlidingWindowChunker` | Chunks of ~400 tokens with ~12% overlap, at paragraph or sentence boundaries. Each chunk stays below the 512-token window of E5. |
| `OpenAiEmbeddingClient` | Sends POST to `/v1/embeddings` with the same configuration as the chat client. Adds the E5 `query:`/`passage:` role prefixes. |
| `InMemoryVectorStore` | Brute-force cosine search over `float[]`, behind a replaceable `VectorStore` interface (ready for pgvector) |
| `DocumentIngestionService` | Runs parse → chunk → embed → store, and answers `search` |
| `DocumentSearchTool` / `IngestDocumentsTool` | The two `IMcpTool`s that the LLM can call |

**Supported formats:** `.pdf`, `.docx`, `.pptx`, `.html`/`.htm`, `.txt`/`.md`. When OCR is
available, also standalone image files (`.png`/`.jpg`/`.jpeg`/`.tif`/`.tiff`/`.bmp`/`.gif`).

**Configuration:**
- `-Drag.docs.dir=<folder>` ingests the folder at startup.
- Embeddings use the `openrouter.*` key and base URL of the chat client, plus
  `-Dopenrouter.embeddings.model` (default `intfloat/multilingual-e5-large`).
- OCR is on by default when a `tesseract` binary is on the host (`rag.ocr.enabled`,
  `rag.ocr.binary`, `rag.ocr.lang`, `rag.ocr.dpi`, ...).

Full property table and the MCP `curl` examples for `ingest_documents` / `document_search`:
`docs/rag-reference.md`. Run guide: README section "8. RAG — Document Q&A".

**OCR:**
- A scanned PDF page is a page with a text layer below `rag.ocr.min.chars.per.page`. PDFBox
  `PDFRenderer` rasterizes each such page, and OCR reads the full page.
- OCR also reads embedded images on text pages and `.docx` `word/media/` images. It appends the text.
  OCR does not read `.pptx` images yet.
- OCR is the **only** step that runs a subprocess (`tesseract <img> stdout -l <lang>`). Without the
  binary, or with `rag.ocr.enabled=false`, ingestion silently uses text only.
- Use `-Djava.awt.headless=true` for server runs (PDFBox and ImageIO use AWT).

**Key facts / pitfalls:**
- **No Apache Tika.** All Tika versions require slf4j 2.0. That causes a logging migration for the
  full project (Logback 1.3 + Aries SPI Fly). It also breaks Felix Health Check
  (`org.slf4j.helpers [1.7,2.0)`). PDFBox logs through commons-logging (JCL), so the slf4j 1.7 /
  Logback 1.2 stack does not change. The parser reads `.docx`/`.pptx` as OOXML zips (no Apache POI).
- **OCR uses the Tesseract CLI, not Tess4J.** The subprocess approach adds no Java or OSGi
  dependencies and no transitive slf4j. The same dependency minimalism excluded Tika. The cost is
  that the host needs an external binary. Rasterization needs no additional dependency, because
  PDFBox 2.0.32 already contains `PDFRenderer`.
- The embeddings client uses the same configuration source as the chat client (`openrouter.*`).
  Thus the embeddings endpoint uses the same base URL and key.
- E5 models are asymmetric and sensitive to prefixes. They truncate at 512 tokens. This is why
  the role prefixes and the 400-token chunk default exist.
- See `docs/rag-reference.md` for the full verification record. See `docs/rag-design.md` for the design narrative.

## ECF Remote Services

The `com.kk.pde.ds.ecf.*` bundles show **OSGi Remote Services** through the Eclipse Communication
Framework (ECF). One OSGi process (the *host*) exports a service. A separate process (the
*consumer*) uses it through DS `@Reference`. This is the same `@Reference` pattern as the local
demo, but across a network socket.

**Topology:** two separate JVMs (two dedicated products). They communicate through the ECF
**Generic provider** transport on `ecftcp://localhost:3288/server`. Discovery uses **file-based
EDEF** (no ZooKeeper or mDNS daemon).

| Bundle | Role |
|--------|------|
| `com.kk.pde.ds.ecf.api` | `IRemoteGreet { String greet(String name); }` |
| `com.kk.pde.ds.ecf.host` | `RemoteGreetImpl`: `@Component` with `service.exported.interfaces=*`, `service.exported.configs=ecf.generic.server`, `ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server` |
| `com.kk.pde.ds.ecf.consumer` | `RemoteGreetConsumer` (`@Reference`) + `GreetCommand` (Gogo `ecf:greet`) + the EDEF file `OSGI-INF/remote-service/remote-greet-endpoint.xml`. The `Remote-Service` manifest header refers to the EDEF file. |

**Run it (two terminals):**
```bash
./mvnw clean verify
./distribution/scripts/run-ecf-host.sh       # terminal 1 — exports IRemoteGreet
./distribution/scripts/run-ecf-consumer.sh   # terminal 2 — imports + invokes greet("ECF")
```
The consumer logs `Remote response: Hello, ECF! (served remotely by host)`. At the `osgi>`
prompt of the consumer, `ecf:greet World` calls the host again.

**Spike (multi-frame / multi-JVM UI):** the same two-terminal pattern over ECF, on port 3289.
```bash
./distribution/scripts/run-spike-master.sh   # terminal 1 — publishes DockLayout, tiles App-1 frames
./distribution/scripts/run-spike-detail.sh   # terminal 2 — tiles its own frames from the shared numbers
```
Design and findings: see the spike documents in the Documentation Map.

**Flow:**
1. The host `RemoteGreetImpl` registers `IRemoteGreet` with `service.exported.*` properties.
2. ECF RSA exports it through the Generic server (TCP socket on :3288).
3. The consumer `Remote-Service` header → RSA reads the EDEF → connects → registers a proxy `IRemoteGreet`.
4. The consumer `@Reference` binds the proxy. Each method call goes to the host JVM and back.

**Key facts / pitfalls:**
- The target platform pins ECF to the **3.14.x line**. It is the last line with `JavaSE-1.8` BREEs. Newer ECF
  needs Java 11/17. The bundles come from Maven Central (groupId `org.eclipse.ecf`). The runtime
  here is Java 21, so Java-17 Equinox bundles also resolve.
- The target platform must list the ECF bundle set **explicitly**. Maven's transitive resolver
  cannot see OSGi `Require-Bundle`/`Import-Package`.
- In the static EDEF, `ecf.endpoint.id.ns` must be `org.eclipse.ecf.core.identity.StringID` (the
  identity namespace class of the Generic provider). `ecf.rsvc.id` must be `1` (deterministic for
  a single exported service). If these values change, copy them from the `ECFEndpointDescription` log of the host.
- The two products do not include Felix HTTP/WebConsole. Thus the two JVMs do not both try to use port 8080.

## Architecture

**Service binding flow:**
1. The `Greet` component registers as the `IGreet` service.
2. The `App` component has `@Reference` on `setApi(IGreet)`.
3. Felix SCR injects `Greet` into `App`.
4. `App.start()` calls `api.greet()` → prints "Hello world!".

## Key Configuration Files

| File | Purpose |
|------|---------|
| `com.kk.pde.ds.target/*.target` | Target platform. Maven-location dependencies, no p2 repository URLs. Most locations use `includeDependencyDepth="none"` and list each bundle explicitly. Two locations use `includeDependencyDepth="infinite"`: Logback, and the test harness (`org.eclipse.core.runtime` + the Equinox launcher). Test libraries are *not* here. They are pom test dependencies. |
| `.github/workflows/build.yml` | CI on Temurin 21: `./mvnw clean verify`, then `./mvnw -f tools/pde2tycho verify` |
| `distribution/p2.product` | Main product definition (bundles, start levels) |
| `distribution/com.kk.pde.ds.ecf.host.product`, `distribution/com.kk.pde.ds.ecf.consumer.product` | ECF host and ECF consumer products |
| `distribution/com.kk.pde.ds.spike.master.product`, `distribution/com.kk.pde.ds.spike.detail.product` | Spike master and spike detail products |
| `distribution/category.xml` | p2 repository category structure |
| `*/META-INF/MANIFEST.MF` | OSGi bundle metadata |
| `*/OSGI-INF/*.xml` | DS component declarations (hand-written, see Conventions) |

## Technology Stack

- **Java 8+** at runtime. All shipped bundles and the fat JAR launcher use Java 8 bytecode.
  The build and the tests need JDK 17+.
- **Tycho 4.0.13** (Maven OSGi build)
- **Equinox 3.23 (2025-03 line)**: `org.eclipse.osgi` 3.23.0. The target platform comes from Maven Central, with no p2 URLs.
- **JUnit 5.12 + Mockito 5.14** (test-only, never shipped)
- **OSGi Declarative Services** with annotations in the source and hand-written XML descriptors
- **Felix SCR** runtime
- **Felix HTTP Jetty** with HTTP Whiteboard for REST API
- **Felix WebConsole** with DS plugin
- **Eclipse Communication Framework (ECF) 3.14.x** for OSGi Remote Services (RSA) with the Generic provider
- **Apache PDFBox 2.0.x** (+ fontbox, commons-logging) for PDF text extraction in the RAG bundle
- **Logback** for logging (default level: INFO)
