# Fat JAR

This document tells you how to build and run the application as one executable JAR. It also describes how the launcher starts Equinox and how it differs from the p2 product.

Contents:

- [Overview](#overview)
- [How it works](#how-it-works)
- [Start levels](#start-levels)
- [Build](#build)
- [Run](#run)
- [Services available](#services-available)
- [OSGi console](#osgi-console)
- [Platform notes](#platform-notes)
- [Known gaps](#known-gaps)

Related documents:

- [build.md](build.md): the Tycho build, the target platform and the product definition.
- [running.md](running.md): how to run the p2 products.
- [architecture.md](architecture.md): component and service wiring.
- [../README.md](../README.md): project overview.
- [../SECURITY.md](../SECURITY.md): known exposures of the localhost HTTP surface.

---

## Overview

The `fatjar/` module puts the full application into one executable JAR. The JAR contains the Equinox framework, all JARs from the `plugins/` directory of the built product, and the configuration. You do not need an installation or a product directory.

The fat JAR is a second delivery model, next to the p2 product. It is a plain Maven module, not a Tycho module. It is not part of the Tycho reactor.

---

## How it works

`com.kk.pde.ds.fatjar.Main` starts an **embedded Equinox through reflection**:

1. `Main` extracts all bundle JARs from `bundles/` inside the fat JAR to a temporary directory (`osgi-fatjar-*`). It also extracts `configuration/`.
2. It sets the `logback.configurationFile` system property to the extracted `logback.xml`.
3. It loads the Equinox framework JAR (`org.eclipse.osgi_*.jar`) through a `URLClassLoader`. The OSGi classes stay off the application classpath.
4. It starts the framework with the `FrameworkFactory` SPI, through `ServiceLoader` and reflection.
5. It installs all bundles and sets the start levels from its own table. See [Start levels](#start-levels).
6. It starts the bundles, but not the fragments. Then it sets the framework start level to 4.
7. It waits for shutdown. A shutdown hook stops the framework and deletes the temporary directory.

> **Why reflection?** If you unpack Equinox onto the flat classpath, the `org.osgi.framework.*` packages are on the application classpath and in the system bundle. This breaks the OSGi resolver. With reflection, `Main.java` has no OSGi code: there are no `org.osgi` imports.

`Main` sets these framework properties:

| Property | Value |
|----------|-------|
| `osgi.noShutdown`, `eclipse.ignoreApp` | `true` |
| `org.osgi.framework.storage` | `osgi-cache` in the temporary directory, cleaned on first init |
| `org.osgi.framework.startlevel.beginning` | `4` |
| `osgi.console` | The value of `-Dosgi.console.port`. Empty means stdin and stdout. |
| `org.osgi.service.http.port` | `8080` |
| `felix.webconsole.username`, `felix.webconsole.password` | `admin`, `admin` |
| `felix.fileinstall.dir` | `configuration/configs` in the temporary directory |
| `openrouter.api.key`, `openrouter.model`, `openrouter.base.url` | From the `-D` system property. If the property is not set, from the environment variable (`OPENROUTER_API_KEY`, `OPENROUTER_MODEL`, `OPENROUTER_BASE_URL`). |

The assembly descriptor (`fatjar/src/assembly/fatjar.xml`) puts these items into the JAR:

- The compiled `Main` class.
- All `*.jar` files from the `plugins/` directory of the built product, in `bundles/`.
- `fatjar/src/main/resources/configuration/logback.xml`. This file is not the same file as `distribution/configuration/logback.xml`.
- The health check files `distribution/configuration/configs/*.cfg`, in `configuration/configs/`.

The JAR contains only the main product. The ECF and spike bundles are not in it.

---

## Start levels

`Main` has its own start-level table. The Javadoc of the table says that it matches `p2.product`, but it does not match fully. The real behaviour is as follows.

| Bundles | Fat JAR (`Main`) | Built product (`config.ini`) |
|---------|------------------|------------------------------|
| configadmin, metatype | 1, start | 1, start |
| scr, service.component, service.component.annotations, util.function, util.promise, eventadmin, service.event, fileinstall, healthcheck.api, healthcheck.core | 2, start | 2, start |
| gogo.*, equinox.console, healthcheck.generalchecks, healthcheck.webconsoleplugin | 3, start | 3, start |
| http.jetty, http.servlet-api, webconsole, webconsole.plugins.ds, inventory | 4, start | 4, start. `p2.product` sets level 0, which means the default level 4. |
| api, imp, app, rest, mcp.api, mcp.server, mcp.client, mcp.llm, chatbot | 4, start | 4, start |
| `com.kk.pde.ds.rag` | No entry: the framework's initial bundle start level, start | 4, start |
| pdfbox, fontbox, commons-logging | No entry: the framework's initial bundle start level, start | 4, no start |
| logback (classic, core), slf4j, commons-fileupload, commons-io | 4, no start | 4, no start |

The real differences are:

- `Main` has no entry for `com.kk.pde.ds.rag`, pdfbox, fontbox and commons-logging. `Main` does not set a start level for these bundles, so they keep the framework's initial bundle start level.
- `Main` starts each bundle that has no entry. Thus pdfbox, fontbox and commons-logging start in the fat JAR. In the product, they do not start automatically.

For the product table, see [build.md](build.md#bundle-start-levels).

---

## Build

Build the main project first. The fat JAR collects the bundles from the built product.

1. Build all bundles and the product:

   ```bash
   ./mvnw clean verify
   ```

2. Build the fat JAR:

   ```bash
   ./mvnw -f fatjar clean package
   ```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

Output: `fatjar/target/osgi-fatjar.jar`

---

## Run

macOS and Linux:

```bash
# Basic
java -jar fatjar/target/osgi-fatjar.jar

# With the API key for the chatbot and LLM features
OPENROUTER_API_KEY=your_key java -jar fatjar/target/osgi-fatjar.jar

# With a telnet console on port 5555
java -Dosgi.console.port=5555 -jar fatjar/target/osgi-fatjar.jar

# With the convenience script (it builds the JAR if the JAR does not exist)
./fatjar/run.sh
```

Windows (cmd.exe):

```bat
java -jar fatjar\target\osgi-fatjar.jar

rem With the API key for the chatbot and LLM features
set OPENROUTER_API_KEY=your_key
java -jar fatjar\target\osgi-fatjar.jar
```

In PowerShell, set the variable with `$env:OPENROUTER_API_KEY = "your_key"`.

---

## Services available

The fat JAR gives the same services as the product build: WebConsole, REST, MCP and the chatbot.

| Service | URL |
|---------|-----|
| REST API | http://localhost:8080/api/greet |
| WebConsole | http://localhost:8080/system/console (admin/admin) |
| MCP server | http://localhost:8080/mcp |
| LLM chat | http://localhost:8080/llm/chat (POST) |

These endpoints have no authentication. Use them on localhost only. See [../SECURITY.md](../SECURITY.md).

---

## OSGi console

By default, the Gogo shell console uses stdin and stdout. Use `ss` to list the bundles. Use `scr:list` to examine the DS components.

For remote access, start the JAR with `-Dosgi.console.port=5555`. Then connect to port 5555 with telnet.

---

## Platform notes

The same fat JAR runs on macOS, Linux and Windows with Java 8 or newer. `Main` and the project bundles use Java 8 bytecode.

The `fatjar/pom.xml` file has OS profiles. Each profile finds the `plugins/` directory of the built product for the current operating system:

| Build OS | `plugins/` directory |
|----------|----------------------|
| macOS (default, no profile) | `distribution/target/products/com.kk.pde.ds.product/macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse/plugins` |
| Linux (profile `linux`) | `distribution/target/products/com.kk.pde.ds.product/linux/gtk/x86_64/plugins` |
| Windows (profile `windows`) | `distribution/target/products/com.kk.pde.ds.product/win32/win32/x86_64/plugins` |

---

## Known gaps

- `fatjar/run.sh` runs the system `mvn` (`mvn clean package -q`), not the Maven Wrapper. Thus the script can use a different Maven version than the rest of the project. It also does not build the main product first. Build the fat JAR with `./mvnw -f fatjar clean package` instead.
- `fatjar/run.sh` is a bash script. There is no Windows version.
- The Javadoc of the start-level table in `Main.java` says that the table matches `p2.product`. See [Start levels](#start-levels) for the differences.
