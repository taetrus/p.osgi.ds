# From a Freezing Monolith to Isolated Apps

*This document tells how we took a single-JVM Swing/OSGi application that froze for all users. We found a path to split it into independent processes, each with its own failure domain. We did not rewrite the panels.*

---

## Summary

A large OSGi application runs **hundreds of bundles in one JVM**. Many teams contribute Swing panels, and the application combines them into a **single window**. All panels share one event thread, one heap and one process. Thus **an error from one team freezes or stops the full application for all users**.

We examined several ways to split the application across processes. We rejected the expensive ones. We selected an incremental architecture with a low cost. It has **one JVM for each panel group, each in its own window**. **Shared ECF remote services are the only link.**

We then **built a working proof of concept with two apps**. It shows the isolation with real code. It also found the one missing piece of infrastructure: dynamic service discovery.

After that, we made the two apps look like one combined UI. First, the detail window attached to the master window. Then each app opened N frames in one shared grid. Then the first master frame became an anchor that moves all frames of the two apps.

**Result:** a tested migration path with a low cost, one team at a time, and running code as proof.

---

## Part 1: The problem

The application is a typical "big ball of mud" desktop client:

- **Hundreds of OSGi bundles** in **one JVM**.
- **Many Swing panels** from **many teams**, all in **one `JFrame`**.

All panels share three resources. Each resource is a single point of failure:

| Shared resource | Failure that it causes |
|---|---|
| One **Event Dispatch Thread** (EDT) | A panel that does slow work freezes the *full* UI. |
| One **heap** | A memory leak or a GC pause in one bundle stops *all* panels. |
| One **process** | A crash in one panel can stop *the full application*. |

Many developers contribute panels, so these failures occur **frequently**. A freeze that Team A causes also affects the users of Team B. The goal became clear: **isolate the panels of each team, so that a problem in one team cannot stop the full application.**

---

## Part 2: What we tried, and why we rejected it

The first idea was: *"move a panel from one JVM to another JVM."* This was the wrong question. But the attempts showed us the real constraints.

**Step 1: "Can we send a `JPanel` over the network?"** No. A Swing component needs the event thread and the native peers of one JVM. If you serialize it, you get a static copy with no live behavior. Updates from the host can never get to it. *A live UI object cannot go across a process boundary.*

**Step 2: "Show the panel as an image and send the clicks back."** This is technically possible. Remote-desktop tools operate this way. The real panel stays headless on the host. The host sends its pixels, and the client sends the mouse and keyboard events back. This keeps the existing click handlers, and the panels need no rewrite. But it is **expensive to build and to operate**. Latency and bandwidth increase, and popups, dialogs and drag-and-drop do not operate correctly. The user rejected it for this stage.

**Step 3: "Write each panel again as remote data plus local rendering."** This is clean in theory. But it means that we **write each of hundreds of panels again** as a data model plus a renderer. **This change is too large, and it is too late for it.**

**Step 4: a new view of the problem.** The goal was never "move panels". The goal was **isolation of failures and performance**. Panel transfer was only an assumed *method*. We asked, *"What do you actually want to get?"* Two answers solved the problem:

- The freezes come from **all** of these causes: backend calls that block, rendering and calculation in the panels, GC pressure and crashes. Thus the isolation must be at the **process** level, with separate EDTs, heaps and crash domains. Isolation at the service level only is not sufficient.
- **Separate windows are acceptable.** This was the key point. Only the requirement for one combined window made the expensive image and embedding methods necessary. Without that requirement, the problem becomes easy.

---

## Part 3: The solution

**Desktop "micro-frontends": one JVM for each panel group, each in its own window, with shared services as the only link.**

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

This design removes each cause of a freeze **with no additional work**:

| Cause of the freeze | Solution |
|---|---|
| Rendering and calculation in the panels on the EDT | **A separate EDT for each app** |
| Panels that block on backend calls or I/O | Threads in each app. Remote calls do not use the EDT. |
| Memory and GC pressure | **Separate heaps** |
| Crashes and exceptions | **Separate processes**, plus a supervisor that starts a crashed app again |

