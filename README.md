# OSGi DS Application (Java 8) - Comprehensive Documentation

## Overview
This workspace contains a complete Java 8 OSGi application built with Maven Tycho. It demonstrates:
- OSGi Declarative Services (DS) with clear API/implementation/consumer separation
- HTTP Whiteboard servlet (REST-like endpoints)
- Felix WebConsole and DS plugin
- Felix Health Checks (custom + general checks)
- File-based configuration via Felix File Install
- Target platform definition (Tycho + .target)
- Product builds for Linux/Windows/macOS
- Remote debugging support
- Logback logging configuration
- Settings loader that reads `settings/mcp.json`

This document describes every major piece, how it fits together, and how to build/run/debug the app.

## Requirements
- Java 8 (or newer, but bundles are compiled for Java 8)
- Maven 3.x
- Internet access for Maven/Tycho to resolve target platform dependencies

## Repository Layout

Top-level modules (Maven Tycho):
- `com.kk.pde.ds.target`: Target platform definition (Tycho target)
- `com.kk.pde.ds.api`: Service API bundle (IGreet)
- `com.kk.pde.ds.imp`: Service implementation bundle (Greet + HealthCheck)
- `com.kk.pde.ds.app`: Service consumer bundle (App)
- `com.kk.pde.ds.rest`: HTTP Whiteboard servlet exposing REST-like endpoints
- `com.kk.pde.ds.feature`: Feature containing the bundles
- `distribution`: p2 repository + product build definitions

Support directories:
- `settings`: file-based settings (currently `mcp.json`)
- `distribution/configuration`: product configuration (config.ini, logback.xml, health check configs)
- `distribution/scripts`: launch scripts for built products

## Architecture at a Glance

Service binding flow:
1. `com.kk.pde.ds.imp.Greet` registers `IGreet`.
2. `com.kk.pde.ds.app.App` declares an `@Reference` to `IGreet`.
3. Felix SCR injects `Greet` into `App`.
4. `App.start()` calls `greet()` and logs output.

HTTP flow:
- `com.kk.pde.ds.rest.GreetServlet` is registered via HTTP Whiteboard and exposes endpoints at `/api/greet/*`.

Health checks:
- `com.kk.pde.ds.imp.GreetHealthCheck` is an OSGi HealthCheck service.
- Additional general checks are configured via File Install (`distribution/configuration/configs`).

## Declarative Services (DS)

Components are defined in `OSGI-INF/*.xml`:
- `com.kk.pde.ds.app.App` (consumer, activates on startup)
- `com.kk.pde.ds.imp.Greet` (provider)
- `com.kk.pde.ds.imp.GreetHealthCheck` (health check)
- `com.kk.pde.ds.rest.GreetServlet` (HTTP Whiteboard servlet)

OSGi metadata is in each bundle's `META-INF/MANIFEST.MF` and referenced by Tycho build properties.

## HTTP Whiteboard (REST-like API)

The REST-like servlet is in `com.kk.pde.ds.rest` and uses OSGi HTTP Whiteboard:
- Servlet pattern: `/api/greet/*`
- Servlet name: `GreetServlet`

Endpoints:
- `GET /api/greet` -> returns a JSON greeting
- `GET /api/greet/{name}` -> personalized greeting
- `POST /api/greet` -> echoes posted body

Sample commands:
```bash
curl http://localhost:8080/api/greet
curl http://localhost:8080/api/greet/World
curl -X POST http://localhost:8080/api/greet -d '{"message":"Test"}'
```

Response format:
```json
{"message":"Hello from OSGi HTTP Whiteboard!","timestamp":1737734567890}
```

Note: This implementation uses the OSGi HTTP Whiteboard servlet API, not JAX-RS Whiteboard. If JAX-RS Whiteboard is required, additional bundles and an implementation would need to be added to the target platform and product.

## Felix WebConsole

WebConsole is included via target platform and product definition:
- URL (default): `http://localhost:8080/system/console`
- Credentials: `admin` / `admin`

The DS plugin is included, so you can inspect DS components and their state in the console.

## Health Checks

Included components and configs:
- Custom DS health check: `com.kk.pde.ds.imp.GreetHealthCheck`
- Felix Health Check Core + General Checks + WebConsole plugin

File-based health check configs (loaded by File Install):
- `distribution/configuration/configs/org.apache.felix.hc.generalchecks.BundlesStartedCheck-app.cfg`
- `distribution/configuration/configs/org.apache.felix.hc.generalchecks.BundlesStartedCheck-core.cfg`
- `distribution/configuration/configs/org.apache.felix.hc.generalchecks.DsComponentsCheck-app.cfg`
- `distribution/configuration/configs/org.apache.felix.hc.generalchecks.HttpRequestsCheck-api.cfg`
- `distribution/configuration/configs/org.apache.felix.hc.generalchecks.ServicesCheck-greet.cfg`
- `distribution/configuration/configs/org.apache.felix.hc.generalchecks.ServicesCheck-healthcheck.cfg`

These are copied into the product `configuration/configs` directory during product build.

## File-Based Configuration (Felix File Install)

`distribution/configuration/config.ini` configures File Install:
- `felix.fileinstall.dir=configuration/configs`
- `felix.fileinstall.poll=5000`
- `felix.fileinstall.noInitialDelay=true`

Any `.cfg` files placed in `configuration/configs` will be picked up and applied automatically.

## Settings Loader (settings/mcp.json)

`com.kk.pde.ds.app.McpSettings` loads `settings/mcp.json` at runtime. Resolution order:
1. `-Dmcp.settings.dir=<dir>` -> `<dir>/mcp.json`
2. `osgi.install.area` -> `<install>/settings/mcp.json`
3. Working directory -> `./settings/mcp.json`
4. Bundle classpath -> `/settings/mcp.json`

