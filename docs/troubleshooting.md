# Troubleshooting

This document lists known problems with the build and the running product. Each entry gives the symptom, the cause and the fix.

## Contents

- [Build problems](#build-problems)
  - [Maven 3.10 cannot build this repository](#maven-310-cannot-build-this-repository)
  - [An old JDK cannot start Tycho](#an-old-jdk-cannot-start-tycho)
  - [`-pl X -am` cannot find an OSGi bundle](#-pl-x--am-cannot-find-an-osgi-bundle)
- [Start problems](#start-problems)
  - [The script cannot find the OSGi jar](#the-script-cannot-find-the-osgi-jar)
  - [Configuration has no effect](#configuration-has-no-effect)
  - [Port 8080 is already in use](#port-8080-is-already-in-use)
- [Bundle and service problems](#bundle-and-service-problems)
  - [A bundle does not start](#a-bundle-does-not-start)
  - [A service is not available](#a-service-is-not-available)
- [WebConsole and health check problems](#webconsole-and-health-check-problems)
  - [The WebConsole is not accessible](#the-webconsole-is-not-accessible)
  - [The Terminal page shows no output](#the-terminal-page-shows-no-output)
  - [The health checks do not show](#the-health-checks-do-not-show)
- [LLM and chatbot problems](#llm-and-chatbot-problems)
  - [`No endpoints found for <model>`](#no-endpoints-found-for-model)
- [Problems in other documents](#problems-in-other-documents)
- [Related documents](#related-documents)

## Build problems

### Maven 3.10 cannot build this repository

**Symptom:** the build stops at the module `com.kk.pde.ds.chatbot` with this error:

```text
[ERROR] Invalid Collect Request: null -> [... com.kk.pde.ds:com.kk.pde.ds.chatbot:jar:lib/commonmark-0.21.0.jar:1.0.0.qualifier (system) ...]
```

The modules before it build and pass their tests.

**Cause:** Tycho 5.0.4 gives each jar on the `Bundle-ClassPath` of a bundle to Maven as a
system-scoped dependency. It uses the path of the jar as the classifier, for example
`lib/commonmark-0.21.0.jar`. Maven 3.10 uses Maven Resolver 2.x. This resolver refuses a
classifier that contains `/`. With `-e`, Maven shows the reason:

```text
Suppressed: java.lang.IllegalArgumentException: Invalid classifier: must not contain '..', '/', '\' or ':': lib/commonmark-0.21.0.jar
```

`com.kk.pde.ds.chatbot` is the only bundle in this repository with jars on its `Bundle-ClassPath`.
This is Tycho issue [#6399](https://github.com/eclipse-tycho/tycho/issues/6399). The `tycho-5.0.x`
branch has a fix (commit `fe12918ff`). On 2026-11-24, no Tycho release has this fix. The latest
release is 5.0.4.

On 2026-11-25, a full build with Tycho 5.0.5-SNAPSHOT (built on 2026-11-20) passed on Maven
3.10.0. All modules built, and the 54 tier-1 tests and 6 tier-2 tests passed. The final 5.0.5
release is not tested.

**Fix:**

1. Use the Maven Wrapper in the repository root. It pins Maven 3.9.16, the version that CI uses.
   ```bash
   ./mvnw clean verify
   ```
2. Do not use the system `mvn` if it is Maven 3.10.

For the build procedure, refer to [build.md](build.md).

### An old JDK cannot start Tycho

**Symptom:** the build stops before the first module with `UnsupportedClassVersionError` and
`class file version 65.0`.

**Cause:** Tycho 5.0.4 runs on Java 21 or later. The JDK that runs Maven is older.

**Fix:**

1. Set `JAVA_HOME` to a JDK 21 or later.
   ```bash
   JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw clean verify
   ```
2. The product still runs on Java 8 or later. This requirement is for the build only.

### `-pl X -am` cannot find an OSGi bundle

**Symptom:** a partial build with `-pl <module> -am` stops with `requires osgi.bundle ... but it could not be found`.

**Cause:** Maven cannot see the OSGi manifest dependencies (`Require-Bundle`, `Import-Package`). Thus `-am` builds only the parent.

**Fix:** list the target module and the OSGi upstream bundles explicitly. Do not use `-am`.

```bash
./mvnw verify -pl com.kk.pde.ds.target,com.kk.pde.ds.api,com.kk.pde.ds.imp
```

## Start problems

### The script cannot find the OSGi jar

**Symptom:** `run.sh` or `run.bat` shows `ERROR: Could not find org.eclipse.osgi jar in: ...`.

**Cause:** the product is not built, or the script cannot find the product for your operating system.

**Fix:**

1. Build the product from the repository root: `./mvnw clean verify`.
2. On Windows, use `run.bat`. `run.sh` supports macOS and Linux only.

### Configuration has no effect

**Symptom:** changes to `config.ini` or `logback.xml` have no effect.

**Checks:**

1. Make sure that the command contains the `-configuration configuration` flag.
2. Make sure that the file is in the `configuration/` directory, not in `config/`.
3. Make sure that you changed the file in the product directory, not only in `distribution/configuration/`. A build copies the source files into the product again.
4. For Logback, make sure that `config.ini` contains `logback.configurationFile=configuration/logback.xml`. As an alternative, give the JVM `-Dlogback.configurationFile=configuration/logback.xml`.

Good and bad commands:

```bash
# GOOD - Points to config directory
java -jar ... -configuration configuration ...

# BAD - Missing flag or wrong path
java -jar ... -configuration config ...   # Wrong directory name
java -jar ... ...                         # Missing flag entirely
```

### Port 8080 is already in use

**Symptom:** the HTTP endpoints do not answer, or an old version of the application answers. The console shows these lines from `org.apache.felix.http.jetty`:

```
!MESSAGE Failed to start Connector: ServerConnector@...{HTTP/1.1, (http/1.1)}{0.0.0.0:8080}
java.io.IOException: Failed to bind to 0.0.0.0/0.0.0.0:8080
Caused by: java.net.BindException: Address already in use
```

**Cause:** a different process uses port 8080. Frequently, this is an earlier instance of the product that is still running. The framework continues to start, so the failure is easy to miss.

**Fix:**

1. Find the process that uses the port.

   macOS or Linux:
   ```bash
   lsof -nP -iTCP:8080 -sTCP:LISTEN
   ```
   Windows:
   ```cmd
   netstat -ano | findstr :8080
   ```
2. Stop that process. For an earlier product instance, type `exit` and then `y` at its `osgi>` prompt. As an alternative, run `pkill -f com.kk.pde.ds.product`.
3. If you must keep the other process, start the product on a different port:
   ```bash
   ./distribution/scripts/run.sh -Dorg.osgi.service.http.port=8081 -Dmcp.server.url=http://localhost:8081/mcp
   ```

## Bundle and service problems

### A bundle does not start

**Symptom:** `lb` shows the bundle as INSTALLED or RESOLVED, not ACTIVE.

**Checks:**

1. Find the bundle ID with `lb <text>`, for example `lb pdfbox`.
2. Run `diag <id>` to see the unresolved requirements.
3. Run `headers <id>` to see the dependencies. Look for missing imports.
4. Make sure that the target platform contains the necessary bundles.

Some library bundles stay RESOLVED, for example `ch.qos.logback.classic`, `org.apache.pdfbox` and `slf4j.api`. This is normal. A bundle in INSTALLED state has a dependency problem.

### A service is not available

**Symptom:** a `@Reference` is not satisfied, and the component is not active.

**Checks:**

1. Run `scr:list` to see the component states.
2. Run `scr:info <component>` to see the reference details.
3. Make sure that the bundle that supplies the service is ACTIVE.
4. Make sure that the service interface is exactly the same.

## WebConsole and health check problems

For the WebConsole and the health checks, refer to [webconsole-and-health-checks.md](webconsole-and-health-checks.md).

### The WebConsole is not accessible

**Symptom:** you cannot connect to http://localhost:8080/system/console.

**Checks:**

1. Make sure that `org.apache.felix.http.jetty` is ACTIVE.
2. Make sure that `config.ini` contains `org.osgi.service.http.port=8080`.
3. Make sure that `config.ini` contains `org.apache.felix.http.enable=true`.
4. Make sure that no other process uses port 8080. Refer to [Port 8080 is already in use](#port-8080-is-already-in-use).
5. The ECF and spike products do not include Felix HTTP or the WebConsole. Use the main product (`run.sh` or `run.bat`).

### The Terminal page shows no output

**Symptom:** the Terminal page at http://localhost:8080/system/console/terminal shows no output. Or output stops showing after the first command.

**Causes and fixes:**

1. **The bundle started after the Gogo runtime, or it started again while Gogo runs.** The Gogo
   runtime puts its own stream into `System.out` before each command. If the bundle
   `com.kk.pde.ds.webconsole.terminal` starts later, printed output stops showing after the first
   command. A tier-2 test failed this way. An update or a refresh of the bundle stops and starts
   it. In both cases the bundle writes this warning on the original `System.err`. It also shows
   the warning as a red line on the page:

   ```text
   Terminal: this bundle started after the Gogo runtime, or it was restarted while Gogo runs. Printed output stops showing after the first command. Give com.kk.pde.ds.webconsole.terminal a lower start level than org.apache.felix.gogo.runtime, for example 1, and restart the application after an update of this bundle.
   ```

   - **Late start:** give the bundle a lower start level than `org.apache.felix.gogo.runtime`.
     In this product the bundle has level 1 (`distribution/p2.product`) and Gogo has level 3.
     Start level 1 is the earliest level. A bundle cannot set its own start level.
   - **Update or refresh:** restart the application. A product run on 2026-11-25 showed the
     problem. After `update 13` of this bundle, the warning was on the page but not in the
     console log. `ss` ran and showed its output. Two `GET /api/greet called` log lines were in
     the console log but not in the stream.
2. **The output came from before the bundle start.** The page does not show it. The capture
   starts with the bundle.
3. **A different component replaced `System.out` after the bundle.** The page does not capture
   the output after that.
4. **The connection is closed after a product restart.** The page says so and becomes read-only.
   Reload the page.

See [webconsole-terminal.md](webconsole-terminal.md).

### The health checks do not show

**Symptom:** the custom health checks do not show in the WebConsole.

**Checks:**

1. Make sure that `config.ini` contains `felix.fileinstall.dir=configuration/configs`.
2. Make sure that File Install is ACTIVE: `lb fileinstall`. The command `lb | grep fileinstall` does not find it, because `lb` shows the bundle name `Apache Felix File Install`. Use `lb -s | grep fileinstall` to filter on the symbolic name.
3. Make sure that the `.config` files are in `configuration/configs/`.
4. Make sure that the file names use the format `<factory.pid>-<instance>.config`.

## LLM and chatbot problems

### `No endpoints found for <model>`

**Symptom:** the chatbot or `/llm/chat` shows `Error from OpenRouter: No endpoints found for <model>.`

**Cause:** OpenRouter does not supply that model now. The default model was `google/gemini-flash-1.5` before. That model is gone. The default model is now `minimax/minimax-m3.1-flash:free`.

**Fix:** set a model that OpenRouter supplies. Use one of these methods:

- Start the product with `-Dopenrouter.model=<model-id>`.
- In the chatbot, click **Settings** and set the model.
- For `/llm/chat`, send the key `model` in the request.

The chatbot reads the model in this order: `settings/chatbot.properties`, then `-Dopenrouter.model`, then the default. A model in `settings/chatbot.properties` thus overrides `-Dopenrouter.model`. To use the `-D` value, clear the model in the Settings dialog or in that file.

For other `Error from OpenRouter: ...` messages, examine the API key, the model name and your OpenRouter credit balance.

## Problems in other documents

These documents have their own troubleshooting entries:

| Area | Document |
|------|----------|
| MCP server: `Connection refused`, `404 Not Found`, `"isError": true`, error `-32601`, a missing tool | [mcp-server-testing.md, Troubleshooting](mcp-server-testing.md#troubleshooting) |
| LLM bridge (`/llm/chat`): no API key, `No endpoints found`, a missing `LlmChatServlet` | [mcp-server-testing.md, Troubleshooting the bridge](mcp-server-testing.md#troubleshooting-the-bridge) |
| ECF: incorrect EDEF values, `pkill -f` that finds no process, port conflicts between two JVMs | [ecf-remote-services.md, Problems we found and lessons](ecf-remote-services.md#problems-we-found-and-lessons) |
| RAG and OCR: no `tesseract` binary, headless runs, network access | [rag-reference.md, OCR behavior](rag-reference.md#ocr-behavior) and [Network access](rag-reference.md#network-access) |

## Related documents

- [running.md](running.md): start, configure, log and debug the product.
- [build.md](build.md): build the product.
- [webconsole-and-health-checks.md](webconsole-and-health-checks.md): the WebConsole and the health checks.
