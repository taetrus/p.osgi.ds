# Design: ECF Remote Services Showcase

**Date:** 2026-05-29
**Status:** Approved (design); pending implementation plan
**Goal:** Add new OSGi bundles that showcase Eclipse Communication Framework (ECF)
usage via the OSGi Remote Services / Remote Service Admin (RSA) standard, using DS
annotations consistently with the rest of the project.

## Summary

Add a self-contained ECF Remote Services demo to the existing OSGi DS project. A
service interface (`IRemoteGreet`) is implemented and **exported** as a remote OSGi
service in one JVM (the *host* process), and transparently **imported** and consumed
via DS `@Reference` in a second JVM (the *consumer* process). The two processes
communicate over ECF's **Generic provider** transport (a TCP socket on
`localhost:3288`). Endpoint discovery is **file-based (EDEF)** — no ZooKeeper or mDNS
daemon — making the demo fully deterministic and reproducible.

This is the natural extension of the project's central theme: `Greet` registers
`IGreet`, `App` injects it via `@Reference`. ECF Remote Services is that same
`@Reference` injection stretched across a network boundary, with RSA marshalling the
call.

## Key decisions (locked)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| ECF capability | **Remote Services (RSA)** | ECF's flagship; maps 1:1 onto the project's `@Reference` pattern. |
| Topology | **Two separate OS processes (JVMs)** | The call genuinely crosses a network socket — realistic. |
| Interface | **New `IRemoteGreet.greet(String name)`** | Keeps the project's greet motif; `String` arg demonstrates parameter marshalling. Separate from local `IGreet` to avoid `@Reference` ambiguity. |
| Discovery | **File-based EDEF** (`Remote-Service` manifest header) | Deterministic, no discovery daemon. |
| Transport | **ECF Generic provider** (`ecf.generic.server` / `ecftcp://`) | Built into ECF; no external broker. |
| Packaging | **Two dedicated products + run scripts** | Zero runtime role logic; EDEF import isolated to the consumer process. |
| ECF version | **ECF 3.14.x line** | Last line with `JavaSE-1.8` BREEs; project is hard-locked to Java 8. Exact per-bundle Maven versions verified empirically at build time. |

## Architecture

```
  ┌─────────────────────────────┐         ecftcp://localhost:3288/server        ┌──────────────────────────────┐
  │   HOST JVM (process 1)       │  ────────────── TCP socket ────────────────▶ │  CONSUMER JVM (process 2)    │
  │                              │                                               │                              │
  │  RemoteGreetImpl @Component  │   RSA exports endpoint                        │  EDEF file imports endpoint  │
  │   property: exported.*       │                                               │  RSA registers proxy svc     │
  │   implements IRemoteGreet    │   greet("ECF") ◀── proxy call ───────────────│  RemoteGreetConsumer         │
  │                              │   returns "Hello, ECF!" ─── over socket ────▶│   @Reference IRemoteGreet    │
  │  ECF Generic SERVER          │                                               │  ECF Generic CLIENT          │
  └─────────────────────────────┘                                               └──────────────────────────────┘
        run-ecf-host.sh                                                                run-ecf-consumer.sh
```

Both JVMs run the full ECF RSA + Generic-provider stack. The host product contains the
host bundle (exporter); the consumer product contains the consumer bundle (importer).
The shared `ecf.api` bundle is in both.

## New bundles

Three new bundles, mirroring the existing `com.kk.pde.ds.api` / `com.kk.pde.ds.imp`
split and conventions (same MANIFEST style, `build.properties`, `eclipse-plugin` pom,
`JavaSE-1.8` BREE, slf4j logging).

### 1. `com.kk.pde.ds.ecf.api`

Plain interface bundle (no DS).

```java
package com.kk.pde.ds.ecf.api;

public interface IRemoteGreet {
    String greet(String name);
}
```

- `Export-Package: com.kk.pde.ds.ecf.api`
- No `Service-Component`, no OSGI-INF.

### 2. `com.kk.pde.ds.ecf.host`

Service provider, exported as remote.

```java
@Component(property = {
    "service.exported.interfaces=*",
    "service.exported.configs=ecf.generic.server",
    "ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server"
})
public class RemoteGreetImpl implements IRemoteGreet {
    @Override
    public String greet(String name) {
        log.info("RemoteGreetImpl.greet({}) invoked remotely", name);
        return "Hello, " + name + "! (served remotely by host)";
    }
}
```

