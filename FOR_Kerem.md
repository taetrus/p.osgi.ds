# FOR Kerem — ECF Remote Services, Explained Like a Story

> This document covers the **ECF Remote Services** showcase added to the project
> (the `com.kk.pde.ds.ecf.*` bundles). It's written to be read, not endured.

## The one-sentence idea

You already had a service (`Greet`) registered in one place and injected somewhere
else with `@Reference`. ECF Remote Services is the *exact same trick* — except the
"somewhere else" is a **different computer (or at least a different JVM)**, and the
method call quietly travels over a network socket to get there.

That's the whole magic: **a remote method call that looks identical to a local one.**

## The cast of characters

Think of it like a restaurant with the kitchen and the dining room in two separate
buildings, connected by a dumbwaiter.

- **`com.kk.pde.ds.ecf.api`** — the *menu*. Just one interface:
  `IRemoteGreet { String greet(String name); }`. Both buildings agree on the menu;
  neither knows how the other building is built.
- **`com.kk.pde.ds.ecf.host`** — the *kitchen* (process #1). It actually cooks:
  `RemoteGreetImpl` returns `"Hello, <name>! (served remotely by host)"`. It hangs a
  sign in the window saying "I'll serve anyone over the dumbwaiter" — that "sign" is
  three service properties (more below).
- **`com.kk.pde.ds.ecf.consumer`** — the *dining room* (process #2). A waiter
  (`@Reference`) wants an `IRemoteGreet`. There's none in this building... but a little
  card (the **EDEF file**) taped to the wall says "the kitchen is at
  `ecftcp://localhost:3288/server`." ECF reads the card, opens the dumbwaiter, and a
  **proxy** waiter appears that forwards every order to the real kitchen.

## The genuinely beautiful part

Open `RemoteGreetImpl.java`. Count the ECF imports. **Zero.** Open
`RemoteGreetConsumer.java`. ECF imports? **Zero.** The business code imports only the
shared interface and slf4j.

All the remoting lives in **declarations**, not code:

- The host exports by setting three properties on its `@Component`:
  ```java
  @Component(property = {
      "service.exported.interfaces=*",                              // export everything
      "service.exported.configs=ecf.generic.server",               // use ECF's Generic transport
      "ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server"  // listen here
  })
  ```
  ECF's Remote Service Admin (RSA) is constantly watching the service registry. The
  instant it sees a service with `service.exported.*` properties, it opens a server
  socket and publishes the service. The implementation class is none the wiser.

- The consumer discovers by a single manifest header:
  ```
  Remote-Service: OSGI-INF/remote-service/*.xml
  ```
  plus an XML file (EDEF) describing where the kitchen is. RSA reads it on bundle
  start, connects, and registers a proxy. The `@Reference` binds to the proxy exactly
  as if it were local.

This is the OSGi philosophy taken to its logical extreme: **behavior is configuration,
not inheritance.**

## How a single greeting travels (the round trip)

```
consumer JVM                                   host JVM
------------                                   --------
@Reference binds proxy $Proxy6
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

We measured this live: **~11 milliseconds** door to door, two separate JVMs. The host
log even shows the call arriving on a thread literally named
`Worker-0: Remote Request Handler - greet:0`.

## Why these specific choices (the "why", not just the "what")

- **Two separate processes, not one.** We could have run host + consumer in one JVM and
  still exercised the whole RSA pipeline. We chose two JVMs because that's the *honest*
  demo — the bytes really cross a socket. Cost: two products and two run scripts.

- **A new interface (`IRemoteGreet`), not reusing `IGreet`.** If we'd remoted the
  existing `IGreet`, the local `Greet` and the remote proxy would both satisfy `App`'s
  `@Reference` — an ambiguity waiting to bite. A separate interface keeps the two demos
  from stepping on each other.

- **File-based EDEF discovery, not ZooKeeper/mDNS.** Network discovery is "magic" but
  flaky and needs a daemon. A static EDEF file is boring and *deterministic* — perfect
  for a reproducible showcase. The tradeoff: the file hardcodes the endpoint (see
  pitfalls).

- **The Generic provider.** ECF supports many transports (JAX-RS, Hazelcast, MQTT...).
  The Generic provider is built in and needs no broker — least friction for a demo.

## The bugs we hit (so you don't have to)

These are the real lessons — the stuff that doesn't show up in tutorials.

1. **"Java 8" was a lie (a useful one).** The project's manifests all say
   `JavaSE-1.8`, but the actual JRE is **Java 21**. "Java 8" here means *bytecode
   target and BREE declaration*, not runtime. This mattered: ECF moved to Java 11/17
   at version 3.15, so we pinned the **3.14.x line** (the last with `JavaSE-1.8`
   BREEs) for *consistency* — but since the runtime is really 21, even the Java-17
   Equinox bundles resolve fine. Always check what JVM actually runs, not what the
   manifest claims.

2. **Maven doesn't understand OSGi dependencies.** Tycho's "Maven location" resolves
   *Maven POM* dependencies. But ECF bundles declare their needs with OSGi
   `Require-Bundle` / `Import-Package`, which Maven can't see. So we had to enumerate
   **every** bundle in the closure by hand — ECF core, identity, provider, sharedobject,
   RSA, the proxy bundle, `asyncproxy`, four Equinox bundles, and the RSA API package.
   The build told us what was missing, one bundle at a time. Lesson: in Tycho, the
   target platform is an explicit bill of materials, not a dependency graph.

3. **The product is the real integration test.** All the bundles *compiled* happily
   (they only need the interface). It was only when Tycho tried to assemble a runnable
   *product* that the missing `org.eclipse.ecf.remoteservice.asyncproxy` package
   surfaced. Compiling proves syntax; resolving a product proves the system can
   actually boot.

4. **The EDEF file is fussy, and you can't guess it.** Our first EDEF said
   `ecf.endpoint.id.ns = ecf.namespace.generic`. Wrong. The Generic provider actually
   uses the *class name* `org.eclipse.ecf.core.identity.StringID`. The only way we
   learned this was to **run the host, read the `ECFEndpointDescription` it printed,
   and copy the real values.** When in doubt, let the producer tell you the truth.
   (Happily, `ecf.rsvc.id=1` *was* guessable — a single exported service always gets
   container-relative id 1.)

5. **`pkill -f "...product"` matched nothing.** The product path is the process's
   *working directory*, not part of its command line, so `pkill -f` couldn't see it.
   We had to match on an actual argument (`osgi.noShutdown`). A reminder that `pkill
   -f` greps `argv`, not `cwd` — and that a "still bound" port will silently let a new
   server fail while an old one keeps answering, masking the problem.

6. **Two servers, one port 8080.** Felix WebConsole/HTTP wants port 8080. Two demo
   JVMs would fight over it, so we left HTTP out of both ECF products entirely. If you
   want a web console, give each process a different `org.osgi.service.http.port`.

## How to run it

```bash
mvn clean verify
# Terminal 1 — the kitchen
./distribution/scripts/run-ecf-host.sh
# Terminal 2 — the dining room
./distribution/scripts/run-ecf-consumer.sh
```

Watch the consumer print `Remote response: Hello, ECF! ...`. Then, at its `osgi>`
prompt, type `ecf:greet World` to send another order through the dumbwaiter on demand.

## The takeaway

Good distributed-systems design hides the network *without lying about it*. ECF Remote
Services lets you keep writing plain Java interfaces and DS `@Reference`s, while a
declarative layer handles sockets, serialization, and proxies. The skill isn't writing
clever remoting code — it's **arranging the declarations** so the clever code already
written (RSA) does the work for you. That's leverage.
