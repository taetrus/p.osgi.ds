# Running the Application

This document tells you how to start, configure, log, debug and control the OSGi product. It covers the run scripts, the manual start, IDE use and the Gogo console.

## Contents

- [Requirements](#requirements)
- [Start the product](#start-the-product)
  - [Method 1: Run a script from the source tree](#method-1-run-a-script-from-the-source-tree)
  - [Method 2: Run a script from inside the product](#method-2-run-a-script-from-inside-the-product)
  - [Method 3: Manual start](#method-3-manual-start)
  - [Stop the product](#stop-the-product)
- [Run script details](#run-script-details)
- [Give arguments to the JVM](#give-arguments-to-the-jvm)
- [Product directory structure](#product-directory-structure)
- [Configuration](#configuration)
  - [config.ini](#configini)
  - [Felix File Install](#felix-file-install)
- [Logging](#logging)
- [Remote debugging](#remote-debugging)
- [IDE integration](#ide-integration)
- [OSGi console commands](#osgi-console-commands)
- [Related documents](#related-documents)

## Requirements

- Java 8 or newer runs the product. The run scripts and CI use Java 21.
- JDK 17 or newer builds the product. For the build, refer to [build.md](build.md).
- The run scripts use the `java` command on your `PATH`.

## Start the product

### Method 1: Run a script from the source tree

This is the recommended method. You do not need to extract an archive. The script finds your operating system and the built product directory for it.

1. Build the product from the repository root:
   ```bash
   ./mvnw clean verify
   ```
2. Optional: set the API key for the chatbot and the LLM features. We recommend this step.

   macOS or Linux:
   ```bash
   export OPENROUTER_API_KEY=your_key_here
   ```
   Windows:
   ```cmd
   set OPENROUTER_API_KEY=your_key_here
   ```
3. Run the script from the repository root.

   macOS or Linux:
   ```bash
   ./distribution/scripts/run.sh
   ```
   Windows:
   ```cmd
   distribution\scripts\run.bat
   ```

You can also give the API key as a system property: `./distribution/scripts/run.sh -Dopenrouter.api.key=your_key`.

### Method 2: Run a script from inside the product

The build copies the script into each product. The script finds `plugins/` next to it. Linux and macOS products contain only `run.sh`. Windows products contain only `run.bat`.

**Linux product:**
```bash
cd distribution/target/products/com.kk.pde.ds.product/linux/gtk/x86_64
./run.sh
```

**macOS product.** The macOS build puts the product inside an `.app` bundle:
```bash
cd distribution/target/products/com.kk.pde.ds.product/macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse
./run.sh
```

**Windows product:**
```cmd
cd distribution\target\products\com.kk.pde.ds.product\win32\win32\x86_64
run.bat
```

The product archives contain the same directories:

- `distribution/target/products/com.kk.pde.ds.product-linux.gtk.x86_64.tar.gz`
- `distribution/target/products/com.kk.pde.ds.product-macosx.cocoa.x86_64.tar.gz`
- `distribution/target/products/com.kk.pde.ds.product-win32.win32.x86_64.zip`

The build keeps the `755` mode on `run.sh` in the `.tar.gz` archives.

### Method 3: Manual start

1. Go to a product directory. This is the directory that contains `plugins/` and `configuration/`.
2. Run this command:
   ```bash
   java -jar plugins/org.eclipse.osgi_*.jar \
        -configuration configuration \
        -console \
        -consoleLog
   ```

**Important:** the `-configuration configuration` flag is necessary. It tells the OSGi framework where `config.ini` is.

The manual command does not set the three `-Djavax.xml.accessExternal*=` flags. The run scripts set them. Refer to [Run script details](#run-script-details).

### Stop the product

Use one of these methods:

- Type `exit` at the `osgi>` prompt. Equinox asks `Really want to stop Equinox? (y/n; default=y)`. Type `y`.
- From a different terminal, run `pkill -f <product-id>`. For example: `pkill -f com.kk.pde.ds.product`. The run scripts give the absolute path of the OSGi jar to `-jar`. That path contains the product ID, so `pkill -f` finds the process.

`config.ini` sets `osgi.noShutdown=true`. Thus the framework continues to run after the start sequence.

## Run script details

The two scripts are `distribution/scripts/run.sh` and `distribution/scripts/run.bat`. They do these steps:

1. If `plugins/` is next to the script, the script runs from inside a product. It uses the script directory as the product directory.
2. If not, the script runs from `distribution/scripts/` in the source tree. It uses the built product for the current operating system. `run.sh` supports macOS and Linux only. On a different system it shows an error and tells you to use `run.bat`.
3. The script finds `plugins/org.eclipse.osgi_*.jar`. If there is no jar, it shows an error and tells you to build first.
4. The script changes to the product directory and starts Java.

The Java command is the same in the two scripts:

```bash
# run.sh ("$@" carries your arguments; run.bat uses %*)
OFFLINE_XML="-Djavax.xml.accessExternalDTD= -Djavax.xml.accessExternalSchema= -Djavax.xml.accessExternalStylesheet="
java $OFFLINE_XML "$@" -jar "$OSGI_JAR" -configuration configuration -console -consoleLog
```

- The `OFFLINE_XML` flags stop the XML parsers from downloading external DTDs, schemas and stylesheets. Document parsing stays offline.
- The scripts put all your arguments **before** `-jar`. Thus `-D` flags and other JVM options get to the JVM. The Equinox launcher arguments stay after the jar.
- Only `run.sh` and `run.bat` set the `OFFLINE_XML` flags.

The ECF and spike demos have their own scripts in `distribution/scripts/` (`run-ecf-host`, `run-ecf-consumer`, `run-spike-master`, `run-spike-detail`). Refer to [ecf-remote-services.md](ecf-remote-services.md).

## Give arguments to the JVM

The scripts send each argument to the JVM.

```bash
# Give system properties
./distribution/scripts/run.sh -Drag.docs.dir=/path/to/your/documents

# Give JVM options
./distribution/scripts/run.sh -Xmx512m -Xms256m
```

A `-D` flag on the command line overrides the same key in `config.ini`. For example, `-Dorg.osgi.service.http.port=8081` moves the HTTP server to port 8081. The `com.kk.pde.ds.mcp.client` bundle connects to `http://localhost:8080/mcp` by default. On a different port, also set `-Dmcp.server.url=http://localhost:8081/mcp`. If you do not, the client logs `Failed to connect to MCP server`. The other features continue to operate.

## Product directory structure

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

## Configuration

### config.ini

The source file is `distribution/configuration/config.ini`. The build puts it in `configuration/` of each product.

| Key | Value | Purpose |
|-----|-------|---------|
| `osgi.bundles.defaultStartLevel` | `4` | Default start level of the bundles |
| `eclipse.ignoreApp` | `true` | Do not start an Eclipse application |
| `osgi.noShutdown` | `true` | Keep the framework on after the start sequence |
| `logback.configurationFile` | `configuration/logback.xml` | Logback configuration file |
| `org.osgi.service.http.port` | `8080` | Felix HTTP port |
| `org.apache.felix.http.enable` | `true` | Enable the Felix HTTP service |
| `felix.webconsole.username` | `admin` | WebConsole user name |
| `felix.webconsole.password` | `admin` | WebConsole password |
| `felix.fileinstall.*` | Refer to the next section | Felix File Install |

For the WebConsole and the health checks, refer to [webconsole-and-health-checks.md](webconsole-and-health-checks.md).

### Felix File Install

Felix File Install loads OSGi configurations from files. These lines in `config.ini` configure it:

```properties
felix.fileinstall.dir=configuration/configs
felix.fileinstall.poll=5000
felix.fileinstall.log.level=3
felix.fileinstall.noInitialDelay=true
```

| Setting | Value | Description |
|---------|-------|-------------|
| `dir` | `configuration/configs` | Directory to monitor for `.cfg` files |
| `poll` | `5000` | Poll interval in milliseconds |
| `log.level` | `3` | Log level (DEBUG) |
| `noInitialDelay` | `true` | Process the files immediately at startup |

**How File Install works:**

1. File Install monitors the `configuration/configs/` directory.
2. At startup and every 5 seconds, it scans for `.cfg` files.
3. It creates or updates OSGi configurations through Config Admin.
4. The health check bundles receive the configurations and create the checks.

**To add a health check:**

1. Create a `.cfg` file in `distribution/configuration/configs/`.
2. Use the factory PID name format: `<factory.pid>-<instance>.cfg`.
3. Build the product again. As an alternative, copy the file into `configuration/configs/` of the running product.

Example for a bundle check:

```properties
# org.apache.felix.hc.generalchecks.BundlesStartedCheck-custom.cfg
hc.name=Custom Bundle Check
hc.tags=custom
bundles.regex=my.custom.bundle
```

## Logging

The source file is `distribution/configuration/logback.xml`. The build copies it into `configuration/` of each product. `config.ini` sets `logback.configurationFile=configuration/logback.xml`, so you do not need a `-D` flag.

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

### Log levels

| Logger | Level | Purpose |
|--------|-------|---------|
| Root | INFO | Default for all loggers |
| `com.kk.pde.ds.*` | INFO | Application packages |
| `org.apache.felix.*` | WARN | Less Felix output |
| `org.eclipse.jetty.*` | WARN | Less Jetty output |

### Log format

```
HH:mm:ss.SSS [thread] LEVEL logger - message
```

Example lines. The time and the thread names change from run to run.

```
19:34:01.925 [Start Level: Equinox Container: 75deedb3-...] INFO  com.kk.pde.ds.imp.Greet - Greet.start()
19:34:01.928 [Start Level: Equinox Container: 75deedb3-...] INFO  com.kk.pde.ds.app.App - App.start()
19:34:01.928 [Start Level: Equinox Container: 75deedb3-...] INFO  com.kk.pde.ds.imp.Greet - Hello world!
```

Some framework messages do not go through Logback. The `-consoleLog` flag writes them to the console in the Equinox format (`!ENTRY ...`, `!MESSAGE ...`).

### Change the log levels

1. Open `logback.xml` in the `configuration/` directory of the product.
2. Add or change a `<logger>` line:
   ```xml
   <!-- Enable DEBUG for application -->
   <logger name="com.kk.pde.ds" level="DEBUG" />

   <!-- Enable DEBUG for specific class -->
   <logger name="com.kk.pde.ds.imp.GreetHealthCheck" level="DEBUG" />
   ```
3. Start the product again.

## Remote debugging

### Enable JDWP

Add the debug agent to the Java command:

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar plugins/org.eclipse.osgi_*.jar \
     -configuration configuration \
     -console \
     -consoleLog
```

| Parameter | Value | Description |
|-----------|-------|-------------|
| `transport` | `dt_socket` | Use the socket transport |
| `server` | `y` | Operate as the debug server |
| `suspend` | `n` | Do not wait for the debugger. Use `y` to stop the JVM until a debugger attaches. |
| `address` | `5005` | Debug port |

### Use the run script with the debug agent

The run script gives your arguments to the JVM before `-jar`. Thus you can add the debug agent directly:

```bash
./distribution/scripts/run.sh -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```

### Connect from an IDE

**IntelliJ IDEA:**
1. Select Run → Edit Configurations → + → Remote JVM Debug.
2. Set Host to `localhost`.
3. Set Port to `5005`.
4. Click Debug.

**Eclipse:**
1. Select Run → Debug Configurations → Remote Java Application → New.
2. Set Host to `localhost`.
3. Set Port to `5005`.
4. Click Debug.

**VS Code:** add this configuration to `launch.json`:
```json
{
    "type": "java",
    "name": "Attach to OSGi",
    "request": "attach",
    "hostName": "localhost",
    "port": 5005
}
```

## IDE integration

### Eclipse PDE

We recommend Eclipse PDE for OSGi work.

1. Import the projects:
   1. Select File → Import → Existing Projects into Workspace.
   2. Select the repository root.
   3. Import all projects.
2. Set the target platform:
   1. Open `com.kk.pde.ds.target/com.kk.pde.ds.target.target` in the Target Editor.
   2. Wait until Eclipse resolves the target.
   3. Click "Set as Active Target Platform".
3. Create a run configuration:
   1. Select Run → Run Configurations → OSGi Framework → New.
   2. Select the bundles to include.
   3. Set the program arguments to `-console -consoleLog`.
   4. Set the VM arguments to `-Dlogback.configurationFile=distribution/configuration/logback.xml`.
4. To debug, use Debug on the run configuration. As an alternative, add the remote debug agent and attach.

### IntelliJ IDEA

We recommend this procedure: run the built product and attach the remote debugger.

1. Build the product:
   ```bash
   ./mvnw clean verify
   ```
2. Start the product with the debug agent:
   ```bash
   ./distribution/scripts/run.sh -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
   ```
3. Select Run → Edit Configurations → Remote JVM Debug.
4. Connect to `localhost:5005`.

**Alternative: direct start**

1. Create a JAR Application configuration.
2. Set the JAR path to `<platform dir>/plugins/org.eclipse.osgi_*.jar`.
3. Set the program arguments to `-configuration configuration -console -consoleLog`.
4. Set the VM options to `-Dlogback.configurationFile=configuration/logback.xml`.
5. Set the working directory to the platform directory of the product.

The platform directory is one of these, under `distribution/target/products/com.kk.pde.ds.product/`:

- `linux/gtk/x86_64`
- `win32/win32/x86_64`
- `macosx/cocoa/x86_64/Eclipse.app/Contents/Eclipse`

### VS Code

1. Install the Java Extension Pack.
2. Open the repository folder.
3. Build with the Maven Wrapper: `./mvnw clean verify`.
4. Start the product with the debug agent. Refer to [Use the run script with the debug agent](#use-the-run-script-with-the-debug-agent).
5. Attach the debugger with the `launch.json` configuration from [Connect from an IDE](#connect-from-an-ide).

## OSGi console commands

The `-console -consoleLog` flags give you the Gogo shell at the `osgi>` prompt.

### Bundle commands

| Command | Description |
|---------|-------------|
| `lb` | List the bundles with IDs and states |
| `lb -s` | List the bundles with their symbolic names |
| `lb <text>` | List only the bundles whose name contains the text, for example `lb fileinstall` |
| `ss` | List the bundles in the short Equinox format |
| `diag <id>` | Show why a bundle does not resolve |
| `start <id>` | Start a bundle |
| `stop <id>` | Stop a bundle |
| `refresh <id>` | Refresh a bundle |
| `update <id>` | Update a bundle |
| `uninstall <id>` | Uninstall a bundle |
| `headers <id>` | Show the bundle headers (`MANIFEST.MF`) |

### Service commands

| Command | Description |
|---------|-------------|
| `services` | List all registered services |
| `services <filter>` | Filter the services with an LDAP filter |

### DS (SCR) commands

| Command | Description |
|---------|-------------|
| `scr:list` | List all DS components |
| `scr:info <component>` | Show the component details |
| `scr:enable <component>` | Enable a component |
| `scr:disable <component>` | Disable a component |

### Example session

The bundle IDs change with the bundle set. Use the IDs that `lb` shows.

```
osgi> lb
osgi> scr:list
osgi> scr:info com.kk.pde.ds.app.App
osgi> services "(objectClass=com.kk.pde.ds.api.IGreet)"
osgi> stop <id of com.kk.pde.ds.app>     # App.stop() closes the Clock and Chat windows
osgi> start <id of com.kk.pde.ds.app>    # App.start() opens them again
osgi> exit                               # then type y
```

## Related documents

- [build.md](build.md): build the product.
- [troubleshooting.md](troubleshooting.md): symptoms, causes and fixes.
- [webconsole-and-health-checks.md](webconsole-and-health-checks.md): the WebConsole and the health checks.
- [ecf-remote-services.md](ecf-remote-services.md): the two-JVM ECF demo.
- [rag-reference.md](rag-reference.md): Document Q&A configuration.
- [../SECURITY.md](../SECURITY.md): the HTTP surface on port 8080 is for localhost only.