- DS component XML generated under `OSGI-INF/` (Service-Component header), matching the
  project's existing convention of checked-in component XML.
- `Import-Package`: `com.kk.pde.ds.ecf.api`, `org.osgi.service.component.annotations;resolution:=optional`,
  `org.slf4j`, plus any RSA constants package if referenced (prefer string literals to
  avoid a compile dependency on RSA API).
- Provides the `IRemoteGreet` service. RSA's topology manager picks up the
  `service.exported.*` properties and exports it via the Generic server on port 3288.

### 3. `com.kk.pde.ds.ecf.consumer`

Service consumer; imports the remote endpoint via EDEF and invokes it.

```java
@Component
public class RemoteGreetConsumer {
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.MANDATORY)
    void bindGreet(IRemoteGreet greet) {
        log.info("Remote IRemoteGreet bound; invoking...");
        log.info("Remote response: {}", greet.greet("ECF"));
    }
    void unbindGreet(IRemoteGreet greet) { ... }
}
```

- EDEF discovery file: `OSGI-INF/remote-service/remote-greet-endpoint.xml`.
- Manifest header: `Remote-Service: OSGI-INF/remote-service/*.xml`.
- On bundle start, RSA's `EndpointDescriptionLocator` reads the EDEF, imports the
  endpoint, and registers a proxy `IRemoteGreet`. The `@Reference` binds to the proxy
  and the `bind` method calls across the socket.
- Optional convenience: a Gogo command component (`ecf:greet <name>`) registered as a
  `org.apache.felix.service.command.Function` (scope/function service properties) so the
  remote service can be re-invoked on demand from the console.

#### EDEF file (sketch — exact endpoint properties verified at build)

```xml
<endpoint-descriptions xmlns="http://www.osgi.org/xmlns/rsa/v1.0.0">
  <endpoint-description>
    <property name="objectClass" value-type="String">
      <array><value>com.kk.pde.ds.ecf.api.IRemoteGreet</value></array>
    </property>
    <property name="endpoint.id" value="ecftcp://localhost:3288/server"/>
    <property name="service.imported.configs" value-type="String">
      <array><value>ecf.generic.server</value></array>
    </property>
    <property name="ecf.endpoint.id" value="ecftcp://localhost:3288/server"/>
    <property name="ecf.endpoint.id.ns" value="ecf.namespace.generic"/>
    <property name="endpoint.service.id" value-type="Long" value="1"/>
    <property name="endpoint.framework.uuid" value="ecf-demo-host"/>
    <property name="remote.intents.supported" value-type="String">
      <array><value>osgi.basic</value><value>osgi.async</value></array>
    </property>
  </endpoint-description>
</endpoint-descriptions>
```

> The precise required property set (`ecf.endpoint.id.ns` namespace value,
> `endpoint.service.id`, etc.) will be confirmed during implementation by exporting
> from the host and inspecting the generated endpoint (e.g. via WebConsole / RSA logs),
> then transcribing into this static EDEF. This is the highest-risk detail.

## Target platform additions

Add a new `<location includeDependencyDepth="..." type="Maven">` block to
`com.kk.pde.ds.target/*.target` for the ECF 3.14.x stack (groupId `org.eclipse.ecf`):

- `org.eclipse.ecf`
- `org.eclipse.ecf.identity`
- `org.eclipse.ecf.remoteservice`
- `org.eclipse.ecf.sharedobject`
- `org.eclipse.ecf.provider` (Generic provider container factories)
- `org.eclipse.ecf.provider.remoteservice`
- `org.eclipse.ecf.osgi.services.remoteserviceadmin` (RSA impl + topology manager)
- `org.eclipse.ecf.osgi.services.distribution`
- transitive: `org.eclipse.equinox.concurrent` (and any others surfaced by resolution)
- RSA API package (`org.osgi.service.remoteserviceadmin`) — confirm whether it is
  already provided by an existing bundle or needs adding.

**Exact versions are verified empirically**: pick the 3.14.x-era per-bundle versions
from Maven Central, attempt `mvn clean verify`, and adjust until the target platform
resolves on Java 8. This is expected to require one or two iterations.

