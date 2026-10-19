# Build Reference

This document tells you how the Maven Tycho build works and what it makes. It also describes the project layout, the target platform and the product definition.

Contents:

- [Requirements](#requirements)
- [Build commands](#build-commands)
- [Continuous integration](#continuous-integration)
- [Build outputs](#build-outputs)
- [Parent POM](#parent-pom)
- [Target platform](#target-platform)
- [Product definition](#product-definition)
- [Project structure](#project-structure)
- [File reference](#file-reference)
- [Statistics](#statistics)

Related documents:

- [running.md](running.md): how to run the products.
- [fat-jar.md](fat-jar.md): the single-JAR delivery model.
- [testing.md](testing.md): the two test tiers and how to run them.
- [architecture.md](architecture.md): component and service wiring.
- [../README.md](../README.md): project overview.
- [../SECURITY.md](../SECURITY.md): known exposures of the localhost HTTP surface.

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **JDK to build** | 17 or newer. Tycho 4 needs it, and the tier-2 test runtime needs `JavaSE-17`. CI uses Temurin 21. |
| **Java to run** | 8 or newer. All shipped bundles and the fat JAR launcher use Java 8 bytecode. |
| **Maven** | Use the Maven Wrapper: `./mvnw`, or `mvnw.cmd` on Windows. The wrapper pins Maven 3.9.16 in `.mvn/wrapper/maven-wrapper.properties`. Tycho 4.0.13 does not start on Maven 3.10. |
| **Internet** | Maven must download the target platform dependencies. |

> **Local JDK note:** Homebrew `mvn` can use a different JDK than `java -version` shows. To use the same JDK as CI on macOS, set `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

---

## Build commands

Run all commands from the repository root. On Windows, use `mvnw.cmd` instead of `./mvnw`.

```bash
# Full build with tests and verification (what CI runs)
./mvnw clean verify

# Quick build (no tests)
./mvnw clean package -DskipTests

# Build specific modules: always list the target module and the OSGi upstream modules
./mvnw clean package -pl com.kk.pde.ds.target,com.kk.pde.ds.api

# Resume a failed build from the distribution module
./mvnw verify -rf :distribution

# Build with debug output
./mvnw clean verify -X
```

Do not use `-pl <module> -am`. Maven cannot see the OSGi manifest dependencies. Thus `-am` builds only the parent, and the build fails with `requires osgi.bundle ... but it could not be found`.

For the test commands, see [testing.md](testing.md).

---

## Continuous integration

Maven Tycho builds this Eclipse PDE / OSGi DS project without an IDE. Tycho resolves the target platform from Maven Central. Then it makes a p2 repository and runnable products for each platform.

GitHub Actions runs the build on each push to `main` and on each pull request to `main`. The workflow is [.github/workflows/build.yml](../.github/workflows/build.yml). It has these steps on `ubuntu-latest`:

1. Set up JDK 21 (Temurin), with a Maven cache.
2. Run `./mvnw --batch-mode --no-transfer-progress clean verify`.
3. Run `./mvnw --batch-mode --no-transfer-progress -f tools/pde2tycho verify`. This step tests the PDE-to-Tycho migration tool, which is outside the Tycho reactor.

The workflow uses the wrapper and not the system `mvn` of the runner. The reason is the Maven 3.10 problem in [Requirements](#requirements).

---

## Build outputs

| Output | Location |
|--------|----------|
| **p2 repository** | `distribution/target/repository/` |
| **Linux archive** | `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz` |
| **Windows archive** | `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip` |
| **macOS archive** | `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz` |

Each archive contains all that the application needs. Unpack it on the target machine. Then run the launcher script in it, or run this command:

```bash
java -jar plugins/org.eclipse.osgi_*.jar -configuration configuration -console -consoleLog
```

On macOS, the product is in an `.app` bundle. Run the command from `Eclipse.app/Contents/Eclipse`.

The `run.sh` scripts in the two `.tar.gz` archives have mode 755. You do not have to make them executable.

Only the main product has archives. The ECF and spike products stay as materialized directories under `distribution/target/products/`. Their run scripts start them from there. For the run procedures, see [running.md](running.md).

The fat JAR is a separate build. See [fat-jar.md](fat-jar.md).

---

## Parent POM

**File**: `pom.xml`

```xml
<properties>
    <tycho.version>4.0.13</tycho.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
</properties>
```

The parent POM has 19 modules. It also sets the versions of the test libraries (JUnit 5.12.2, Mockito 5.14.2, ByteBuddy 1.18.13). The test libraries are test-scope dependencies only.

### Build plugins

| Plugin | Purpose |
|--------|---------|
| `tycho-maven-plugin` | Enables OSGi/PDE build support |
| `target-platform-configuration` | Configures the target platform and the build environments |
| `tycho-compiler-plugin` | Compiles Java 8 bytecode |
| `maven-surefire-plugin` (3.5.6) | Runs tier-1 tests (`*Test`) in a plain JVM |
| `tycho-surefire-plugin` | Runs tier-2 tests (`*IT`) inside Equinox (`plugin-test`) |
| `tycho-p2-director-plugin` | Materializes the products (no archives). In `distribution/pom.xml` |
| `maven-antrun-plugin` (3.1.0) | Post-processes the products and makes the main product archives. In `distribution/pom.xml` |

The `maven-antrun-plugin` step runs in the `verify` phase. It does these tasks:

1. It changes the bundle paths in each `config.ini` from `reference:file:` to `reference:file:../plugins/`. This lets `java -jar` start the product.
2. It copies `settings/mcp.json`, `logback.xml`, the health check `*.cfg` files and `run.sh`/`run.bat` into the main product.
3. It copies `logback.xml` and the related run scripts into the ECF and spike products.
4. It sets mode 755 on the `.sh` scripts.
5. It makes the three main product archives again, with the changed `config.ini`. The `.tar.gz` archives keep mode 755 on `run.sh`.

### Build environments

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

---

## Target platform

**File**: `com.kk.pde.ds.target/com.kk.pde.ds.target.target`

The target platform defines all OSGi bundles that are available to the build and the runtime. It has only Maven locations, with no p2 repository URLs. It lists each bundle explicitly, because Maven cannot see OSGi `Require-Bundle` and `Import-Package` requirements. The tables below show each group. The `.target` file is the source of truth.

#### OSGi core and runtime

| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.eclipse.osgi` | 3.23.0 | OSGi framework (Equinox) |
| `org.eclipse.equinox.console` | 1.4.800 | Console support |
| `org.apache.felix.scr` | 2.2.12 | Declarative Services runtime |

#### Felix Gogo shell

| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.felix.gogo.runtime` | 1.1.6 | Shell runtime |
| `org.apache.felix.gogo.command` | 1.1.2 | Shell commands |
| `org.apache.felix.gogo.shell` | 1.1.4 | Interactive shell |

#### HTTP service

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
| `org.apache.felix.fileinstall` | 3.7.4 | Loads configurations automatically |

#### Health checks

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

#### OSGi services

| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.osgi.service.component` | 1.5.1 | DS service interfaces |
| `org.osgi.service.component.annotations` | 1.5.1 | DS annotations |
| `org.osgi.util.function` | 1.2.0 | Functional interfaces |
| `org.osgi.util.promise` | 1.3.0 | Promise API |

#### Logging

| Bundle | Version | Purpose |
|--------|---------|---------|
| `ch.qos.logback:logback-classic` | 1.2.11 | Logging. This location includes the transitive dependencies. |

#### Utilities

| Bundle | Version | Purpose |
|--------|---------|---------|
| `commons-fileupload` | 1.5 | File upload support |
| `commons-io` | 2.16.1 | IO utilities |

#### Document parsing (RAG)

| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.apache.pdfbox:pdfbox` | 2.0.32 | PDF text extraction and rasterization |
| `org.apache.pdfbox:fontbox` | 2.0.32 | Font support for PDFBox |
| `commons-logging` | 1.3.5 | Logging backend for PDFBox (JCL, not slf4j) |

#### ECF remote services (3.14.x line)

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

#### Test harness (tier-2 tests only, never shipped)

| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.eclipse.core.runtime` | 3.33.0 | Runtime for `tycho-surefire`, with its transitive closure |
| `org.eclipse.equinox.launcher` | 1.6.1000 | Starts the tier-2 test runtime |

The test libraries (JUnit, Mockito, ByteBuddy) are not in the target platform. They are test-scope dependencies in the poms.

---

## Product definition

**File**: `distribution/p2.product`

The `distribution/` module also has four more product files: `com.kk.pde.ds.ecf.host.product`, `com.kk.pde.ds.ecf.consumer.product`, `com.kk.pde.ds.spike.master.product` and `com.kk.pde.ds.spike.detail.product`. This section describes the main product only.

### Product configuration

| Setting | Value |
|---------|-------|
| **UID** | `com.kk.pde.ds.product` |
| **Version** | `1.0.0.qualifier` |
| **Type** | bundles |
| **Include Launchers** | false (the product uses the Equinox launcher) |
| **Auto Include Requirements** | true |
| **config.ini** | `configuration/config.ini` for Linux, macOS and Windows |

### Launcher arguments

```xml
<launcherArgs>
    <programArgs>-consoleLog</programArgs>
    <vmArgs>-Declipse.ignoreApp=true -Dosgi.noShutdown=true
            -Dlogback.configurationFile=configuration/logback.xml</vmArgs>
    <vmArgsMac>-XstartOnFirstThread</vmArgsMac>
</launcherArgs>
```

### Bundle start levels

| Level | Bundles | Purpose |
|-------|---------|---------|
| **0** (default) | http.jetty, http.servlet-api, webconsole, webconsole.plugins.ds, inventory, osgi.service.servlet | HTTP infrastructure. Level 0 means "use the default start level". See the note below. |
| **1** | osgi, configadmin, metatype | Core framework |
| **2** | scr, service.component, service.component.annotations, util.function, util.promise, fileinstall, service.event, eventadmin, healthcheck.api, healthcheck.core | Services |
| **3** | gogo.*, equinox.console, healthcheck.generalchecks, healthcheck.webconsoleplugin | Console and plugins |
| **4** | api, imp, app, rest, mcp.api, mcp.server, mcp.client, mcp.llm, chatbot, rag | Application bundles (auto-start) |
| **4** | pdfbox, fontbox, commons-logging | RAG libraries (no auto-start) |

Notes on the start levels:

- In a product file, `startLevel="0"` means the default start level. `distribution/configuration/config.ini` sets `osgi.bundles.defaultStartLevel=4`. Thus the built `config.ini` starts the HTTP and WebConsole bundles at level 4 (`@4:start`).
- `org.eclipse.osgi` is the framework itself. Its level 1 entry has no effect.
- `org.osgi.service.servlet` has a start-level entry, but it is not in the plugin list and not in the built product.
- Tycho adds the requirements (Logback, slf4j) to the built `config.ini` at level 4 with no auto-start. commons-fileupload and commons-io have no `<configurations>` entry, so they also get level 4 with no auto-start.

The fat JAR uses a different start-level table. See [fat-jar.md](fat-jar.md#start-levels).

### Included plugins

`p2.product` lists the plugins below. Because `autoIncludeRequirements="true"`, Tycho also adds their requirements (for example Logback and slf4j) to the built product. Thus the `plugins/` directory contains more JARs than the list.

**Application (10)**:

- `com.kk.pde.ds.api`, `com.kk.pde.ds.imp`, `com.kk.pde.ds.app`, `com.kk.pde.ds.rest`
- `com.kk.pde.ds.mcp.api`, `com.kk.pde.ds.mcp.server`, `com.kk.pde.ds.mcp.client`, `com.kk.pde.ds.mcp.llm`
- `com.kk.pde.ds.chatbot`, `com.kk.pde.ds.rag`

**RAG libraries**: `org.apache.pdfbox`, `org.apache.pdfbox.fontbox`, `org.apache.commons.commons-logging`

**Framework**:

- OSGi: `org.eclipse.osgi`, `org.eclipse.equinox.console`
- Felix: scr, gogo.*, http.*, webconsole.*, configadmin, metatype, fileinstall, eventadmin, healthcheck.*, inventory
- OSGi services: service.component, service.component.annotations, util.function, util.promise, service.event
- Apache Commons: commons-fileupload, commons-io

---

## Project structure

```
p.osgi.ds/
├── pom.xml                           # Parent POM (Tycho build, 19 modules)
├── mvnw, mvnw.cmd                    # Maven Wrapper (use these, not the system mvn)
├── .mvn/wrapper/                     # Wrapper config (pins Maven 3.9.16)
├── .github/workflows/build.yml       # CI: ./mvnw clean verify on Temurin 21
├── CLAUDE.md                         # IDE guidance
├── README.md                         # Project overview
├── SECURITY.md                       # Known exposures of the localhost HTTP surface
├── docs/                             # Design notes, how-to guides, references
├── settings/
│   └── mcp.json                      # MCP client config (copied into the product, not read by the app)
│
├── com.kk.pde.ds.target/             # Target platform definition
│   ├── pom.xml
│   └── com.kk.pde.ds.target.target   # Maven dependencies for OSGi
│
├── com.kk.pde.ds.api/                # Service API bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   └── src/com/kk/pde/ds/api/
│       └── IGreet.java               # Service interface
│
├── com.kk.pde.ds.imp/                # Service implementation bundle
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
├── com.kk.pde.ds.app/                # Service consumer bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   │   └── com.kk.pde.ds.app.App.xml
│   └── src/com/kk/pde/ds/app/
│       └── App.java                  # Consumer component (opens Clock + Chat windows)
│
├── com.kk.pde.ds.rest/               # REST API bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   │   └── com.kk.pde.ds.rest.GreetServlet.xml
│   └── src/com/kk/pde/ds/rest/
│       ├── GreetServlet.java         # HTTP Whiteboard servlet
│       └── GreetResponse.java        # Response DTO
│
├── com.kk.pde.ds.mcp.api/            # MCP tool API bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── src/com/kk/pde/ds/mcp/api/
│   │   ├── IMcpTool.java             # Tool contract interface
│   │   ├── IMcpToolRegistry.java     # Tool registry interface
│   │   └── Json.java                 # Shared hand-rolled JSON parser
│   └── src_test/                     # JsonTest
│
├── com.kk.pde.ds.mcp.server/         # MCP server bundle
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
├── com.kk.pde.ds.mcp.client/         # MCP client bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/com.kk.pde.ds.mcp.client.McpClient.xml
│   └── src/com/kk/pde/ds/mcp/client/
│       └── McpClient.java            # Protocol test client
│
├── com.kk.pde.ds.mcp.llm/            # LLM bridge bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   └── src/com/kk/pde/ds/mcp/llm/
│       ├── OpenRouterAgent.java      # Agent loop with tool calls
│       └── LlmChatServlet.java       # HTTP endpoint for LLM chat
│
├── com.kk.pde.ds.chatbot/            # Chatbot UI bundle
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
├── com.kk.pde.ds.rag/                # Document Q&A (RAG) bundle
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
├── fatjar/                           # Fat JAR launcher (standalone, not in the reactor)
│   ├── pom.xml                       # Plain Maven (not Tycho)
│   ├── run.sh                        # Build + run convenience script
│   ├── src/assembly/fatjar.xml       # Assembly descriptor
│   └── src/main/java/.../Main.java   # Embedded Equinox launcher
│
├── tools/pde2tycho/                  # PDE-to-Tycho migration CLI (plain Maven, not in the reactor)
│
└── distribution/                     # Products and p2 repository
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

## File reference

The tables below give the purpose of the important files. The tree in [Project structure](#project-structure) shows where they are.

### Build configuration

| File | Purpose |
|------|---------|
| `pom.xml` | Parent Maven Tycho POM |
| `*/pom.xml` | Module-specific POMs |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper (Maven 3.9.16) |
| `.github/workflows/build.yml` | CI workflow |
| `com.kk.pde.ds.target/*.target` | Target platform definition |

### Product configuration

| File | Purpose |
|------|---------|
| `distribution/p2.product` | Main product definition with bundles and start levels |
| `distribution/com.kk.pde.ds.{ecf.host,ecf.consumer,spike.master,spike.detail}.product` | ECF and spike product definitions |
| `distribution/category.xml` | p2 repository category structure |
| `distribution/configuration/config.ini` | OSGi framework configuration |
| `distribution/configuration/logback.xml` | Logging configuration |
| `distribution/configuration/configs/*.cfg` | Health check configurations |

### Launcher scripts

| File | Purpose |
|------|---------|
| `distribution/scripts/run.sh` | Linux/macOS launcher for the main product |
| `distribution/scripts/run.bat` | Windows launcher for the main product |
| `distribution/scripts/run-ecf-{host,consumer}.{sh,bat}` | ECF host and consumer launchers |
| `distribution/scripts/run-spike-{master,detail}.{sh,bat}` | Spike master and detail launchers |

### Application sources

| File | Purpose |
|------|---------|
| `com.kk.pde.ds.api/src/.../IGreet.java` | Service interface |
| `com.kk.pde.ds.imp/src/.../Greet.java` | Service implementation |
| `com.kk.pde.ds.imp/src/.../GreetHealthCheck.java` | Custom health check |
| `com.kk.pde.ds.app/src/.../App.java` | Service consumer |
| `com.kk.pde.ds.rest/src/.../GreetServlet.java` | HTTP Whiteboard servlet |
| `com.kk.pde.ds.rest/src/.../GreetResponse.java` | Response DTO |

### OSGi metadata

| File | Purpose |
|------|---------|
| `*/META-INF/MANIFEST.MF` | Bundle metadata (exports, imports and more) |
| `*/OSGI-INF/*.xml` | DS component descriptors |
| `com.kk.pde.ds.feature/feature.xml` | Feature definition |

### Runtime settings

| File | Purpose |
|------|---------|
| `settings/mcp.json` | MCP client configuration. It points to `http://localhost:8080/mcp`. The application does not read it. |
| `<working dir>/settings/chatbot.properties` | Chatbot settings (API key, model, base URL). The Settings dialog writes this file. |

---

## Statistics

| Metric | Count |
|--------|-------|
| Maven modules | 19 in the root pom. The reactor has 20 projects with the parent. `fatjar` and `tools/pde2tycho` build separately. |
| Application bundles | 16 (api, imp, app, rest, mcp.api/server/client/llm, chatbot, rag, ecf.api/host/consumer, spike.api/master/detail) |
| Tests | 54 in 9 classes across 3 bundles: 48 plain-JVM (`*Test`) and 6 in-framework (`*IT`). JUnit 5 and Mockito. |
| DS components | 31 (`OSGI-INF/*.xml` descriptors) |
| MCP tools | 9 (echo, greet, system_info, calculator, date_time, bundle_list, http_fetch, document_search, ingest_documents) |
| Health checks | 7 (1 custom and 6 file-based) |
| Launcher scripts | 10 (`run`, `run-ecf-host/consumer`, `run-spike-master/detail`, each as `.sh` and `.bat`) |
| Products | 5 (main, ecf.host, ecf.consumer, spike.master, spike.detail), each for Linux, Windows and macOS |
| Start levels | 5 (0 to 4) |
| Target dependencies | 48 Maven coordinates in the `.target` file |
