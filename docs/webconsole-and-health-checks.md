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
  - [Known issue: three configuration files use wrong property names](#known-issue-three-configuration-files-use-wrong-property-names)
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

Each file name has the form `<factory PID>-<name>.cfg`.
The factory PID selects the check type in `org.apache.felix.healthcheck.generalchecks`.
Each of these check types needs a configuration (`configuration-policy="require"`).
Thus a check exists only when a file configures it.

#### 1. Application Bundles Check

**File:** `org.apache.felix.hc.generalchecks.BundlesStartedCheck-app.cfg`

```properties
hc.name=Application Bundles Check
hc.tags=application,bundles
bundles.regex=com.kk.pde.ds.api|com.kk.pde.ds.imp|com.kk.pde.ds.app|com.kk.pde.ds.rest
```

Intent: make sure that the listed application bundles are ACTIVE.
See the [known issue](#known-issue-three-configuration-files-use-wrong-property-names).

#### 2. Core Bundles Check

**File:** `org.apache.felix.hc.generalchecks.BundlesStartedCheck-core.cfg`

```properties
hc.name=Core Bundles Check
hc.tags=system,bundles
bundles.regex=org.apache.felix.scr|org.apache.felix.configadmin|org.apache.felix.eventadmin|org.apache.felix.http.jetty
```

Intent: make sure that the essential OSGi infrastructure bundles are ACTIVE.
See the [known issue](#known-issue-three-configuration-files-use-wrong-property-names).

#### 3. DS Components Check

**File:** `org.apache.felix.hc.generalchecks.DsComponentsCheck-app.cfg`

```properties
hc.name=DS Components Check
hc.tags=application,components
components.regex=com.kk.pde.ds.*
```

Intent: make sure that all application DS components are satisfied.
See the [known issue](#known-issue-three-configuration-files-use-wrong-property-names).

#### 4. REST API Check

**File:** `org.apache.felix.hc.generalchecks.HttpRequestsCheck-api.cfg`

```properties
hc.name=REST API Check
hc.tags=application,http
requests.json=["http://localhost:8080/api/greet"]
connectTimeoutInMs=3000
readTimeoutInMs=5000
```

Intent: make sure that the REST endpoint is accessible and sends a response.
See the [known issue](#known-issue-three-configuration-files-use-wrong-property-names).

#### 5. Greet Service Available

**File:** `org.apache.felix.hc.generalchecks.ServicesCheck-greet.cfg`

```properties
hc.name=Greet Service Available
hc.tags=application,services
services.list=com.kk.pde.ds.api.IGreet
```

Makes sure that the `IGreet` service is registered.

#### 6. Health Check Services

**File:** `org.apache.felix.hc.generalchecks.ServicesCheck-healthcheck.cfg`

```properties
hc.name=Health Check Services
hc.tags=system,services
services.list=org.apache.felix.hc.api.HealthCheck,org.osgi.service.event.EventAdmin
```

Makes sure that the health check infrastructure operates.

### Known issue: three configuration files use wrong property names

The metatype of `org.apache.felix.healthcheck.generalchecks` 2.0.6 defines the property names for each check type.
Three files use property names that are not in that metatype.
The check ignores an unknown property and uses its default value.

| File | Property in the file | Property that the check reads | Default value of the check property |
|------|----------------------|-------------------------------|-------------------------------------|
| `BundlesStartedCheck-app.cfg`, `BundlesStartedCheck-core.cfg` | `bundles.regex` | `includesRegex` | `.*` (all bundles) |
| `DsComponentsCheck-app.cfg` | `components.regex` | `components.list` (a list of component names, not a regex) | no default |
| `HttpRequestsCheck-api.cfg` | `requests.json` | `requests` (request specs, for example `/api/greet => 200`) | example paths such as `/path/example.html` |

`connectTimeoutInMs`, `readTimeoutInMs`, `services.list`, `hc.name` and `hc.tags` are correct names.

Possible effects (not tested on a running product):

- The two bundle checks examine all bundles, not only the listed bundles. An inactive bundle gives WARN.
- The DS components check does not get the list of application components.
- The REST API check requests the example paths of the default value, not `/api/greet`. These requests can fail, and the check can give WARN.

To correct the files, use the property names from the metatype. Then make sure that each check gives the expected result on the **Health Check** tab.

### Health check summary

| Check | Type | Tags | Purpose |
|-------|------|------|---------|
| Greet Service Health Check | Custom | application, greet | Makes sure that the IGreet service works |
| Application Bundles Check | BundlesStarted | application, bundles | App bundles ACTIVE (see the known issue) |
| Core Bundles Check | BundlesStarted | system, bundles | System bundles ACTIVE (see the known issue) |
| DS Components Check | DsComponents | application, components | Components satisfied (see the known issue) |
| REST API Check | HttpRequests | application, http | REST endpoint accessible (see the known issue) |
| Greet Service Available | Services | application, services | IGreet registered |
| Health Check Services | Services | system, services | HC infrastructure operational |

## Related documents

- [declarative-services.md](declarative-services.md): the DS components, with `GreetHealthCheck`.
- [rest-api.md](rest-api.md): the endpoint that the REST API Check calls.
- [architecture.md](architecture.md): the wiring of all components.
- [running.md](running.md): how to start the product.
- [build.md](build.md): how to build the product.
- [../SECURITY.md](../SECURITY.md): the WebConsole credentials and the other known exposures.
