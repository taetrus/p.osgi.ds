# Two-App Isolation Spike — Results

A runnable proof-of-concept for the migration in `monolith-to-multi-jvm-migration.md`:
two panel-groups in **separate JVMs / separate windows**, coordinated **only** by a shared
ECF remote service — **no panel remoting**. It validates the architecture and de-risks the
recovery story before committing teams.

## What it is

| Bundle | Role |
|--------|------|
| `com.kk.pde.ds.spike.api` | `ICatalogService` + `CatalogItem` (Serializable DTO) — the only cross-app coupling |
| `com.kk.pde.ds.spike.master` | **App-1** (own JVM/window): `JList` of items; owns the data + selection; exports `ICatalogService` over ECF on `ecftcp://localhost:3289/catalog` |
| `com.kk.pde.ds.spike.detail` | **App-2** (own JVM/window): consumes the service via static EDEF; polls the selection off-EDT and displays it |

Each app has Freeze/Crash buttons to demonstrate isolation interactively.

## Run it

```bash
mvn clean verify
# Terminal 1 — start the master FIRST (it must be up before the detail connects)
./distribution/scripts/run-spike-master.sh
# Terminal 2
./distribution/scripts/run-spike-detail.sh
```
Select an item in App-1; App-2's detail panel updates across JVMs. Use the Freeze/Crash
buttons to see isolation.

## Verified results

- **Cross-JVM coordination ✅** — master sets the selection locally; App-2 receives it over the
  wire: `Detail now showing remote selection: Hex Bolt M8`. The selection state lives in App-1;
  App-2 reads it through the ECF proxy.
- **Crash isolation ✅** — killing App-1 left App-2 running; App-2 degraded gracefully
  (`Lost connection to master ICatalogService`, via DS dynamic unbind). One app's crash does not
  take down the other.
- **EDT isolation ✅ (manual)** — "Freeze me 5s" freezes only that app's window; the other stays
  responsive. App-1's UI freeze doesn't block App-2 because the service impl answers off the EDT.

## Key finding: restart-rejoin needs a discovery provider

After restarting App-1, **App-2 did not auto-reconnect.** ECF's `EndpointDescriptionLocator`
reads the static EDEF **once at startup** and does not re-import when a provider returns. So with
static EDEF, discovery is "once," not "continuous."

**Implication for the real migration:** running many independently-restarting apps requires a
**discovery provider** (ECF JmDNS/Zeroconf or ZooKeeper) or a central registry app, or
programmatic re-import on disconnect — exactly as called out in the migration design. Static EDEF
is fine for a fixed two-app demo, not for production elasticity.

## Notes / limitations

- Start order matters with static EDEF: master before detail.
- Endpoint port `:3289` (distinct from the greeting demo's `:3288`) so both demos coexist.
- OSGi `Import-Package` is per-package — UI bundles import `javax.swing` **and**
  `javax.swing.event/border/text` (a `NoClassDefFoundError` on `ListSelectionEvent` caught this).
- Recovery-on-restart and a discovery provider are the natural next increments.
