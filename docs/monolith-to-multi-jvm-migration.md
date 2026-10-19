# Migration Design: Split a Swing/OSGi Monolith into Multiple JVMs

## Context

The current application is a **single OSGi JVM** with **hundreds of bundles**. Many bundles contribute Swing panels, and the application puts all panels into **one shared `JFrame`**. All panels share one **Event Dispatch Thread (EDT)**, one **heap** and one **process**. Thus the application freezes and becomes unstable frequently, for *all* of these causes:

- Panels block the EDT on backend calls or I/O.
- Panels do heavy rendering or calculation on the shared EDT.
- Memory or GC pressure from one bundle stops all panels.
- Crashes or exceptions in one panel make the full application unstable.

Many teams work on different panels. Thus an error from one team makes the application worse for all users.

**Goal:** split the monolith into **multiple independent JVMs ("apps"), one for each panel group**. This gives real isolation of failures and performance. Do the split incrementally, and use the ECF Remote Services foundation that this repository already has. Do **not rewrite panels**, and do **not send the UI over the network**. We examined image streaming and native window embedding, and we rejected them because they are too expensive.

**Fixed decisions:**

- **Separate windows are acceptable.** There is no requirement for one combined `JFrame`. This decision makes the cheap approach possible. Only the single-window requirement made the expensive UI-remoting options necessary.
- **Shared ECF Remote Services are the primary coordination mechanism.** They give typed request/response calls and asynchronous calls. Use distributed events only where publish/subscribe is a better fit.
- **The migration is incremental.** There is no rewrite of everything at one time.

## Why this design gives isolation

Each panel group runs in its own JVM with its own window. This isolates each cause of a freeze automatically. No Swing component goes across a process boundary.

| Cause of the freeze | Solution |
|---|---|
| Rendering and calculation in the panels on the EDT | **A separate EDT for each app.** A slow paint in App-A cannot freeze App-B. |
| Panels that block on backend calls or I/O | Threads in each app, and remote calls **off the EDT** (async proxy or `SwingWorker`) |
| Memory and GC pressure | **Separate heaps.** A GC pause or a leak in one app does not stop the other apps. |
| Crashes and exceptions | **Separate processes.** A crash stops one window only. The supervisor starts it again. |

The panels keep their existing Swing code and run in their own window. Only one thing changes: calls between panel groups that were method calls in one JVM become ECF service calls.

## Target architecture

```
                     ┌──────────────────────── Shell / Launcher (own JVM) ─────────────────────────┐
                     │  starts & supervises apps · global menu/toolbar · window layout · restarts   │
                     └───────────────┬─────────────────────┬─────────────────────┬─────────────────┘
                                     │                      │                     │
              ┌──────────────────────▼──┐   ┌───────────────▼─────────┐  ┌────────▼──────────────────┐
              │ App A (JVM, JFrame,EDT,  │   │ App B (JVM, JFrame,EDT,  │  │ Core/Registry App (JVM)   │
              │ heap) — panel-group A     │   │ heap) — panel-group B    │  │ discovery + shared state  │
              │  uses contracts only ─────┼───┼──► ECF Remote Services ◄─┼──┤  session/auth/config svc  │
              └───────────────────────────┘   └──────────────────────────┘  └───────────────────────────┘
                              ▲  shared *.api bundles (interfaces + Serializable DTOs) — the ONLY cross-app coupling
                              └───────────────────────────────────────────────────────────────────────────────────
```

**App:** a unit that you deploy. It contains one panel group and its own services, in its own OSGi JVM, with its own `JFrame` or frames. Build and start it in the same way as the `com.kk.pde.ds.ecf.host` and `com.kk.pde.ds.ecf.consumer` products and run scripts in this repository.

**Shell / Launcher:** a small process with these tasks:

- It starts and supervises the apps.
- It holds the global UI parts (menu bar, toolbar, status bar) and sends their actions to the apps over ECF.
- Optionally, it controls the window geometry to approximate a docked or tiled layout.

**Contract tier:** the shared `*.api` bundles. They contain **only** ECF service interfaces and `Serializable` DTOs. An app can depend on the *contract* bundle of another app. It must **never** depend on the *implementation* bundle of another app. This rule is the integration mechanism. It is also the firewall that keeps the teams independent.

## Rules for each app

