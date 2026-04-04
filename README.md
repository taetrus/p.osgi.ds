# OSGi Declarative Services Application

A complete Java 8 OSGi application demonstrating enterprise-grade architecture with Declarative Services, HTTP Whiteboard, Health Checks, and product builds using Maven Tycho.

---

## Table of Contents

1. [Overview](#overview)
2. [Requirements](#requirements)
3. [Quick Start](#quick-start)
4. [Project Structure](#project-structure)
5. [Build System (Maven Tycho)](#build-system-maven-tycho)
6. [Target Platform](#target-platform)
7. [Declarative Services (DS)](#declarative-services-ds)
8. [HTTP Whiteboard (REST API)](#http-whiteboard-rest-api)
9. [Felix WebConsole](#felix-webconsole)
10. [Health Checks](#health-checks)
11. [MCP Server (Model Context Protocol)](#mcp-server-model-context-protocol)
12. [LLM Integration (OpenRouter / OpenAI-Compatible)](#llm-integration-openrouter--openai-compatible)
13. [Chatbot UI](#chatbot-ui)
14. [File-Based Configuration](#file-based-configuration)
15. [Settings Loader](#settings-loader)
16. [Logging](#logging)
17. [Product Definition](#product-definition)
18. [Running the Application](#running-the-application)
19. [Fat JAR (Single Executable)](#fat-jar-single-executable)
20. [Remote Debugging](#remote-debugging)
21. [IDE Integration](#ide-integration)
22. [OSGi Console Commands](#osgi-console-commands)
23. [Troubleshooting](#troubleshooting)
24. [File Reference](#file-reference)

---

## Overview

This workspace contains a fully-functional Java 8 OSGi application built with **Maven Tycho 4.0.13**. It demonstrates:

| Feature | Description |
|---------|-------------|
| **Declarative Services** | Clean API/implementation/consumer separation with `@Component`, `@Reference`, `@Activate` |
| **HTTP Whiteboard** | REST-like endpoints via OSGi HTTP Whiteboard servlet pattern |
| **Felix WebConsole** | Web-based administration with DS plugin |
| **Health Checks** | Custom and general health checks with WebConsole plugin |
| **File Install** | Auto-loading configuration files at runtime |
| **Target Platform** | Tycho target definition with Maven dependencies |
| **Product Builds** | Platform-specific archives for Linux, Windows, macOS |
| **Remote Debugging** | JDWP support for attaching debuggers |
| **Logging** | Logback with configurable levels |
| **MCP Server** | Model Context Protocol with tool registry and JSON-RPC 2.0 |
| **LLM Integration** | OpenAI-compatible API bridge with agent loop and tool calls |
| **Chatbot UI** | Swing desktop chat with model selection and conversation history |

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **Java** | 17 or newer (bundles compile to Java 8 bytecode, but Eclipse Equinox 3.22 runtime requires Java 17+) |
| **Maven** | 3.6+ |
| **Internet** | Required for Maven to resolve target platform dependencies |

---

## Quick Start

### Build
```bash
# Full build with verification
mvn clean verify

# Quick build (skip tests)
mvn clean package -DskipTests
```

### Run (after build)

**Option A — Launcher script** (runs from the built product directory):

```bash
# macOS / Linux:
./distribution/scripts/run.sh

# Windows:
distribution\scripts\run.bat
```

**Option B — Fat JAR** (single executable, no product directory needed):

```bash
cd fatjar && mvn clean package
java -jar target/osgi-fatjar.jar
```

### Enable LLM Features (Chatbot)

Set your OpenRouter API key as an environment variable before running:

```bash
export OPENROUTER_API_KEY=your_key_here   # macOS/Linux
set OPENROUTER_API_KEY=your_key_here      # Windows
```

The chatbot window will launch automatically alongside the clock.

### Access Services
- **WebConsole**: http://localhost:8080/system/console (admin/admin)
- **REST API**: http://localhost:8080/api/greet

---

## Project Structure

```
p.osgi.ds/
├── pom.xml                           # Parent POM (Tycho build)
├── CLAUDE.md                         # IDE guidance
├── README.md                         # This file
├── settings/
│   └── mcp.json                      # Runtime settings file
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
│   └── src/com/kk/pde/ds/imp/
│       ├── Greet.java                # IGreet implementation
│       └── GreetHealthCheck.java     # Custom health check
│
├── com.kk.pde.ds.app/                # Service Consumer Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   │   └── com.kk.pde.ds.app.App.xml
│   └── src/com/kk/pde/ds/app/
│       ├── App.java                  # Consumer component
│       └── McpSettings.java          # Settings loader utility
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
│   └── src/com/kk/pde/ds/mcp/api/
│       ├── IMcpTool.java             # Tool contract interface
│       └── IMcpToolRegistry.java     # Tool registry interface
│
├── com.kk.pde.ds.mcp.server/         # MCP Server Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/                     # DS component descriptors
│   └── src/com/kk/pde/ds/mcp/server/
│       ├── McpServlet.java           # JSON-RPC 2.0 HTTP endpoint
│       ├── McpToolRegistry.java      # Dynamic tool registry
│       ├── JsonUtil.java             # JSON parser utility
│       └── tools/
│           ├── EchoTool.java         # Echo tool
│           ├── GreetTool.java        # Greet tool (uses IGreet)
│           └── SystemInfoTool.java   # System info tool
│
├── com.kk.pde.ds.mcp.client/         # MCP Client Bundle
│   ├── pom.xml
│   └── src/com/kk/pde/ds/mcp/client/
│       └── McpClient.java            # Protocol test client
│
├── com.kk.pde.ds.mcp.llm/            # LLM Bridge Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   └── src/com/kk/pde/ds/mcp/llm/
│       ├── OpenRouterAgent.java       # Agent loop with tool calls
│       ├── LlmChatServlet.java        # HTTP endpoint for LLM chat
│       └── LlmJsonUtil.java          # JSON parser (extended)
│
├── com.kk.pde.ds.chatbot/            # Chatbot UI Bundle
│   ├── pom.xml
│   ├── META-INF/MANIFEST.MF
│   ├── OSGI-INF/
│   └── src/com/kk/pde/ds/chatbot/
│       ├── ChatService.java           # DS component (history, model)
│       ├── ChatFrame.java             # Main window (JFrame)
│       ├── ChatPanel.java             # Message display (styled)
│       ├── InputPanel.java            # Text input + buttons
│       └── ModelFetcher.java          # /v1/models endpoint query
│
├── fatjar/                            # Fat JAR Launcher (standalone)
│   ├── pom.xml                       # Plain Maven (not Tycho)
│   ├── run.sh                        # Build + run convenience script
│   ├── src/assembly/fatjar.xml       # Assembly descriptor
│   └── src/main/java/.../Main.java   # Embedded Equinox launcher
│
├── com.kk.pde.ds.feature/            # Feature (bundle grouping)
│   ├── pom.xml
│   └── feature.xml
│
├── com.kk.pde.ds.imp.tests/          # Test Bundle (disabled)
│   ├── pom.xml
│   └── src/.../GreetTest.java
│
└── distribution/                     # Product & Repository
    ├── pom.xml
    ├── p2.product                    # Product definition
    ├── category.xml                  # p2 category structure
    ├── configuration/
    │   ├── config.ini                # OSGi framework config
    │   ├── logback.xml               # Logging configuration
    │   └── configs/                  # Health check configs
    │       ├── *.cfg                 # 6 health check files
    └── scripts/
        ├── run.sh                    # Linux/macOS launcher
        └── run.bat                   # Windows launcher
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
| `tycho-p2-director-plugin` | Materializes and archives products |
| `maven-antrun-plugin` | Post-processes products (config fix, file copying) |

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

```bash
# Full build with tests and verification
mvn clean verify

# Quick build (no tests)
mvn clean package -DskipTests

# Build specific module
mvn clean package -pl com.kk.pde.ds.api

# Resume failed build from distribution
mvn verify -rf :distribution

# Build with debug output
mvn clean verify -X
```

### Build Outputs

| Output | Location |
|--------|----------|
| **p2 Repository** | `distribution/target/repository/` |
| **Linux Archive** | `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz` |
| **Windows Archive** | `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip` |
| **macOS Archive** | `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz` |

---

## Target Platform

**File**: `com.kk.pde.ds.target/com.kk.pde.ds.target.target`

The target platform defines all OSGi bundles available during build and runtime.

### Dependency Categories

#### OSGi Core & Runtime
| Bundle | Version | Purpose |
|--------|---------|---------|
| `org.eclipse.osgi` | 3.22.0 | OSGi Framework |
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

---

## Declarative Services (DS)

OSGi Declarative Services provides dependency injection for OSGi services.

### DS Annotations Used

| Annotation | Purpose |
|------------|---------|
| `@Component` | Declares a class as a DS component |
| `@Reference` | Injects a service dependency |
| `@Activate` | Lifecycle method called when component is activated |

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
    private static Logger log = LoggerFactory.getLogger(Greet.class);

    @Activate
    public void start() {
        log.info("Greet.start()");
    }

    @Override
    public void greet() {
        log.info("Hello world!");
    }
}
```

**DS Descriptor**: `OSGI-INF/com.kk.pde.ds.imp.Greet.xml`
- Registers `Greet` as service providing `com.kk.pde.ds.api.IGreet`

#### 3. App Consumer

**File**: `com.kk.pde.ds.app/src/com/kk/pde/ds/app/App.java`

```java
@Component
public class App {
    private static Logger log = LoggerFactory.getLogger(App.class);
    private IGreet greet;

    @Reference
    public void setApi(IGreet greet) {
        log.info("App.setApi()");
        this.greet = greet;
    }

    @Activate
    public void start() {
        log.info("App.start()");
        McpSettings.load(log);  // Load settings file
        greet.greet();          // Use injected service
    }
}
```

**DS Descriptor**: `OSGI-INF/com.kk.pde.ds.app.App.xml`
- References `IGreet` interface via `setApi` bind method

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
            if (greetService == null) {
                resultLog.critical("IGreet service is not available");
                return new Result(resultLog);
            }
            greetService.greet();
            resultLog.info("IGreet service is available and operational");
            resultLog.info("Service implementation: {}", greetService.getClass().getName());
        } catch (Exception e) {
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
│  2. App component references satisfied                               │
│     - setApi(IGreet) called with Greet instance                     │
│     - @Activate start() called                                       │
│     - Calls greet.greet() → "Hello world!"                          │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  3. GreetServlet references satisfied                                │
│     - setGreetService(IGreet) called                                │
│     - Registered at /api/greet/* via HTTP Whiteboard                │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  4. GreetHealthCheck references satisfied                            │
│     - IGreet injected via @Reference field                          │
│     - Available for health check execution                          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## HTTP Whiteboard (REST API)

The REST API uses **OSGi HTTP Whiteboard** pattern (not JAX-RS).

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
| **Bundles** | List all 31 bundles with states (INSTALLED, RESOLVED, ACTIVE) |
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
1. Checks if `IGreet` service is available
2. Attempts to invoke `greet()` method
3. Returns OK if successful, CRITICAL if unavailable or throws exception

### File-Based Health Checks

All configurations are located in `distribution/configuration/configs/` and loaded by Felix File Install.

#### 1. Application Bundles Check

**File**: `org.apache.felix.hc.generalchecks.BundlesStartedCheck-app.cfg`

```properties
hc.name=Application Bundles Check
hc.tags=application,bundles
bundles.regex=com.kk.pde.ds.api|com.kk.pde.ds.imp|com.kk.pde.ds.app|com.kk.pde.ds.rest
```

Verifies all application bundles are ACTIVE.

#### 2. Core Bundles Check

**File**: `org.apache.felix.hc.generalchecks.BundlesStartedCheck-core.cfg`

```properties
hc.name=Core Bundles Check
hc.tags=system,bundles
bundles.regex=org.apache.felix.scr|org.apache.felix.configadmin|org.apache.felix.eventadmin|org.apache.felix.http.jetty
```

Verifies essential OSGi infrastructure bundles are ACTIVE.

#### 3. DS Components Check

**File**: `org.apache.felix.hc.generalchecks.DsComponentsCheck-app.cfg`

```properties
hc.name=DS Components Check
hc.tags=application,components
components.regex=com.kk.pde.ds.*
```

Verifies all application DS components are satisfied.

#### 4. REST API Check

**File**: `org.apache.felix.hc.generalchecks.HttpRequestsCheck-api.cfg`

```properties
hc.name=REST API Check
hc.tags=application,http
requests.json=["http://localhost:8080/api/greet"]
connectTimeoutInMs=3000
readTimeoutInMs=5000
```

Verifies REST endpoint is accessible and responding.

#### 5. Greet Service Available

**File**: `org.apache.felix.hc.generalchecks.ServicesCheck-greet.cfg`

```properties
hc.name=Greet Service Available
hc.tags=application,services
services.list=com.kk.pde.ds.api.IGreet
```

Verifies `IGreet` service is registered.

#### 6. Health Check Services

**File**: `org.apache.felix.hc.generalchecks.ServicesCheck-healthcheck.cfg`

```properties
hc.name=Health Check Services
hc.tags=system,services
services.list=org.apache.felix.hc.api.HealthCheck,org.osgi.service.event.EventAdmin
```

Verifies health check infrastructure is operational.

### Health Check Summary

| Check | Type | Tags | Purpose |
|-------|------|------|---------|
| Greet Service Health Check | Custom | application, greet | Verifies IGreet service works |
| Application Bundles Check | BundlesStarted | application, bundles | App bundles ACTIVE |
| Core Bundles Check | BundlesStarted | system, bundles | System bundles ACTIVE |
| DS Components Check | DsComponents | application, components | Components satisfied |
| REST API Check | HttpRequests | application, http | REST endpoint accessible |
| Greet Service Available | Services | application, services | IGreet registered |
| Health Check Services | Services | system, services | HC infrastructure operational |

---

## MCP Server (Model Context Protocol)

The MCP bundles implement a tool-call server following the [Model Context Protocol](https://modelcontextprotocol.io/) specification (JSON-RPC 2.0 over HTTP).

### Architecture

```
IMcpTool (service interface)
    ├── EchoTool        ─┐
    ├── GreetTool        ├── Register via @Reference (dynamic, 0..n)
    └── SystemInfoTool  ─┘
            │
    McpToolRegistry (collects all IMcpTool services)
            │
    McpServlet (/mcp endpoint, JSON-RPC 2.0)
```

### Built-in Tools

| Tool | Description | Input |
|------|-------------|-------|
| `echo` | Echoes a message back | `{"message": "text"}` |
| `greet` | Invokes IGreet service | `{"name": "optional"}` |
| `system_info` | Returns JVM/OS info | `{}` |

### Adding Custom Tools

Create a new `@Component` implementing `IMcpTool` in any bundle. It will be auto-discovered by the dynamic `@Reference` in `McpToolRegistry`.

---

## LLM Integration (OpenRouter / OpenAI-Compatible)

The `com.kk.pde.ds.mcp.llm` bundle bridges the MCP tool registry to OpenAI-compatible APIs.

### Agent Loop

`OpenRouterAgent` implements a tool-calling agent loop:
1. Sends user message + available tools to the LLM
2. If LLM responds with `tool_calls` → executes tool via OSGi registry, feeds result back
3. If LLM responds with `stop` → returns final text answer
4. Repeats up to 10 turns

### HTTP Endpoint

```bash
# Send a chat message via HTTP
curl -X POST http://localhost:8080/llm/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What tools do you have?", "model": "google/gemini-flash-1.5"}'
```

---

## Chatbot UI

The `com.kk.pde.ds.chatbot` bundle provides a Swing desktop chat interface.

### Features

- Multi-turn conversation with session history
- Model selection via editable dropdown (queries `/v1/models` endpoint)
- "Refresh Models" button to reload available models
- "Clear" button to reset conversation history
- Works with any OpenAI-compatible API (OpenRouter, LM Studio, etc.)
- MCP tool integration — LLM can use registered tools during conversations
- Non-blocking UI via `SwingWorker` (UI stays responsive during LLM calls)

### Configuration

| Source | Variable | Default | Purpose |
|--------|----------|---------|---------|
| Env var | `OPENROUTER_API_KEY` | — | API key (recommended) |
| System property | `openrouter.api.key` | — | API key (alternative, overrides env var) |
| System property | `openrouter.model` | `google/gemini-flash-1.5` | Default model ID |
| System property | `openrouter.base.url` | `https://openrouter.ai/api/v1` | API base URL |

### Service Wiring

```
OpenRouterAgent (@Reference IMcpToolRegistry)
        │
ChatService (@Reference OpenRouterAgent)
        │
App (@Reference ChatService) → launches ChatFrame on EDT
```

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

1. File Install watches `configuration/configs/` directory
2. On startup and every 5 seconds, scans for `.cfg` files
3. Creates/updates OSGi configurations via Config Admin
4. Health check bundles pick up configurations and instantiate checks

### Adding New Health Checks

1. Create a new `.cfg` file in `distribution/configuration/configs/`
2. Use factory PID naming: `<factory.pid>-<instance>.cfg`
3. Rebuild product or copy directly to running product's `configuration/configs/`

Example for a new bundle check:
```properties
# org.apache.felix.hc.generalchecks.BundlesStartedCheck-custom.cfg
hc.name=Custom Bundle Check
hc.tags=custom
bundles.regex=my.custom.bundle
```

---

## Settings Loader

### Overview

The `McpSettings` utility loads `settings/mcp.json` at application startup.

**File**: `com.kk.pde.ds.app/src/com/kk/pde/ds/app/McpSettings.java`

### Resolution Order

The loader checks these locations in order (first match wins):

| Priority | Source | Example |
|----------|--------|---------|
| 1 | System property `-Dmcp.settings.dir=<dir>` | `<dir>/mcp.json` |
| 2 | OSGi install area (`osgi.install.area`) | `<install>/settings/mcp.json` |
| 3 | Working directory | `./settings/mcp.json` |
| 4 | Bundle classpath | `/settings/mcp.json` |

### Security

- Logs file source and byte count
- **Does NOT log file contents** (to avoid leaking secrets)
- Warnings logged if file not found

### Usage Example

```java
@Activate
public void start() {
    String mcpJson = McpSettings.load(log);
    // Parse and use settings...
}
```

### Custom Settings Directory

```bash
# Specify custom settings directory
java -Dmcp.settings.dir=/custom/settings -jar plugins/org.eclipse.osgi_*.jar ...
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

    <!-- Default logging level -->
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

Example output:
```
14:30:15.123 [main] INFO  com.kk.pde.ds.imp.Greet - Greet.start()
14:30:15.124 [main] INFO  com.kk.pde.ds.app.App - App.setApi()
14:30:15.125 [main] INFO  com.kk.pde.ds.app.App - App.start()
14:30:15.126 [main] INFO  com.kk.pde.ds.imp.Greet - Hello world!
```

### Changing Log Levels

Edit `logback.xml` in the product's `configuration/` directory:

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
| **0** | http.jetty, http.servlet-api, webconsole, webconsole.plugins.ds, inventory | HTTP infrastructure |
| **1** | osgi, configadmin, metatype | Core framework |
| **2** | scr, service.component, fileinstall, healthcheck.api, healthcheck.core, eventadmin | Services |
| **3** | gogo.*, equinox.console, healthcheck.generalchecks, healthcheck.webconsoleplugin | Console & plugins |
| **4** | api, imp, app, rest | Application bundles |

### Included Plugins (31 total)

**Application (4)**:
- `com.kk.pde.ds.api`
- `com.kk.pde.ds.imp`
- `com.kk.pde.ds.app`
- `com.kk.pde.ds.rest`

**Framework (27)**:
- OSGi: `org.eclipse.osgi`, `org.eclipse.equinox.console`
- Felix: scr, gogo.*, http.*, webconsole.*, configadmin, metatype, fileinstall, eventadmin, healthcheck.*, inventory
- OSGi Services: service.component, service.component.annotations, util.function, util.promise, service.event
- Apache Commons: commons-fileupload, commons-io

---

## Running the Application

> **Java requirement**: Java 17+ is required at runtime. Eclipse Equinox 3.22 does not support Java 8 even though our bundles are compiled to Java 8 bytecode.

### Method 1: Run Scripts from Source Tree (Recommended)

After `mvn clean verify`, run the launcher script directly from the repo root. The scripts auto-detect your OS and find the correct built product directory — no extracting archives needed.

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

The same scripts also work when placed inside an extracted product archive (they detect `plugins/` next to them):

**Linux product:**
```bash
cd distribution/target/products/com.kk.pde.ds.product/linux/gtk/x86_64
./run.sh
```

**macOS product** (note: the macOS build wraps everything inside an `.app` bundle):
```bash
cd distribution/target/products/com.kk.pde.ds.product/macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse
./run.sh
```

**Windows product:**
```cmd
cd distribution\target\products\com.kk.pde.ds.product\win32\win32\x86_64
run.bat
```

### Method 3: Manual Launch

From inside any product directory (where `plugins/` and `configuration/` live):

```bash
java -jar plugins/org.eclipse.osgi_*.jar \
     -configuration configuration \
     -console \
     -consoleLog
```

**Important**: The `-configuration configuration` flag is **required** to point the OSGi framework to the `config.ini` file.

### Run Script Details

Both scripts use the same dual-mode detection logic:

1. If `plugins/` exists next to the script → running from inside a product, use current directory
2. Otherwise → running from `distribution/scripts/` in the source tree, navigate to the built product for the current OS

**run.sh (macOS/Linux):**
```bash
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$SCRIPT_DIR/plugins" ]; then
    PRODUCT_DIR="$SCRIPT_DIR"
else
    BASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
    case "$(uname -s)" in
        Darwin) PRODUCT_DIR="$BASE_DIR/target/products/.../Eclipse.app/Contents/Eclipse" ;;
        Linux)  PRODUCT_DIR="$BASE_DIR/target/products/.../linux/gtk/x86_64" ;;
    esac
fi
OSGI_JAR=$(ls "$PRODUCT_DIR/plugins/org.eclipse.osgi_"*.jar 2>/dev/null | head -1)
cd "$PRODUCT_DIR"
java -jar "$OSGI_JAR" -configuration configuration -console -consoleLog "$@"
```

**run.bat (Windows):**
```batch
@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
if exist "%SCRIPT_DIR%\plugins\" (
    set "PRODUCT_DIR=%SCRIPT_DIR%"
) else (
    for %%A in ("%SCRIPT_DIR%\..")  do set "BASE_DIR=%%~fA"
    set "PRODUCT_DIR=%BASE_DIR%\target\products\...\win32\win32\x86_64"
)
for %%f in ("%PRODUCT_DIR%\plugins\org.eclipse.osgi_*.jar") do set OSGI_JAR=%%f
cd /d "%PRODUCT_DIR%"
java -jar "%OSGI_JAR%" -configuration configuration -console -consoleLog %*
```

### Passing Additional Arguments

```bash
# Pass custom system properties
./run.sh -Dmcp.settings.dir=/custom/settings

# Pass JVM arguments
./run.sh -Xmx512m -Xms256m
```

### Product Directory Structure

```
com.kk.pde.ds.product/
├── plugins/                    # 31 OSGi bundles
│   ├── org.eclipse.osgi_3.22.0.v20241030-2121.jar
│   ├── org.apache.felix.scr_2.2.12.jar
│   ├── com.kk.pde.ds.api_1.0.0.*.jar
│   └── ... (28 more bundles)
├── configuration/              # Framework configuration
│   ├── config.ini              # OSGi config
│   ├── logback.xml             # Logging config
│   └── configs/                # Health check configs
│       ├── *.cfg               # 6 health check files
├── settings/                   # Runtime settings
│   └── mcp.json
├��─ run.sh                      # Linux/macOS launcher
└── run.bat                     # Windows launcher
```

---

## Fat JAR (Single Executable)

The `fatjar/` module packages the entire application — Equinox framework, all 37 OSGi bundles, and configuration — into a single 8.4 MB executable JAR. No installation or product directory needed.

### How It Works

The fat JAR uses a **reflection-based embedded Equinox** approach:

1. `Main.java` extracts all bundle JARs from `bundles/` inside the fat JAR to a temp directory
2. Loads the Equinox framework JAR via `URLClassLoader` (keeps OSGi classes off the app classpath)
3. Boots the framework using `FrameworkFactory` SPI via `ServiceLoader` + reflection
4. Installs all bundles with correct start levels matching the product definition
5. Starts everything, waits for shutdown, cleans up the temp directory

> **Why reflection?** Unpacking Equinox onto the flat classpath would put `org.osgi.framework.*` packages on both the app classpath and the system bundle, breaking the OSGi resolver. The reflection approach keeps Main.java completely OSGi-free — zero `org.osgi` imports.

### Build

The fat JAR is a **standalone Maven module** (not part of the Tycho reactor). Build the main project first, then the fat JAR:

```bash
# 1. Build all bundles + product
mvn clean verify

# 2. Build the fat JAR (collects bundles from the built product)
cd fatjar
mvn clean package
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

Once running, the same services are available as the product build:

| Service | URL |
|---------|-----|
| REST API | http://localhost:8080/api/greet |
| WebConsole | http://localhost:8080/system/console (admin/admin) |
| MCP Server | http://localhost:8080/mcp |
| LLM Chat | http://localhost:8080/llm/chat (POST) |

### OSGi Console

The Gogo shell console is available on stdin/stdout by default. Use `ss` to list bundles and `scr:list` to inspect DS components. For remote access, start with `-Dosgi.console.port=5555` and connect via telnet.

### Platform Notes

The fat JAR is cross-platform — the same JAR runs on macOS, Linux, and Windows with Java 17+. The `pom.xml` includes OS-detection profiles to find the built product plugins directory automatically.

---

## Remote Debugging

### Enable JDWP

Add the debug agent to the Java command:

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar plugins/org.eclipse.osgi_3.22.0.v20241030-2121.jar \
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

```bash
# Linux/macOS - pass JVM args through the script
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar "$OSGI_JAR" -configuration configuration -console -consoleLog

# Or modify run.sh to include debug by default
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
   - Select repository root
   - Import all projects

2. **Configure Target Platform**
   - Window → Preferences → Plug-in Development → Target Platform
   - Add → Nothing → Next
   - Add → File System → Browse to `com.kk.pde.ds.target/com.kk.pde.ds.target.target`
   - Or: Add → Software Site → Browse to `com.kk.pde.ds.target/com.kk.pde.ds.target.target`
   - Set as Active Target Platform

3. **Create Run Configuration**
   - Run → Run Configurations → OSGi Framework → New
   - Select bundles to include
   - Program arguments: `-console -consoleLog`
   - VM arguments: `-Dlogback.configurationFile=distribution/configuration/logback.xml`

4. **Debug**
   - Use Debug on the run configuration
   - Or add remote debug agent and attach

### IntelliJ IDEA

**Recommended approach**: Run the built product and attach remote debugger.

1. **Build**
   ```bash
   mvn clean verify
   ```

2. **Run with Debug**
   ```bash
   cd distribution/target/products/com.kk.pde.ds.product
   java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
        -jar plugins/org.eclipse.osgi_*.jar \
        -configuration configuration -console -consoleLog
   ```

3. **Attach Debugger**
   - Run → Edit Configurations → Remote JVM Debug
   - Connect to `localhost:5005`

**Alternative: Direct Launch**

1. Create JAR Application configuration
2. JAR path: `<product>/plugins/org.eclipse.osgi_*.jar`
3. Program args: `-configuration configuration -console -consoleLog`
4. VM options: `-Dlogback.configurationFile=configuration/logback.xml`
5. Working directory: `<product root>`

### VS Code

1. Install Java Extension Pack
2. Open repository folder
3. Use Maven for building: `mvn clean verify`
4. Attach debugger using launch configuration

---

## OSGi Console Commands

When running with `-console -consoleLog`, you get access to the Gogo shell.

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

```
osgi> lb
START LEVEL 4
   ID|State      |Level|Name
    0|Active     |    0|System Bundle (3.22.0.v20241030-2121)
    1|Active     |    1|org.apache.felix.configadmin (1.9.26)
    2|Active     |    2|org.apache.felix.scr (2.2.12)
   ...
   28|Active     |    4|com.kk.pde.ds.api (1.0.0.qualifier)
   29|Active     |    4|com.kk.pde.ds.imp (1.0.0.qualifier)
   30|Active     |    4|com.kk.pde.ds.app (1.0.0.qualifier)
   31|Active     |    4|com.kk.pde.ds.rest (1.0.0.qualifier)

osgi> scr:list
 BundleId Component Name                                         Default State
    [29] com.kk.pde.ds.imp.Greet                                 active
    [29] com.kk.pde.ds.imp.GreetHealthCheck                      active
    [30] com.kk.pde.ds.app.App                                   active
    [31] com.kk.pde.ds.rest.GreetServlet                         active

osgi> services "(objectClass=com.kk.pde.ds.api.IGreet)"
{com.kk.pde.ds.api.IGreet}={component.name=com.kk.pde.ds.imp.Greet, ...}
  Registered by bundle: com.kk.pde.ds.imp [29]
```

---

## Troubleshooting

### Bundle Not Starting

**Symptoms**: Bundle shows as INSTALLED or RESOLVED instead of ACTIVE

**Checks**:
1. Check dependencies: `headers <bundle-id>`
2. Look for missing imports in the output
3. Verify target platform includes required bundles

### Service Not Available

**Symptoms**: `@Reference` not satisfied, component not active

**Checks**:
1. Run `scr:list` to see component states
2. Run `scr:info <component>` for detailed reference info
3. Check if the providing bundle is ACTIVE
4. Verify the service interface matches exactly

### WebConsole Not Accessible

**Symptoms**: Cannot connect to http://localhost:8080/system/console

**Checks**:
1. Verify `org.apache.felix.http.jetty` is ACTIVE
2. Check `config.ini` for `org.osgi.service.http.port=8080`
3. Verify `org.apache.felix.http.enable=true`
4. Check for port conflicts

### Health Checks Not Showing

**Symptoms**: Custom health checks not appearing in WebConsole

**Checks**:
1. Verify `felix.fileinstall.dir=configuration/configs`
2. Check File Install is ACTIVE: `lb | grep fileinstall`
3. Verify `.cfg` files are in the correct directory
4. Check file naming: `<factory.pid>-<instance>.cfg`

### Settings Not Loading

**Symptoms**: `mcp.json` not found warnings

**Checks**:
1. Verify `settings/mcp.json` exists in product root
2. Check resolution order (see Settings Loader section)
3. Use `-Dmcp.settings.dir=<path>` to specify location
4. Check working directory when launching

### Configuration Not Applied

**Symptoms**: Changes to `config.ini` or `logback.xml` not taking effect

**Checks**:
1. Verify `-configuration configuration` flag is present
2. Check file is in `configuration/` directory (not `config/`)
3. For logback: verify `-Dlogback.configurationFile=configuration/logback.xml`

### Common config.ini Issues

```properties
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
| `com.kk.pde.ds.target/*.target` | Target platform definition |

### Product Configuration

| File | Purpose |
|------|---------|
| `distribution/p2.product` | Product definition with bundles and start levels |
| `distribution/category.xml` | p2 repository category structure |
| `distribution/configuration/config.ini` | OSGi framework configuration |
| `distribution/configuration/logback.xml` | Logging configuration |
| `distribution/configuration/configs/*.cfg` | Health check configurations |

### Launcher Scripts

| File | Purpose |
|------|---------|
| `distribution/scripts/run.sh` | Linux/macOS launcher |
| `distribution/scripts/run.bat` | Windows launcher |

### Application Sources

| File | Purpose |
|------|---------|
| `com.kk.pde.ds.api/src/.../IGreet.java` | Service interface |
| `com.kk.pde.ds.imp/src/.../Greet.java` | Service implementation |
| `com.kk.pde.ds.imp/src/.../GreetHealthCheck.java` | Custom health check |
| `com.kk.pde.ds.app/src/.../App.java` | Service consumer |
| `com.kk.pde.ds.app/src/.../McpSettings.java` | Settings loader |
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
| `settings/mcp.json` | Application settings file |

---

## Statistics

| Metric | Count |
|--------|-------|
| Maven Modules | 13 (12 active, 1 test disabled) |
| Application Bundles | 9 (api, imp, app, rest, mcp.api, mcp.server, mcp.client, mcp.llm, chatbot) |
| Framework Bundles | 27 |
| DS Components | 8 |
| MCP Tools | 3 (echo, greet, system_info) |
| Health Checks | 7 (1 custom + 6 file-based) |
| Java Source Files | 18 |
| Configuration Files | 9 (.ini, .xml, .cfg) |
| Shell Scripts | 2 |
| Build Environments | 3 (Linux, Windows, macOS) |
| Start Levels | 5 (0-4) |
| Target Dependencies | ~50 Maven coordinates |

---

## License

[Add your license information here]

---

## Contributing

[Add contributing guidelines here]
