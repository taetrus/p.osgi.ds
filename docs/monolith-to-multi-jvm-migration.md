# Migration Design: Splitting a Swing/OSGi Monolith into Multiple JVMs

## Context

The current application is a **single OSGi JVM** containing **hundreds of bundles**, many of
which contribute Swing panels that are combined into **one shared `JFrame`**. Because every
panel shares one **Event Dispatch Thread (EDT)**, one **heap**, and one **process**, the app
suffers rampant freezes and instability from *all* of:

- panels blocking the EDT on backend/IO calls,
- panels' own heavy rendering/computation on the shared EDT,
- memory/GC pressure from one bundle stalling everyone,
- crashes/exceptions from one panel destabilizing the whole app.

Many teams work on different panels, so one team's mistake degrades everyone's experience.

**Goal:** split the monolith into **multiple independent JVMs ("apps"), one per panel-group**,
for true fault and performance isolation — incrementally, reusing the ECF Remote Services
foundation already in this repo, **without rewriting panels** and **without UI remoting**
(image streaming / native window embedding were evaluated and rejected as too expensive).

**Locked decisions:**
- **Separate windows are acceptable** — no single-combined-`JFrame` requirement. (This is what
  makes the cheap approach viable; the single-window constraint was the only thing that forced
  the expensive UI-remoting options.)
- **Coordination centers on shared ECF Remote Services** (typed request/response + async),
  with distributed events as a secondary mechanism only where pub/sub fits better.
- **Incremental** migration — no big-bang rewrite.

## Why this works (isolation matrix)

Running each panel-group in its own JVM with its own window gives automatic isolation of every
freeze cause — *without* moving any Swing component across a process boundary:

| Freeze cause | Fixed by |
|---|---|
| Panels' own rendering/compute on EDT | **Separate EDT per app** — App-A's slow paint can't freeze App-B |
| Panels block on backend/IO | App-local threads + remote calls made **off-EDT** (async proxy / SwingWorker) |
| Memory / GC pressure | **Separate heaps** — one app's GC pause/leak doesn't stop others |
| Crashes / exceptions | **Separate processes** — a crash takes down one window; supervisor restarts it |

Panels keep their existing Swing code and run *for real* in their own window — the only change
is that cross-group interactions that used to be in-JVM method calls become ECF service calls.

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

**App** = a deployable unit (one panel-group + its own services) in its own OSGi JVM, with its
own `JFrame`(s). Built and launched exactly like the `com.kk.pde.ds.ecf.host`/`consumer`
products + run scripts already in this repo.

**Shell / Launcher** = a small process that starts and supervises apps, hosts global chrome
(menu bar, toolbar, status) and dispatches its actions to apps over ECF, and (optionally)
coordinates window geometry to approximate a docked/tiled layout.

**Contracts tier** = shared `*.api` bundles. They contain **only** ECF service interfaces and
`Serializable` DTOs. An app may depend on another app's *contract* bundle, **never** on its
*implementation* bundle. This is both the integration mechanism and the firewall that keeps
teams decoupled.

## Core principles (the rules every app follows)

1. **Coupling only through contracts.** No app references another app's impl classes — only its
   `*.api` interfaces/DTOs. If you need something from another team, it's a service call.
2. **Serializable DTOs, never live objects.** Data crossing a JVM boundary is a versioned,
   serializable value object. No passing live domain objects or Swing models across apps.
3. **Async at the boundary, always.** UI never blocks the EDT on a remote call. Use ECF
   **async remote-service proxies** (`Future`/`CompletableFuture` — the
   `org.eclipse.ecf.remoteservice.asyncproxy` bundle is already in the target platform) and/or
   `SwingWorker`. Apply results back on the EDT via `invokeLater`.
4. **Each app owns its data.** Shared cross-cutting state (session, auth, user context,
   selection/"current record", config) lives behind a **service owned by the Core app**, not in
   per-app singletons.