1. **Use only contracts for coupling.** No app uses the implementation classes of another app. It uses only the `*.api` interfaces and DTOs. If you need something from another team, make a service call.
2. **Use serializable DTOs, never live objects.** Data that goes across a JVM boundary is a serializable value object with a version. Do not send live domain objects or Swing models between apps.
3. **Make all boundary calls asynchronous.** The UI must never block the EDT on a remote call. Use ECF **async remote-service proxies** (`Future` or `CompletableFuture`), `SwingWorker`, or both. The target platform already contains the `org.eclipse.ecf.remoteservice.asyncproxy` bundle. Use `invokeLater` to apply the results on the EDT.
4. **Each app owns its data.** Shared state for all apps is behind a **service that the Core app owns**, not in singletons in each app. This state includes session, authentication, user context, the selection or "current record", and configuration.
5. **Design for partial failure.** Each remote call can time out or fail. The other app can be stopped or in a restart. Panels show a placeholder or a disabled state. They recover automatically when the service binds again. DS dynamic `@Reference` does this. `RemoteGreetConsumer` in this repository uses it. The spike showed one limit: with a static EDEF file, the service does not bind again after the provider restarts. See [spike-isolation-results.md](spike-isolation-results.md).

## The contract layer

Coordination uses shared remote services. Thus, do these steps for each shared domain that panel groups use across each other in the JVM today:

1. Define an interface in a shared `*.api` bundle, for example `com.acme.<domain>.api.IOrderService`. Also define `Serializable` DTOs (`OrderDTO`, `OrderQuery`, …). Use the conventions of the existing `com.kk.pde.ds.ecf.api` bundle (exported package, `JavaSE-1.8` BREE).
2. In the **owner app**, implement the interface as a DS `@Component`. Export it with the ECF properties that this repository already uses: `service.exported.interfaces=*`, `service.exported.configs=ecf.generic.server` and `ecf.exported.containerfactoryargs=...`.
3. In each **consumer app**, inject the proxy with `@Reference`. Call it **off the EDT**.

**Asynchronous pattern (recommended shape for UI calls):**

```java
// In a panel, on the EDT (e.g. button handler) — never blocks:
new SwingWorker<OrderDTO, Void>() {
    protected OrderDTO doInBackground() { return orderService.find(id); } // remote, off-EDT
    protected void done() { render(get()); }                              // back on EDT
}.execute();
```

You can also use an async proxy that returns `CompletableFuture<OrderDTO>`, and complete the result on the EDT.

**Contract discipline:** give the `*.api` bundles semantic versions. Keep them backward-compatible, because apps deploy and restart **independently**. During a rolling update, a consumer can run against an older or a newer provider. Add methods, but do not change existing signatures. Add fields to DTOs, but do not remove fields.

## Discovery and topology at scale (decide early)

The showcase uses a **static EDEF file** for one endpoint. This does **not** scale to many apps with many services. Each pair would need an EDEF file that you keep by hand, with matching `ecf.rsvc.id` values. For the real system, select one of these options:

- **A discovery provider.** ECF supports ZooKeeper and JmDNS/Zeroconf. Apps publish and discover endpoints automatically. This option is best for dynamic topologies with many endpoints.
- **A central Registry/Core app.** It has a well-known endpoint. Each app registers with it and asks it for the location of other apps. This option is easier to understand and to operate. The same app can also hold the shared session, configuration and authentication services, and the supervisor.

**Recommendation:** use a **Core/Registry app** with a well-known fixed endpoint. It owns discovery and the shared services for all apps. It is the one peer that must always run. All other apps find services through it. Add a network discovery provider later, only if dynamic scaling makes it necessary.

## How to select app boundaries

- **Follow team ownership and coupling seams.** A good boundary is between two panel groups that have *few* interactions. Split along seams with low traffic. Keep clusters with many interactions **together** in one app. A remote call in a tight inner loop adds latency and complexity.
- **Make a list of the coupling first.** Record each interaction between groups in the monolith today. Examples are direct method calls between panels, shared singletons, shared listeners or EventAdmin topics, and shared caches or session data. Each item becomes a **service contract** (request/response) or, as a second option, a **distributed event** (a notification with no response). This list *is* the backlog of contracts.
- **Move shared global state to Core services before you split anything**. This state includes login and session, user context, the "current selection" or record, configuration, feature flags and caches. No app can operate alone until these are services. This prerequisite needs the most work.

## Incremental migration plan

**Phase 0: foundations (before you move a panel group):**

- Define the `*.api` contracts, the DTO conventions and the asynchronous UI call pattern.
- Create the **Core/Registry app** with a first version of discovery and the supervisor.
- Move the shared state for all apps (session, authentication, selection and context, configuration) into **Core ECF services**. *This is the prerequisite for all other work.* Each future app depends on it.
- Build the first version of the **Shell/Launcher**. At first, it only starts the current monolith as "App 0".