The panels **keep their existing code**. Each panel runs in its own window. Only one thing changes: interactions between teams that were method calls in one JVM become **ECF service calls**. This project already uses ECF service calls.

**Where the real cost is:** the cost is not in the UI technology. The cost is in two tasks:

1. Change the implicit coupling inside the JVM into explicit service contracts.
2. Move the shared global state (session, selection, configuration) into a Core service first.

This work is **incremental**. First, move the panel group that causes the most problems. Keep the other panel groups in the monolith. Move more panel groups over time. The monolith becomes smaller, and each step gives value immediately.

---

## Part 4: The proof (we built it)

To decrease the risk before teams start, we built a **runnable spike with two apps**:

- **App-1 (master)** has its own JVM and window. It shows a list of catalog items. It keeps the data and the current selection. It **exports** an `ICatalogService` over ECF.
- **App-2 (detail)** has its own JVM and window. It **uses** that service and shows the details of the item that the user selects in App-1.

The two apps share only a small contract bundle. At this stage, the bundle had one interface and one serializable DTO. The spike sends no panels between JVMs.

**What we tested with running code:**

- ✅ **Cross-JVM coordination.** A selection in App-1 updates App-2 across processes (`Detail now showing remote selection: Hex Bolt M8`).
- ✅ **Crash isolation.** We killed App-1, and App-2 continued to run and showed "unavailable". The crash of one app did **not** stop the other app.
- ✅ **Freeze isolation.** Each app has its own EDT. Thus a freeze of the UI thread of one app does not freeze the other app. The frozen App-1 *window* does not block App-2, because the App-1 service does not use the event thread to answer.

**The one gap that the spike found:**

- ⚠️ After App-1 **restarted**, App-2 did **not** connect again automatically. The spike used a *static* endpoint file, and ECF reads that file **one time at startup**. Thus discovery occurred one time only. It was not continuous. A system where apps crash, **restart and join again** needs this missing piece. A spike must find this type of gap *before* you build on the design.

---

## Part 5: One combined UI from two processes

The next request was: *make the two apps look like one UI.* The user wanted three things:

1. **One window**: one combined UI on the screen.
2. **Real panels**: the real interactive widgets, with no change.
3. **Isolated processes**: a separate EDT, heap and crash domain for each app.

A Swing widget cannot go across processes. Native window embedding could give all three things, but it is not a usable option on macOS. Thus we kept items 2 and 3, and we made a visual copy of item 1 (commit `d6f987e`):

- The master window was the **anchor**. It published its live screen bounds (`DockBounds`) through `ICatalogService`.
- The detail window was the **follower**. It had no border, and it attached itself to the right edge of the anchor with the same height.
- Every panel showed a badge with its owner process: teal "APP-1 · MASTER" and amber "APP-2 · DETAIL".
- The close button (●) closed the two apps. A crash of one app did not stop the other app.

To keep the follower synchronized with one remote call for each poll, `ICatalogService` got `getDockState()`. It returns one `DockState` snapshot.

---

## Part 6: From two windows to a tiled grid

The combined UI with two windows showed the isolation with one window for each app. The next step made this general (commit `bee59ff`). Each app opens **N frames**, and the frames of the two apps fill **one shared grid**.

**Slot calculation.** The grid has one global sequence of slots. `DockLayout` gives the slot numbers:

- `masterSlot(k) = 2k`: master frames use the even slots.
- `detailSlot(k) = 2k + 1`: detail frames use the odd slots.

The fill sequence is M, D, M, D and so on. `slotBounds(slot)` calculates the screen bounds of a slot: `col = slot % columns` and `row = slot / columns`. When a row is full, the next slot goes to a new row.

**One layout, shared parameters.** The master starts first. It calculates the number of columns from the width of the primary screen: `columns = max(1, (screenWidth − 60) / 400)`. It publishes the `DockLayout` one time with `setLayout()`. The detail reads the layout in its first poll and uses the same `slotBounds()` formula. The two apps share parameters, not state. No frame sends its own messages.

