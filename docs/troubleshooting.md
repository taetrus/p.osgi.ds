# Troubleshooting

This document lists known problems with the build and the running product. Each entry gives the symptom, the cause and the fix.

## Contents

- [Build problems](#build-problems)
  - [Maven 3.10 cannot start Tycho](#maven-310-cannot-start-tycho)
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
  - [The health checks do not show](#the-health-checks-do-not-show)
- [LLM and chatbot problems](#llm-and-chatbot-problems)
  - [`No endpoints found for <model>`](#no-endpoints-found-for-model)
- [Problems in other documents](#problems-in-other-documents)
- [Related documents](#related-documents)

## Build problems

### Maven 3.10 cannot start Tycho

**Symptom:** the build stops with `No implementation for MavenTargetLocationFactory was bound`.

**Cause:** Tycho 4.0.13 does not start on Maven 3.10.

**Fix:**

1. Use the Maven Wrapper in the repository root. It pins Maven 3.9.16.
   ```bash
   ./mvnw clean verify
   ```
2. Do not use the system `mvn` if it is Maven 3.10.

For the build procedure, refer to [build.md](build.md).

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

### The health checks do not show

**Symptom:** the custom health checks do not show in the WebConsole.

**Checks:**

1. Make sure that `config.ini` contains `felix.fileinstall.dir=configuration/configs`.
2. Make sure that File Install is ACTIVE: `lb fileinstall`. The command `lb | grep fileinstall` does not find it, because `lb` shows the bundle name `Apache Felix File Install`. Use `lb -s | grep fileinstall` to filter on the symbolic name.
3. Make sure that the `.cfg` files are in `configuration/configs/`.
4. Make sure that the file names use the format `<factory.pid>-<instance>.cfg`.

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