The loader logs the source and byte count but does not log the contents (to avoid leaking secrets).

Default settings file:
- `settings/mcp.json`

Product build copies `settings/mcp.json` into each product at `settings/mcp.json`.

## Logging

Logback is used for logging. Configuration:
- `distribution/configuration/logback.xml`
- Applied via `-Dlogback.configurationFile=configuration/logback.xml`

Default levels:
- `INFO` for application packages (`com.kk.pde.ds`)
- `WARN` for Felix/Jetty to reduce noise

## Build Instructions

From the repository root:

Full build (recommended):
```bash
mvn clean verify
```

Quick build without tests:
```bash
mvn clean package -DskipTests
```

Build a specific module:
```bash
mvn clean package -pl com.kk.pde.ds.api
```

Resume a failed build from distribution:
```bash
mvn verify -rf :distribution
```

Build outputs:
- p2 repository: `distribution/target/repository/`
- Product archives:
  - `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz`
  - `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip`
  - `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz`

## Running the Application

### From a built product (recommended)
1. Extract the platform-specific archive from `distribution/target/products`.
2. Run the launcher script inside the extracted product.

Windows:
```cmd
run.bat
```

Linux/macOS:
```bash
./run.sh
```

### Manual launch (inside product root)
```bash
java -jar plugins/org.eclipse.osgi_3.22.0.v20241030-2121.jar -configuration configuration -console -consoleLog
```

The `-configuration configuration` flag ensures the framework uses the correct `config.ini`.

## Remote Debugging

Enable JDWP by adding the agent flag:
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar plugins/org.eclipse.osgi_3.22.0.v20241030-2121.jar -configuration configuration -console -consoleLog
```

## IDE Run/Debug

### Eclipse PDE
Recommended for development because it understands OSGi bundles and target platforms.

Setup:
1. Import the projects into your workspace.
2. Window > Preferences > Plug-in Development > Target Platform:
   - Add target definition from `com.kk.pde.ds.target/com.kk.pde.ds.target.target`
   - Set it as the active target
3. Run > Run Configurations... > OSGi Framework

Suggested launch settings:
- Program arguments: `-console -consoleLog`
- VM arguments (optional but useful):
  - `-Dlogback.configurationFile=distribution/configuration/logback.xml`
  - `-Dmcp.settings.dir=<repo>/settings` (or set working directory to repo root)

Debug in PDE:
- Use "Debug" on the same launch configuration, or add:
  `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`
  and attach via "Remote Java Application" on `localhost:5005`.

### IntelliJ IDEA
Recommended path is to run the built product and attach a remote debugger.

Run (built product):
1. Build with `mvn clean verify`.
2. Extract the product archive and run `run.bat` or `run.sh`.

Attach debugger:
- Run/Debug Configurations... > Remote JVM Debug
- Host: `localhost`
- Port: `5005`

If you want to launch directly from IntelliJ:
- Create a "JAR Application" configuration
- JAR path: `<product>/plugins/org.eclipse.osgi_*.jar`
- Program args: `-configuration configuration -console -consoleLog`
- VM options (optional): `-Dlogback.configurationFile=configuration/logback.xml`
- Working directory: `<product root>`

## OSGi Console (Gogo)

The product includes Gogo runtime/shell. With `-console -consoleLog`, you can use the OSGi console, for example:
- `lb` (list bundles)
- `services` (list services)
- `scr:list` (list DS components)

## Product Definition and Start Levels

Product configuration: `distribution/p2.product`
- Includes all application bundles plus Felix/OSGi runtime bundles
- Explicit start levels are defined for system, runtime, and app bundles
- WebConsole, HTTP, and Health Check bundles are auto-started

Configuration is aligned with `distribution/configuration/config.ini`.

## Target Platform (Tycho)

Target definition: `com.kk.pde.ds.target/com.kk.pde.ds.target.target`

Key included dependencies:
- OSGi framework: `org.eclipse.osgi`
- DS runtime: `org.apache.felix.scr`
- Gogo shell/command/runtime
- HTTP Whiteboard: `org.apache.felix.http.jetty` + servlet API
- WebConsole + DS plugin + inventory
- Config Admin + Metatype + File Install
- Health Check API, core, general checks, WebConsole plugin
- Logback (with transitive dependencies)

The target is used by Tycho to resolve all bundles and build the p2 repository and products.

## Tests

Module `com.kk.pde.ds.imp.tests` exists but is currently disabled in the root `pom.xml` until JUnit 5 bundles are added to the target platform.

## Troubleshooting

Common checks:
- Verify `config.ini` is used: `-configuration configuration`
- Ensure `org.eclipse.osgi_*.jar` exists under `plugins/` in product root
- WebConsole credentials are set in `distribution/configuration/config.ini`
- Confirm `felix.fileinstall.dir` points to `configuration/configs`

## File Reference Map (Key Files)

- Root build: `pom.xml`
- Target platform: `com.kk.pde.ds.target/com.kk.pde.ds.target.target`
- Product definition: `distribution/p2.product`
- p2 category: `distribution/category.xml`
- Product config: `distribution/configuration/config.ini`
- Logging config: `distribution/configuration/logback.xml`
- Health check configs: `distribution/configuration/configs/*.cfg`
- Run scripts: `distribution/scripts/run.bat`, `distribution/scripts/run.sh`
- Settings: `settings/mcp.json`
- DS components: `*/OSGI-INF/*.xml`
- Bundle manifests: `*/META-INF/MANIFEST.MF`