5. **Design for partial failure.** Every remote call can time out or fail; the peer app may be
   down or restarting. Panels degrade gracefully (placeholder/disabled) and recover
   automatically when the service rebinds (DS dynamic `@Reference` already does this — proven by
   `RemoteGreetConsumer` in this repo).

## The contract layer (since coordination = shared remote services)

For each shared domain currently accessed across panel-groups in-JVM:

1. Define an interface in a shared `*.api` bundle, e.g. `com.acme.<domain>.api.IOrderService`,
   plus `Serializable` DTOs (`OrderDTO`, `OrderQuery`, …). Mirror the existing
   `com.kk.pde.ds.ecf.api` bundle conventions (exported package, `JavaSE-1.8` BREE).
2. The **owning app** implements it as a DS `@Component` and exports it with the ECF properties
   already proven here (`service.exported.interfaces=*`, `service.exported.configs=ecf.generic.server`,
   `ecf.exported.containerfactoryargs=...`).
3. **Consuming apps** inject the proxy via `@Reference` and call it **off-EDT**.

**Async pattern (recommended UI call shape):**
```java
// In a panel, on the EDT (e.g. button handler) — never blocks:
new SwingWorker<OrderDTO, Void>() {
    protected OrderDTO doInBackground() { return orderService.find(id); } // remote, off-EDT
    protected void done() { render(get()); }                              // back on EDT
}.execute();
```
(Or use an async proxy returning `CompletableFuture<OrderDTO>` and complete onto the EDT.)

**Contract discipline:** version the `*.api` bundles semantically; keep them backward-compatible
because apps deploy and restart **independently** — a consumer may run against an older or newer
provider during a rolling update. Add methods, don't break signatures; evolve DTOs additively.

## Discovery & topology at scale (decide early)

The showcase uses a **static EDEF file** for a single endpoint. That does **not** scale to many
apps × many services (each pairing would need a hand-maintained EDEF with matching
`ecf.rsvc.id`s). For the real system choose one:

- **A discovery provider** — ECF supports Zookeeper and JmDNS/Zeroconf. Apps publish/discover
  endpoints automatically. Best for dynamic, many-endpoint topologies.
- **A central Registry/Core app** — a well-known endpoint that every app registers with and
  queries to locate peers. Simpler to reason about and operate; doubles as the home for shared
  session/config/auth services and the supervisor.

**Recommendation:** a **Core/Registry app** (well-known fixed endpoint) that owns discovery +
cross-cutting shared services. It's the one "always-on" peer; everything else finds services
through it. Add a network discovery provider later only if dynamic scaling demands it.

## Decomposition strategy — choosing app boundaries

- **Follow team ownership and coupling seams.** A good boundary is where two panel-groups talk
  *little*. Split along low-traffic seams; keep chatty, cohesive clusters **together** in one app
  (remoting a tight inner loop = latency + complexity).
- **Map the coupling first.** Catalog every cross-group interaction in the monolith today —
  direct method calls between panels, shared singletons, shared listeners/EventAdmin topics,
  shared caches/session. Each entry becomes either a **service contract** (request/response) or,
  secondarily, a **distributed event** (fire-and-forget notification). This catalog *is* the
  contract backlog.
- **Promote shared global state to Core services before splitting anything** — login/session,
  user context, "current selection/record", config, feature flags, caches. Until these are
  services, no app can fully stand alone. This is the highest-effort prerequisite.

## Incremental migration playbook

**Phase 0 — Foundations (before carving any panel-group):**
- Establish the `*.api` contracts + DTO conventions and the async-call UI pattern.
- Stand up the **Core/Registry app** (discovery + supervisor skeleton).
- Extract cross-cutting shared state (session, auth, selection/context, config) into **Core ECF
  services**. *This is the gating prerequisite* — every future app depends on it.
- Build the **Shell/Launcher** skeleton; initially it just launches today's monolith as "App 0".

