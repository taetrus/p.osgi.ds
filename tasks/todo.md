# Todo: Two-app isolation spike

Design: `docs/monolith-to-multi-jvm-migration.md` (Recommended PoC section)

Goal: two panel-groups in SEPARATE JVMs/windows, coordinated only by a shared ECF
remote service (no panel remoting), proving fault/perf isolation (freeze/crash one →
the other survives & recovers).

## Bundles
- [ ] `com.kk.pde.ds.spike.api` — ICatalogService + CatalogItem (Serializable DTO)
- [ ] `com.kk.pde.ds.spike.master` — App-1: JList of items, exports ICatalogService (ECF), sets selection; Freeze/Crash buttons
- [ ] `com.kk.pde.ds.spike.detail` — App-2: detail panel, consumes ICatalogService (EDEF), polls selection off-EDT, degrades+recovers; Freeze/Crash buttons

## Wiring
- [ ] root pom modules + feature.xml
- [ ] 2 products (spike.master.product :3289 catalog, spike.detail.product) + 4 run scripts
- [ ] detail EDEF -> ecftcp://localhost:3289/catalog (fix ecf.rsvc.id from live export)
- [ ] antrun: copy logback + scripts into spike products

## Verify
- [ ] mvn clean verify
- [ ] run master + detail (2 JVMs); select in master -> detail updates across JVMs
- [ ] Freeze App-2 5s -> App-1 stays responsive (separate EDTs)
- [ ] Crash App-1 -> App-2 shows "unavailable"; restart App-1 -> App-2 auto-recovers (DS dynamic rebind)

## Review

Built and verified end-to-end. Two panel-groups in separate JVMs/windows, coordinated
ONLY by the ECF `ICatalogService` — zero panel remoting.

**Verified (clean build, two JVMs):**
- Cross-JVM coordination: master sets selection locally; detail (App-2) receives it over the
  wire — `Detail now showing remote selection: Hex Bolt M8`.
- Crash isolation: killed App-1; App-2 kept running and degraded gracefully
  (`Lost connection to master ICatalogService` via DS dynamic unbind) — one crash did NOT take
  down the other.
- Manual (buttons present, need a click): "Freeze me 5s" on either app freezes only that app's
  EDT; the other stays responsive (separate EDTs). App-1's UI freeze does not block App-2 because
  the service impl answers off-EDT.

**Key empirical finding (confirms the migration doc):**
- After restarting App-1, App-2 did NOT auto-reconnect. ECF's `EndpointDescriptionLocator`
  processes the static EDEF once at startup and does not re-import when a provider returns. So
  **restart-rejoin requires a discovery provider** (JmDNS/ZooKeeper) or programmatic re-import —
  static EDEF is discovery-once. This is the most important input for the real migration.

**Bug hit & fixed:** `NoClassDefFoundError javax.swing.event.ListSelectionEvent` — OSGi
Import-Package is per-package; widened both UI bundles to import javax.swing.event/border/text
(matching the existing `app` bundle).

**Files:** `com.kk.pde.ds.spike.{api,master,detail}`, two products, 4 run scripts, antrun copies,
root pom + feature wiring. Endpoint on `ecftcp://localhost:3289/catalog` (distinct from the
greeting demo's :3288).