**Frame count.** The default is N = 2. Set `SPIKE_FRAMES=N` (or `-Dspike.frames=N`) on the master. The detail reads N from the layout.

**Panels for each frame.** `distribute(panelCount, frames)` divides the panels of each app into balanced, contiguous groups. For example, with 4 panels and 2 frames, frame 0 gets panels 0 and 1, and frame 1 gets panels 2 and 3. With N = 1, each app has one window, as in Part 5.

**Proof from a run.** With `SPIKE_FRAMES=3`, the master frames went to slots 0, 2 and 4. The detail frames went to slots 1, 3 and 5. A new row started after column 4.

---

## Part 7: The anchor moves all frames

With N frames for each app, there is no single "host window" for the follower. Commit `69a9371` added a new anchor:

- The first master frame, **M-1**, is the anchor (badge "M-1 ⚓"). It is the only frame that you can drag or minimize.
- The anchor does not send the position of each window. It publishes one `AnchorState(offsetX, offsetY, minimized)`. The offset is the distance between the anchor and its home slot.
- Each other frame goes to `slotBounds(slot) + offset`. Each frame adds the same offset to its own home position, so the frames keep their relative positions.
- In the master JVM, the other master frames move immediately during the drag. The detail gets the offset in its `getDockState()` poll every 120 ms. It moves its frames only when the `AnchorState` changes.
- Minimize reduces the anchor to its header strip and hides all other frames. Restore shows them again.

This is the same rule as in Part 6: **share parameters, not state.** `DockLayout` shares the static grid. `AnchorState` shares the one value that moves the grid.

The commit record states that `mvn clean verify` passed and that the master started with M-1 as the anchor and with no exceptions.

---

## Part 8: Next step: close the gap for automatic join

The solution is **dynamic service discovery**. Apps **advertise** their services and **discover** other apps continuously. Thus the system finds a restarted app again and connects to it automatically. Discovery does not occur only one time at startup.

We tried the usual method first: ECF's **JmDNS/Zeroconf discovery provider**. We learned that it is not a simple addition. The provider bundle is necessary, but it is not sufficient. In a local test with two JVMs, ECF did not create or connect a discovery container automatically. Thus it needs **explicit wiring of the discovery container**. Also, mDNS multicast between two processes on one developer computer (macOS loopback) is not reliable. We returned the spike to its tested static-EDEF state. We did not ship discovery that we had not tested.

There are **two possible solutions** for the gap. Select one for each environment:

1. **A discovery provider (JmDNS/Zeroconf or ZooKeeper), tested on a real network.** This is the correct solution for flexible topologies with many apps. It needs the explicit wiring of the discovery container and a real (not loopback) network to test it.
2. **A programmatic import of the endpoint again.** A small watchdog in the consumer imports the endpoint again through ECF's `RemoteServiceAdmin` when the provider comes back. It uses the existing static endpoint data. It needs no multicast, and you can test it fully on one computer. It is a practical temporary solution until a discovery provider is available.

After that, the plan is the incremental migration:

1. Create the Core app (shared state, discovery and supervisor).
2. Move the first panel group that causes problems.
3. Repeat for the next panel group.

---

## Lessons

1. **Examine the method, not only the requirement.** "Move a panel" was an assumption. The real goal was *isolation*. The correct question ("What do you actually want to get?") removed the most difficult part of the problem.
2. **One constraint caused all the cost.** Only the requirement for one combined window made the expensive solutions necessary. Without it, the problem became cheap.
3. **Separate processes give isolation, and services give coordination at a low cost.** The architecture uses what OSGi and ECF already do well. It needs no new UI mechanism.
4. **Build a spike to find the gap.** A few hundred lines of running code proved the idea *and* found the discovery requirement. This was much cheaper than finding the gap during the migration.
5. **Migrate incrementally.** Move the worst panel group first, and let the monolith become smaller. Do not rewrite everything at one time. Each step gives value immediately.
6. **Share parameters, not state.** The combined UI shares only a few numbers (`DockLayout` and `AnchorState`). Each app calculates its own window positions from those numbers.
