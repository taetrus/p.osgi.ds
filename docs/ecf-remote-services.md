# ECF Remote Services: Design and Lessons

This document explains the ECF Remote Services demo in the `com.kk.pde.ds.ecf.*`
bundles. It gives the design, the decisions, and the problems that we found during
the work.

## Summary

The local demo registers the `Greet` service in one bundle and injects it into `App`
with `@Reference`. ECF Remote Services uses the same pattern across two JVMs. The
service and the consumer are in different processes. The method call goes through a
network socket.

The result is a remote method call that has the same form as a local call.

## Bundles

| Bundle | Role |
|--------|------|
| `com.kk.pde.ds.ecf.api` | The contract. It contains one interface: `IRemoteGreet { String greet(String name); }`. Both processes use this interface. Neither process knows the implementation of the other. |
| `com.kk.pde.ds.ecf.host` | Process 1. `RemoteGreetImpl` returns `"Hello, <name>! (served remotely by host)"`. Three service properties tell ECF to export the service. |
| `com.kk.pde.ds.ecf.consumer` | Process 2. `RemoteGreetConsumer` has a `@Reference` to `IRemoteGreet`. No local bundle supplies that service. An EDEF file gives the host address `ecftcp://localhost:3288/server`. ECF reads the file, connects to the host, and registers a proxy that sends each call to the host. |

## No ECF code in the business classes

`RemoteGreetImpl.java` and `RemoteGreetConsumer.java` import no ECF classes. They
import only these packages:

- the shared interface `com.kk.pde.ds.ecf.api`
- the DS annotations `org.osgi.service.component.annotations.*`
- slf4j, for logs

`GreetCommand.java` imports the shared interface and the DS annotations. It does not
import slf4j.

Declarations, not code, control the remote behavior.

The host exports the service with three properties on its `@Component`:

```java
@Component(property = {
    "service.exported.interfaces=*",                              // export all interfaces
    "service.exported.configs=ecf.generic.server",               // use the ECF Generic transport
    "ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server"  // listen on this URI
})
```

ECF Remote Service Admin (RSA) monitors the service registry. When it finds a service
with `service.exported.*` properties, it opens a server socket and publishes the
service. The implementation class does not know about this step.

The consumer imports the service with one manifest header:

```
Remote-Service: OSGI-INF/remote-service/*.xml
```

The header points to an EDEF (Endpoint Description Extender Format) XML file. The file
gives the address of the host. RSA reads the file when the bundle starts. Then RSA
connects to the host and registers a proxy. The `@Reference` binds to the proxy in the
same way as to a local service.

This design follows a core OSGi principle: configuration sets the behavior, not
inheritance.

## The path of one call

`RemoteGreetConsumer` makes the first call from its `@Activate` method, not from the
bind method. The activate method starts a daemon thread with the name
`ecf-consumer-greet`. That thread calls `greet("ECF")` and writes the response to the
log.

```
consumer JVM                                   host JVM
------------                                   --------
@Reference binds the proxy
@Activate starts thread "ecf-consumer-greet"
   │  greet("ECF")
   ▼
ECF generic CLIENT  ──── TCP :3288 ────▶  ECF generic SERVER
                                              │  "Remote Request Handler" thread
                                              ▼
                                          RemoteGreetImpl.greet("ECF")
                                              │  returns "Hello, ECF! ..."
   proxy returns  ◀──── TCP :3288 ─────────┘
   │
   ▼
log: "Remote response: Hello, ECF! (served remotely by host)"
```

We measured one run with two separate JVMs. In that run, these values occurred:

- The round trip took approximately 11 milliseconds.
- The proxy class was `$Proxy6`.
- The host received the call on the thread `Worker-0: Remote Request Handler - greet:0`.

These values come from one run only. Other runs can show different values.

## Design decisions

### Two processes, not one

One JVM can run the host and the consumer and still use the full RSA pipeline. We
used two JVMs because then the bytes really go through a socket. The cost is two
products and two run scripts.

### A new interface, not `IGreet`

If we exported the existing `IGreet`, the local `Greet` and the remote proxy can both
satisfy the `@Reference` in `App`. That makes the binding ambiguous. A separate
interface, `IRemoteGreet`, keeps the two demos apart.

### File-based EDEF discovery, not ZooKeeper or mDNS

Network discovery needs a daemon and can fail without a clear cause. A static EDEF
file is deterministic. Thus the demo gives the same result each time. The trade-off
is that the endpoint address is static in the file (see problem 4).

### The Generic provider

ECF supports many transports, for example JAX-RS, Hazelcast, and MQTT. The Generic
provider is part of ECF and needs no broker. Thus it is the simplest choice for a
demo.

## Problems we found and lessons

