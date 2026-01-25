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

Extract a product archive, cd into it, and run the launcher script:

**Windows:**
```cmd
run.bat
```

**Linux/macOS:**
```bash
./run.sh
```

**Or manually (Java 8+ compatible):**
```bash
java -jar plugins/org.eclipse.osgi_3.22.0.v20241030-2121.jar -configuration configuration -console -consoleLog
```

**With remote debugging:**
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar plugins/org.eclipse.osgi_3.22.0.v20241030-2121.jar -configuration configuration -console -consoleLog
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
com.kk.pde.ds.app      → App consumer (@Reference injection)
com.kk.pde.ds.rest     → REST API via HTTP Whiteboard (@Reference injection)
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

- **Java 8+** (application bundles are Java 8 compatible)
- **Tycho 4.0.13** (Maven OSGi build)
- **Eclipse 2024-12** target platform
- **OSGi Declarative Services** via annotations
- **Felix SCR** runtime
- **Felix HTTP Jetty** with HTTP Whiteboard for REST API
- **Felix WebConsole** with DS plugin
- **Logback** for logging (default level: INFO)