**Phase 1 — Carve the worst offender:**
- Pick the panel-group causing the most freezes (or the most self-contained one).
- Move it + its services into a new **app/JVM** (own product/feature/run-script, modeled on the
  ECF products in this repo). Its window opens separately (or shell-docked).
- Replace its in-JVM cross-group calls with ECF service calls (now possible because contracts +
  Core state exist from Phase 0).
- **Validate isolation:** deliberately induce a freeze/leak/crash inside the carved app and
  confirm the monolith and other apps keep running. This is the proof the whole effort hinges on.

**Phase 2…N — peel off the next group each iteration.** The monolith shrinks; each carve-out
delivers isolation value immediately. No big-bang.

**Final state:** the monolith is gone (or reduced to a thin Core), and the Shell orchestrates N
isolated apps coordinated over ECF.

## Window / UX strategy

- **Default:** independent OS top-level windows, one per app. Simplest; OS window manager handles
  them.
- **Optional docked/tiled look:** the Shell coordinates window geometry over a layout service
  (each app reports preferred size; Shell assigns bounds) to *approximate* the old combined
  layout. It's an approximation — not a true single window.
- **Global menu/toolbar:** hosted by the Shell; menu actions dispatch to the relevant app via a
  service (e.g., `IAppCommands.execute(commandId, args)`).
- **Caveats:** focus, z-order, and taskbar grouping behave like separate apps; cross-window
  drag-and-drop won't work; a shared "always on top" status bar must live in the Shell.

## Failure handling & supervision

- **Supervisor (in Shell/Core):** monitors app liveness via a heartbeat service or process
  liveness; **restarts crashed apps**. A crash is now a contained, recoverable event.
- **Graceful degradation:** when a remote service is unavailable, consuming panels show a
  placeholder/disabled state and **auto-recover on rebind** (DS dynamic `@Reference`; proven).
- **Timeouts on all remote calls**; treat every cross-app call as fallible. Consider a simple
  circuit-breaker for flaky peers.
- **Distributed logging:** add a correlation/request id across apps so a single user action can
  be traced through multiple JVMs (debugging is harder once multi-process).

## Reuse from this repo (the foundation already built)

The `com.kk.pde.ds.ecf.api` / `.host` / `.consumer` bundles + the two products + the
`run-ecf-*.sh/.bat` scripts are the **reference template** for "a contract + an app + its own
JVM + a consumer that binds the remote service." Each migration carve-out follows that exact
shape. DS dynamic `@Reference` rebinding is the proven mechanism for automatic recovery when an
app restarts.

## Risks & trade-offs (honest)

- **Coupling extraction is the real cost**, not UI tech. A hundred-bundle monolith likely has
  dense, implicit cross-panel coupling; turning it into explicit contracts is the bulk of the work.
- **Shared-state extraction (Phase 0) is the riskiest prerequisite.** Until session/selection/
  config are services, apps can't truly stand alone.
- **Serialization boundary:** no more passing live objects; DTO design and churn are ongoing.
- **Latency:** cross-app calls are now (localhost) network calls. Keep chatty interactions inside
  one app; only remote across low-traffic seams.
- **Discovery/versioning at scale** needs real discipline (registry/discovery + semver contracts).
- **Operational complexity:** N processes to launch, supervise, log, and deploy vs. one.

## Recommended proof-of-concept (before committing teams)

Build a **two-app spike** in this repo to validate the pattern end-to-end: App-1 (master panel,
own JVM/window) and App-2 (detail panel, own JVM/window), where selecting in App-1 drives App-2
via a shared ECF service + the Core-held "current selection" state — proving separate
EDTs/heaps/crash-domains coordinated purely by services, with **zero** panel remoting. Then run
the isolation test (freeze/crash one app, confirm the other survives).

## Out of scope

True single-window compositing; native window embedding; image-streaming UI remoting; web/browser
migration; replacing Swing.