1. **The manifests say Java 8, but the runtime is Java 21.** All the project
   manifests declare `JavaSE-1.8`. In this project, "Java 8" means the bytecode target
   and the BREE declaration, not the runtime. ECF moved to Java 11/17 at version 3.15.
   For consistency, we pinned the **3.14.x line**, the last line with `JavaSE-1.8`
   BREEs. The runtime is Java 21, so the Java 17 Equinox bundles also resolve.
   Lesson: check the JVM that runs the product, not the value in the manifest.

2. **Maven does not see OSGi dependencies.** The Tycho Maven location resolves Maven
   POM dependencies. ECF bundles declare their dependencies with OSGi
   `Require-Bundle` and `Import-Package`, and Maven cannot read these headers. Thus we
   listed each bundle in the closure manually:
   - ECF core, identity, provider, and sharedobject
   - RSA, the RSA proxy bundle, and `asyncproxy`
   - four Equinox bundles
   - the RSA API package

   The build showed each missing bundle, one at a time. Lesson: in Tycho, the target
   platform is an explicit bill of materials, not a dependency graph.

3. **The product build is the real integration test.** All the bundles compiled,
   because they need only the interface. The missing
   `org.eclipse.ecf.remoteservice.asyncproxy` package appeared only when Tycho
   assembled a runnable product. Lesson: compilation shows that the syntax is correct.
   Product resolution shows that the system can start.

4. **You cannot guess the EDEF values.** Our first EDEF used
   `ecf.endpoint.id.ns = ecf.namespace.generic`. That value is incorrect. The Generic
   provider uses the class name `org.eclipse.ecf.core.identity.StringID`. We found the
   correct value with this procedure:
   1. Start the host.
   2. Read the `ECFEndpointDescription` in the host log.
   3. Copy the real values into the EDEF file.

   Lesson: when you are not sure, let the producer give you the values. The value
   `ecf.rsvc.id=1` was predictable. The host exports only one service, so its
   container-relative ID is always 1.

5. **`pkill -f "...product"` found no process.** At that time, the product path was
   the working directory of the process, not part of its command line. `pkill -f`
   compares against the command line (`argv`), not the working directory (`cwd`).
   Also, a port that an old process still holds can cause a new server to fail
   silently. The old server continues to answer, so the failure stays hidden.

   The current run scripts give the absolute path of the OSGi jar to `-jar`. That path
   contains the product ID. The `osgi.noShutdown` setting is now in
   `distribution/configuration/config.ini`, not on the command line. To stop a
   process, use one of these methods:
   - Type `exit` at the `osgi>` prompt. Then type `y` when Equinox asks.
   - Run `pkill -f com.kk.pde.ds.ecf.host.product` or
     `pkill -f com.kk.pde.ds.ecf.consumer.product` from a different terminal.

6. **Two JVMs cannot both use port 8080.** Felix HTTP and the WebConsole use port
   8080. Two demo JVMs would both try to open that port. Thus the two ECF products do
   not include the HTTP bundles. If you want a web console in each process, give each
   process a different `org.osgi.service.http.port`.

## Run the demo

The build makes two dedicated products from `distribution/com.kk.pde.ds.ecf.host.product`
and `distribution/com.kk.pde.ds.ecf.consumer.product`. The ECF bundles come from Maven
Central, with the groupId `org.eclipse.ecf`.

1. Build the project:
   ```bash
   ./mvnw clean verify
   ```
   On Windows, use `mvnw.cmd clean verify`.
2. In terminal 1, start the host.

   macOS and Linux:
   ```bash
   ./distribution/scripts/run-ecf-host.sh
   ```
   Windows `cmd.exe`:
   ```cmd
   distribution\scripts\run-ecf-host.bat
   ```
3. In terminal 2, start the consumer.

   macOS and Linux:
   ```bash
   ./distribution/scripts/run-ecf-consumer.sh
   ```
   Windows `cmd.exe`:
   ```cmd
   distribution\scripts\run-ecf-consumer.bat
   ```
4. Make sure that the consumer log shows `Remote response: Hello, ECF! (served remotely by host)`.
5. To call the host again, type `ecf:greet World` at the consumer `osgi>` prompt.

`ecf:greet` is a custom Gogo shell command. `GreetCommand` in
`com.kk.pde.ds.ecf.consumer` supplies it.

## The same pattern in the spike

The multi-frame / multi-JVM UI spike uses the same ECF pattern on port 3289.
`CatalogServiceImpl` in `com.kk.pde.ds.spike.master` exports `ICatalogService` on
`ecftcp://localhost:3289/catalog`. For the design and the results, see
[spike-isolation-results.md](spike-isolation-results.md).

## Conclusion

A good distributed design hides the network but does not hide the fact that a network
is there. With ECF Remote Services, you continue to write plain Java interfaces and DS
`@Reference`s. A declarative layer controls the sockets, the serialization, and the
proxies. The important skill is to write the correct declarations. Then RSA, which is
existing code, does the remote work.
