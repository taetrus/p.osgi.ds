# WebConsole and Health Checks

This document tells you how to use the Felix WebConsole and the Felix Health Checks in the main product.
It lists the console tabs, the custom `GreetHealthCheck`, and the six file-based health checks.

> **Security:** The WebConsole uses the demo credentials `admin`/`admin`, and port 8080 is for localhost only. See [../SECURITY.md](../SECURITY.md).

## Contents

- [Felix WebConsole](#felix-webconsole)
  - [Open the WebConsole](#open-the-webconsole)
  - [Access](#access)
  - [Configuration (config.ini)](#configuration-configini)
  - [Available tabs](#available-tabs)
  - [Included WebConsole bundles](#included-webconsole-bundles)
- [Health checks](#health-checks)
  - [Run the health checks](#run-the-health-checks)
  - [Architecture](#architecture)
  - [Custom health check: GreetHealthCheck](#custom-health-check-greethealthcheck)
  - [File-based health checks](#file-based-health-checks)
  - [Health check summary](#health-check-summary)
- [Related documents](#related-documents)

## Felix WebConsole

The Felix WebConsole gives live administration of a running OSGi framework.
It shows the bundles, the services, the DS components and the configuration.

### Open the WebConsole

1. Start the product. See [running.md](running.md).
2. Open http://localhost:8080/system/console in a browser. The URL is the same on macOS and on Windows.
3. Type the user name `admin` and the password `admin`.
4. Open the **Components** tab. It shows the state of each `@Component` and the `@Reference`s that are bound.

The page http://localhost:8080/system/console/components opens the **Components** tab directly.

### Access

| Setting | Value |
|---------|-------|
| **URL** | http://localhost:8080/system/console |
| **User name** | `admin` |
| **Password** | `admin` |

### Configuration (config.ini)

The file `distribution/configuration/config.ini` sets the credentials and the HTTP port:

```properties
org.osgi.service.http.port=8080
felix.webconsole.username=admin
felix.webconsole.password=admin
```

### Available tabs

| Tab | Description |
|-----|-------------|
| **Bundles** | Lists all bundles with their states (INSTALLED, RESOLVED, ACTIVE) |
| **Services** | Shows the registered OSGi services |
| **Components** | Shows the DS components through the DS plugin (all `@Component` definitions) |
| **Health Check** | Shows the health check results through the health check WebConsole plugin |
| **Configuration** | Shows the OSGi configurations, and lets you edit them |

### Included WebConsole bundles

The target platform (`com.kk.pde.ds.target/*.target`) supplies these bundles:

- `org.apache.felix.webconsole:4.3.16`
- `org.apache.felix.webconsole.plugins.ds:2.1.0` (DS plugin)
- `org.apache.felix.inventory:1.1.0` (the DS plugin needs it)

## Health checks

The product has one custom health check (`GreetHealthCheck`) and six general-purpose health checks.
The health check WebConsole plugin shows the results of all of them.

### Run the health checks

1. Start the product. See [running.md](running.md).
2. Open http://localhost:8080/system/console/healthcheck in a browser. The URL is the same on macOS and on Windows.
3. Run the checks by tag or by name.
4. Look at the result of each check: OK, WARN or CRITICAL.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Health Check Framework                        │
├─────────────────────────────────────────────────────────────────────┤
│  API: org.apache.felix.healthcheck.api (2.0.4)                      │
│  Core: org.apache.felix.healthcheck.core (2.0.8)                    │
│  General Checks: org.apache.felix.healthcheck.generalchecks (2.0.6) │
│  WebConsole Plugin: org.apache.felix.healthcheck.webconsoleplugin   │
│                     (2.0.2)                                          │
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

### Custom health check: GreetHealthCheck

**File:** `com.kk.pde.ds.imp/src/com/kk/pde/ds/imp/GreetHealthCheck.java`
**DS descriptor:** `com.kk.pde.ds.imp/OSGI-INF/com.kk.pde.ds.imp.GreetHealthCheck.xml`

| Property | Value |
|----------|-------|
| **Name** (`hc.name`) | Greet Service Health Check |
| **Tags** (`hc.tags`) | application, greet |
| **MBean name** (`hc.mbean.name`) | greetServiceHealthCheck |

SCR reads these properties from the hand-written XML descriptor, not from the `@Component` annotation.
The code is in [declarative-services.md](declarative-services.md#5-greethealthcheck).

**Behavior:**

1. The component has a mandatory, static (1..1) reference to `IGreet`. If no `IGreet` service is available, the component is unsatisfied and SCR does not register the check.
2. When the check runs, it calls `greet()`.
3. The result is OK if the call is successful. The result is CRITICAL only if `greet()` throws an exception.

When the result is OK, the log of the check has two lines:
"IGreet service is available and operational", and the name of the implementation class.
When the result is CRITICAL, the log line is "IGreet service invocation failed: " and the exception message.

### File-based health checks

All six configuration files are in `distribution/configuration/configs/`.
In the runtime, this folder is `configuration/configs/`.
Felix File Install loads the files. `config.ini` sets File Install:

```properties
felix.fileinstall.dir=configuration/configs
felix.fileinstall.poll=5000
felix.fileinstall.log.level=3
felix.fileinstall.noInitialDelay=true
```

File Install examines the folder each 5000 ms. Thus it also loads a changed file while the product runs.

Each file name has the form `<factory PID>-<name>.config`.
The factory PID selects the check type in `org.apache.felix.healthcheck.generalchecks`.
Each of these check types needs a configuration (`configuration-policy="require"`).
Thus a check exists only when a file configures it.

The files use the typed File Install format (`.config`), not the plain `.cfg` format.
In a `.config` file, a string value has quotes and an array value has square brackets.
An integer value has the prefix `I`, for example `I"3000"`.
Use this format, because several check properties are arrays.
A `.cfg` file cannot give an array: the check gets the full text `a,b` as one value.

#### 1. Application Bundles Check

**File:** `org.apache.felix.hc.generalchecks.BundlesStartedCheck-app.config`

```properties
hc.name="Application Bundles Check"
hc.tags=["application","bundles"]
includesRegex="com[.]kk[.]pde[.]ds[.].*"
```

Makes sure that all 10 `com.kk.pde.ds.*` bundles are ACTIVE.
The pattern uses `[.]` for a literal dot. The `.config` parser removes a backslash escape such as `\\.`.

#### 2. Core Bundles Check

**File:** `org.apache.felix.hc.generalchecks.BundlesStartedCheck-core.config`

```properties
hc.name="Core Bundles Check"
hc.tags=["system","bundles"]
includesRegex="org[.]apache[.]felix[.](scr|configadmin|eventadmin|http[.]jetty)"
```

Makes sure that the four core Felix bundles (SCR, Config Admin, Event Admin, HTTP Jetty) are ACTIVE.

#### 3. DS Components Check

**File:** `org.apache.felix.hc.generalchecks.DsComponentsCheck-app.config`

```properties
hc.name="DS Components Check"
hc.tags=["application","components"]
components.list=["com.kk.pde.ds.imp.Greet","com.kk.pde.ds.app.App","com.kk.pde.ds.rest.GreetServlet","com.kk.pde.ds.mcp.server.McpServlet","com.kk.pde.ds.mcp.server.McpToolRegistry","com.kk.pde.ds.mcp.llm.OpenRouterAgent","com.kk.pde.ds.mcp.llm.LlmChatServlet","com.kk.pde.ds.chatbot.ChatService","com.kk.pde.ds.rag.internal.DocumentIngestionServiceImpl"]
```

Makes sure that the nine main application components are active.
`components.list` takes component names. It does not take a pattern.
When you add a main component, add its name to this list.

#### 4. REST API Check

**File:** `org.apache.felix.hc.generalchecks.HttpRequestsCheck-api.config`

```properties
hc.name="REST API Check"
hc.tags=["application","http"]
requests=["/api/greet => 200"]
connectTimeoutInMs=I"3000"
readTimeoutInMs=I"5000"
```

Makes sure that `GET /api/greet` gives HTTP 200.
Each entry in `requests` has the form `<path or URL> => <expected status>`.
A relative path goes to the local HTTP service.

#### 5. Greet Service Available

**File:** `org.apache.felix.hc.generalchecks.ServicesCheck-greet.config`

```properties
hc.name="Greet Service Available"
hc.tags=["application","services"]
services.list=["com.kk.pde.ds.api.IGreet"]
```

Makes sure that the `IGreet` service is registered.

#### 6. Health Check Services

**File:** `org.apache.felix.hc.generalchecks.ServicesCheck-healthcheck.config`

```properties
hc.name="Health Check Services"
hc.tags=["system","services"]
services.list=["org.apache.felix.hc.api.execution.HealthCheckExecutor","org.osgi.service.event.EventAdmin"]
```

Makes sure that the health check executor and Event Admin services are registered.
Do not add `org.apache.felix.hc.api.HealthCheck` to this list.
This check is itself a `HealthCheck` service, so SCR then logs `Circular reference detected`.

#### Expected result

On the **Health Check** tab, enter `*` as the tag and select **Force instant execution**.
The result is `8 HealthCheck executed, 0 failures` (checked on a running product).

#### History: the old `.cfg` files did not work

Before commit `743e262`, these checks used `.cfg` files with property names that the checks do not read.
A check ignores an unknown property and uses its default value. The live result was 4 failures in 8 checks:

| Old property | Property that the check reads | Result with the default value |
|--------------|-------------------------------|-------------------------------|
| `bundles.regex` | `includesRegex` | Both bundle checks examined all bundles and gave WARN for library bundles |
| `components.regex` | `components.list` | The DS check examined 0 components and always gave OK |
| `requests.json` | `requests` | The REST check sent the example requests of the default value, also to `api.example.com` |
| `services.list=a,b` (in a `.cfg` file) | `services.list` (an array) | The check looked for one service named `a,b` and gave TEMPORARILY_UNAVAILABLE |

### Health check summary

| Check | Type | Tags | Purpose |
|-------|------|------|---------|
| Greet Service Health Check | Custom | application, greet | Makes sure that the IGreet service works |
| Application Bundles Check | BundlesStarted | application, bundles | App bundles ACTIVE |
| Core Bundles Check | BundlesStarted | system, bundles | System bundles ACTIVE |
| DS Components Check | DsComponents | application, components | Components satisfied |
| REST API Check | HttpRequests | application, http | REST endpoint accessible |
| Greet Service Available | Services | application, services | IGreet registered |
| Health Check Services | Services | system, services | HC infrastructure operational |

## Related documents

- [declarative-services.md](declarative-services.md): the DS components, with `GreetHealthCheck`.
- [rest-api.md](rest-api.md): the endpoint that the REST API Check calls.
- [architecture.md](architecture.md): the wiring of all components.
- [running.md](running.md): how to start the product.
- [build.md](build.md): how to build the product.
- [../SECURITY.md](../SECURITY.md): the WebConsole credentials and the other known exposures.
