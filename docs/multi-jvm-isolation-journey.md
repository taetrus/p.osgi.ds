# From a Freezing Monolith to Isolated Apps

*How we took a single-JVM Swing/OSGi application that froze for everyone and found a
path to splitting it into independent, fault-isolated processes — without rewriting
the panels.*

---

## Executive summary

A large OSGi application runs **hundreds of bundles in one JVM**, with many teams'
Swing panels combined into a **single window**. Because everything shares one event
thread, one heap, and one process, **one team's mistake freezes or crashes the whole
app for everyone**.

We explored several ways to split it across processes, discarded the expensive ones,
and landed on a cheap, incremental architecture: **one JVM per panel-group, in its own
window, coordinated only by shared ECF remote services**. We then **built a working
two-app proof-of-concept** that demonstrates the isolation for real — and it surfaced
the one piece of infrastructure still needed (dynamic service discovery), which we are
now adding.

**Outcome:** a validated, low-cost, team-by-team migration path off the monolith — with
running code to prove it.

---

## Part 1 — The problem

The application is a classic "big ball of mud" desktop client:

- **Hundreds of OSGi bundles** in **one JVM**.
- **Many Swing panels** from **many teams**, all docked into **one `JFrame`**.

Everything shares three things, and each is a single point of failure:

| Shared resource | Failure it causes |
|---|---|
| One **Event Dispatch Thread** (EDT) | Any panel doing slow work freezes the *entire* UI |
| One **heap** | One bundle's memory leak / GC pause stalls *everyone* |
| One **process** | One panel's crash can take down *the whole app* |

With many developers contributing panels, these failures are **rampant** — and a freeze
caused by Team A is felt by Team B's users. The goal became clear: **isolate teams'
panels so one team's problem can't sink the ship.**

---

## Part 2 — The journey (what we tried, and why we ruled it out)

The first instinct was: *"move a panel from one JVM to another."* That turned out to be
the wrong framing, and chasing it taught us the real constraints.

**Step 1 — "Can we just send a `JPanel` over the wire?"** No. A Swing component is welded
to one JVM's event thread and native peers. Serialize it and you get a dead snapshot with
no live behavior — and host-side updates can never reach it. *A live UI object cannot
cross a process boundary.*

**Step 2 — "Mirror the panel as an image + forward clicks back."** Technically possible
(it's how remote-desktop tools work): keep the real panel headless on the host, stream its
pixels, and send mouse/keyboard events back. It even preserves existing click handlers with
no panel rewrite. But it's **expensive to build and operate** (latency, bandwidth, and
broken popups/dialogs/drag-drop), and the user ruled it out for this stage.

**Step 3 — "Re-implement each panel as remote data + local rendering."** Clean in theory,
but it means **rewriting every one of hundreds of panels** into a data-model + renderer.
**Too invasive, too late.**

**Step 4 — the reframe.** Stepping back: the goal was never "transfer panels." It was
**fault and performance isolation**. Panel-transfer was just an assumed *means*. Once we
asked *"what are you actually trying to achieve?"*, two answers unlocked everything:

- The freezes come from **all** of: backend blocking, the panels' own rendering/compute,
  GC pressure, and crashes — so the isolation must be at the **process** level (separate
  EDTs *and* heaps *and* crash domains), not just the service level.
- **Separate windows are acceptable.** This was the key: the *only* thing that forced the
  expensive image/embedding approaches was insisting on a single combined window. Drop that,
  and the whole problem becomes easy.

---

## Part 3 — The solution

**Desktop "micro-frontends": one JVM per panel-group, each in its own window, coordinated
only by shared services.**

```
                  ┌──────────── Shell / Launcher ────────────┐
                  │  starts & supervises apps · global menu   │
                  └───────┬───────────────┬───────────────┬───┘
              ┌───────────▼──┐   ┌─────────▼────┐   ┌──────▼─────────────┐
              │ App A (JVM)  │   │ App B (JVM)  │   │ Core/Registry (JVM)│
              │ own window   │   │ own window   │   │ shared state +     │
              │ own EDT/heap │   │ own EDT/heap │   │ discovery          │
              └──────┬───────┘   └──────┬───────┘   └─────────┬──────────┘
                     └────── ECF shared remote services ──────┘
                  (the ONLY coupling: *.api bundles = interfaces + DTOs)
```

Why this fixes every freeze cause **for free**:

| Freeze cause | Fixed by |
|---|---|
| Panels' own rendering/compute on the EDT | **Separate EDT per app** |
| Panels blocking on backend/IO | App-local threads; remote calls made off-EDT |
| Memory / GC pressure | **Separate heaps** |
| Crashes / exceptions | **Separate processes** (+ supervisor restart) |

The panels **keep their existing code** and run for real in their own window. The only
change: cross-team interactions that used to be in-JVM method calls become **ECF service
calls** — which this project already does well.

**Where the cost actually is** (honest): not UI tech, but **untangling the implicit
in-JVM coupling into explicit service contracts**, and promoting shared global state
(session, selection, config) into a Core service first. That work is **incremental** —
carve out the worst-offending panel-group first, leave the rest as the monolith, and peel
off more over time. The monolith shrinks; value lands immediately.

---

## Part 4 — The proof (we built it)

To de-risk before committing teams, we built a **runnable two-app spike**:

- **App-1 (master)** — its own JVM and window: a list of catalog items; owns the data and
  the current selection; **exports** an `ICatalogService` over ECF.
- **App-2 (detail)** — its own JVM and window: **consumes** that service and shows the
  details of whatever is selected in App-1.

The *only* thing shared between them is a tiny contract bundle (one interface + one
serializable DTO). No panel remoting.

**What we verified, with running code:**

- ✅ **Cross-JVM coordination** — selecting in App-1 updates App-2 across processes
  (`Detail now showing remote selection: Hex Bolt M8`).
- ✅ **Crash isolation** — killing App-1 left App-2 running and gracefully showing
  "unavailable." One app's crash did **not** take down the other.
- ✅ **Freeze isolation** — freezing one app's UI thread leaves the other responsive
  (separate EDTs); App-1's frozen *window* doesn't block App-2 because its service answers
  off the event thread.

**The one gap the spike surfaced (and why that's valuable):**

- ⚠️ After App-1 **restarted**, App-2 did **not** automatically reconnect. The spike used a
  *static* endpoint file, which ECF reads **once at startup**. So discovery was
  "once," not "continuous." For a system where apps crash and **restart and rejoin**, that's
  the missing piece — exactly the kind of thing a spike is meant to catch *before* you build
  on it.

---

## Part 5 — What's next: closing the auto-rejoin gap

The fix is **dynamic service discovery**: apps **advertise** their services and **discover**
peers continuously, so a restarted app is re-found and re-connected automatically (instead of
discovery happening once at startup).

We attempted the obvious route — ECF's **JmDNS/Zeroconf discovery provider** — and learned it
is not a drop-in: adding the provider bundle is necessary but not sufficient. ECF did not
auto-instantiate or connect a discovery container in a local two-JVM test, which means it needs
**explicit discovery-container wiring**, and mDNS multicast between two processes on a single
developer machine (macOS loopback) is itself unreliable. We rolled the spike back to its
verified static-EDEF state rather than ship unverified discovery.

So the auto-rejoin gap has **two viable closes**, to be chosen per environment:

1. **Discovery provider (JmDNS/Zeroconf or ZooKeeper), verified on a real network** — the
   "proper" answer for elastic, many-app topologies. Needs the explicit discovery-container
   wiring and a true (non-loopback) network to validate.
2. **Deterministic programmatic re-import** — a small consumer-side watchdog that re-imports the
   endpoint via ECF's `RemoteServiceAdmin` when the provider returns. Works with the existing
   static endpoint info, needs no multicast, and is fully verifiable locally. A pragmatic bridge
   until a discovery provider is stood up.

After that, the roadmap is the incremental migration itself: stand up the Core (shared state +
discovery + supervisor), carve out the first painful panel-group, and iterate.

---

## Key takeaways

1. **Question the means, not just the requirement.** "Transfer a panel" was an assumption;
   the real goal was *isolation*. The right question ("what are you actually trying to
   achieve?") dissolved the hardest part of the problem.
2. **One constraint was doing all the damage.** Insisting on a single combined window was the
   only thing forcing expensive solutions. Relaxing it made the problem cheap.
3. **Isolation is free once you split processes; coordination is cheap with services.** The
   architecture leans on what OSGi/ECF already do well, not on novel UI plumbing.
4. **Build a spike to find the gap.** A few hundred lines of running code proved the thesis
   *and* surfaced the discovery requirement — far cheaper than discovering it mid-migration.
5. **Migrate incrementally.** Carve out the worst offender first; let the monolith shrink.
   No big-bang rewrite, value on day one.