## Build wiring

- **Root `pom.xml`**: add `com.kk.pde.ds.ecf.api`, `com.kk.pde.ds.ecf.host`,
  `com.kk.pde.ds.ecf.consumer` to `<modules>` (before `feature`/`distribution`).
- **`com.kk.pde.ds.feature/feature.xml`**: add the 3 demo bundles and the ECF stack
  bundles so they land in the p2 repository.
- **Two new product files** in `distribution/`:
  - `com.kk.pde.ds.ecf.host.product` — `type="bundles" includeLaunchers="false"`,
    containing: OSGi core, Felix SCR, Equinox console + Gogo, logback, the ECF stack,
    `com.kk.pde.ds.ecf.api`, `com.kk.pde.ds.ecf.host`.
  - `com.kk.pde.ds.ecf.consumer.product` — same base + `ecf.api` + `ecf.consumer`.
- **`distribution/pom.xml` antrun**: the generic `config.ini` path-fix
  (`**/configuration/config.ini`) already covers new products. Add copy lines for the
  two new run scripts and logback into the two new product output dirs; optionally
  archive them.
- **Run scripts** in `distribution/scripts/`: `run-ecf-host.sh` / `.bat` and
  `run-ecf-consumer.sh` / `.bat`, modeled on the existing `run.sh` (auto-detect OS,
  locate the product dir, `java -jar org.eclipse.osgi_*.jar -configuration configuration
  -console -consoleLog`).

> Note: building two extra products across the 3 configured OS environments adds build
> time. Acceptable for a showcase; if it becomes a problem, the demo products can be
> restricted to the host OS later (out of scope for v1).

## Demo / run instructions (target UX)

```bash
mvn clean verify
# Terminal 1 — start the host (exports IRemoteGreet on :3288)
./distribution/scripts/run-ecf-host.sh
# Terminal 2 — start the consumer (imports via EDEF, invokes greet)
./distribution/scripts/run-ecf-consumer.sh
```

Expected: the consumer's log prints `Remote response: Hello, ECF! (served remotely by
host)`, and the host's log prints `RemoteGreetImpl.greet(ECF) invoked remotely` — proof
the call crossed the socket between two JVMs.

## Verification (success criteria)

1. `mvn clean verify` completes cleanly (target platform resolves; all bundles compile
   on Java 8; p2 repo + products build).
2. Host process starts, ECF Generic server binds `localhost:3288`, endpoint exported
   (visible in host logs / WebConsole RSA view).
3. Consumer process starts, EDEF import succeeds, proxy registered, `@Reference` binds,
   `greet("ECF")` returns the host's string — logged in the consumer.
4. No regression: the existing `com.kk.pde.ds.product` still builds and runs as before.

## Documentation

- `CLAUDE.md`: add the 3 modules to the module-structure list; add an "ECF Remote
  Services" section (architecture, run instructions, port, discovery mechanism).
- `README.md`: add an ECF section.
- `FOR_Kerem.md`: add an engaging ECF section per project convention (what RSA is, how
  the export/import works, the EDEF discovery trick, pitfalls — especially the Java-8
  version pin and endpoint-property fiddliness).

## Risks & mitigations

| Risk | Mitigation |
|------|------------|
| ECF 3.14.x Maven coordinates/versions don't resolve cleanly on Java 8 | Iterate empirically; pin exact versions; fall back to alternative 3.14.x point releases. Highest risk — tackle first. |
| Static EDEF property set incomplete/incorrect → import fails | Export live from host first, inspect the real endpoint properties, transcribe into the EDEF. |
| Two extra products inflate build time | Accept for v1; document option to restrict to host OS. |
| RSA API package (`org.osgi.service.remoteserviceadmin`) missing from target | Add explicitly if resolution reports it missing. |

## Out of scope (YAGNI)

- Network discovery providers (ZooKeeper, jmDNS/Zeroconf).
- Alternative transports (JAX-RS/Jersey, Hazelcast, MQTT).
- Asynchronous remote services (`CompletableFuture` proxies) — could be a follow-up.
- Security/TLS (`ecf.generic.ssl.server`).
- Reusing or remoting the existing `IGreet`.