**Phase 1: move the worst panel group:**

1. Select the panel group that causes the most freezes, or the group that is the most self-contained.
2. Move the group and its services into a new **app/JVM**, with its own product, feature and run script. Use the ECF products in this repository as the model. Its window opens separately, or the Shell docks it.
3. Replace its calls to other groups in the JVM with ECF service calls. Phase 0 makes this possible, because the contracts and the Core state exist.
4. **Test the isolation.** Cause a freeze, a leak or a crash in the moved app on purpose. Make sure that the monolith and the other apps continue to run. The full effort depends on this proof.

**Phase 2 to N: move the next group in each iteration.** The monolith becomes smaller. Each moved group gives isolation immediately. There is no rewrite of everything at one time.

**Final state:** the monolith no longer exists, or only a thin Core remains. The Shell controls N isolated apps that coordinate over ECF.

## Window and UX strategy

- **Default:** one independent OS top-level window for each app. This is the easiest option. The OS window manager controls the windows.
- **Optional docked or tiled look:** the Shell controls the window geometry through a layout service. Each app reports its preferred size, and the Shell gives the bounds. This *approximates* the old combined layout. It is an approximation, not a real single window.
- **Global menu and toolbar:** the Shell holds them. A menu action goes to the applicable app through a service, for example `IAppCommands.execute(commandId, args)`.
- **Limits:** focus, z-order and taskbar grouping behave as for separate applications. Drag-and-drop between windows does not operate. A shared "always on top" status bar must be in the Shell.

## Failure handling and supervision

- **Supervisor (in the Shell or the Core):** it monitors the apps through a heartbeat service or through the process state. It **starts crashed apps again**. Thus a crash becomes a contained event that you can recover from.
- **Graceful degradation:** when a remote service is not available, the consumer panels show a placeholder or a disabled state. They **recover automatically when the service binds again** (DS dynamic `@Reference`). The spike showed that this needs a discovery provider or a new import after a restart. See [spike-isolation-results.md](spike-isolation-results.md).
- **Timeouts on all remote calls:** each call between apps can fail. Think about a simple circuit breaker for peers that are not reliable.
- **Distributed logging:** add a correlation or request ID across apps. Then you can follow one user action through multiple JVMs. Debugging becomes more difficult with multiple processes.

## What this repository already gives

The `com.kk.pde.ds.ecf.api`, `com.kk.pde.ds.ecf.host` and `com.kk.pde.ds.ecf.consumer` bundles, the two ECF products and the `run-ecf-*.sh` and `run-ecf-*.bat` scripts are the **reference template**. They show a contract, an app in its own JVM, and a consumer that binds the remote service. Each moved group uses that same shape. DS dynamic `@Reference` rebinding is the mechanism for automatic recovery when an app restarts.

## Risks and trade-offs

- **The extraction of the coupling is the real cost, not the UI technology**. A monolith with hundreds of bundles probably has much implicit coupling between panels. Most of the work is to change this coupling into explicit contracts.
- **The extraction of the shared state (Phase 0) is the prerequisite with the highest risk**. Apps cannot operate alone until session, selection and configuration are services.
- **Serialization boundary:** you cannot send live objects. DTO design and DTO changes continue for the life of the system.
- **Latency:** calls between apps are now network calls (on localhost). Keep interactions with much traffic inside one app. Use remote calls only across seams with low traffic.
- **Discovery and versions at scale** need real discipline: a registry or discovery provider, and contracts with semantic versions.
- **Operational complexity:** you must start, supervise, log and deploy N processes, not one.

## Proof of concept

Before teams start, build a **spike with two apps** in this repository to test the pattern from end to end:

- App-1 has the master panel, in its own JVM and window.
- App-2 has the detail panel, in its own JVM and window.
- A selection in App-1 changes App-2 through a shared ECF service and a shared "current selection" state.

The spike must show separate EDTs, heaps and crash domains that coordinate only through services, with **no** panels sent over the network. Then run the isolation test: freeze or crash one app, and make sure that the other app continues to run.

**Status: the spike now exists.** The bundles are `com.kk.pde.ds.spike.api`, `com.kk.pde.ds.spike.master` and `com.kk.pde.ds.spike.detail`. For the results, see [spike-isolation-results.md](spike-isolation-results.md). The spike has one difference from this design: App-1 keeps the selection in `CatalogServiceImpl`. There is no Core app. The `ICatalogService` interface stays the same if a Core app holds the selection later.

## Out of scope

These items are out of scope:

- a real single window that combines the UI of all apps
- native window embedding
- UI remoting through image streaming
- migration to a web or browser UI
- replacement of Swing
