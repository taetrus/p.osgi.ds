# Todo: Combined-UI docking (approach C)

Goal: two isolated JVMs (master, detail) whose borderless windows dock edge-to-edge
to LOOK like one combined UI showing both panels as real widgets. macOS + Windows.

## API (spike.api)
- [ ] DockBounds DTO (x,y,width,height) — Serializable
- [ ] DockState DTO (bounds, closing, selected) — Serializable
- [ ] ICatalogService: + setHostBounds, setClosing, requestShutdown, getDockState

## Master (anchor)
- [ ] CatalogServiceImpl: hold hostBounds + closing; implement new methods
- [ ] MasterApp: undecorated frame, draggable header w/ top-left ✕, push bounds on move/resize

## Detail (follower)
- [ ] DetailApp: undecorated frame, matching header, poll getDockState ~120ms,
      dock to master's right edge + match height, exit on closing flag, survive crash (unavailable)

## Verify
- [ ] mvn clean verify
- [ ] run master + detail; screencapture screen → confirm they read as one window
- [ ] drag master header → detail follows; ✕ closes both; crash master → detail survives (no dock follow, shows unavailable)

## Review

Built and verified. Two isolated JVMs whose borderless windows dock edge-to-edge into one
combined UI showing both real panels.

- API: DockBounds + DockState DTOs; ICatalogService gained setHostBounds / setClosing /
  requestShutdown / getDockState.
- Master = anchor: undecorated frame, draggable header, top-left ● close (calls requestShutdown),
  publishes live screen bounds on move/resize.
- Detail = follower: undecorated frame, matching header, polls getDockState ~120ms, docks to the
  anchor's right edge matched in height, shows the remote selection, exits on the closing flag,
  survives a crash (shows "host unavailable", stops tracking).

Verified (clean build, two JVMs, screen capture):
- The two windows render as ONE combined UI (master list left + detail right, shared dark theme,
  flush seam). Selection set in App-1 shows in App-2 ("Hex Bolt M8") over ECF.
- Crash isolation in docked mode: killed App-1 → App-2 stayed alive, logged "Lost connection",
  degraded gracefully with no dock exceptions.

Known limits (accepted): same-display only (screen coords must align); follower rubber-bands
slightly while dragging (120ms poll); visible header seam (two windows, not one).

## Update — multi-panel showcase (each panel badged with its app)
- Master (App-1, teal "APP-1 · MASTER"): CATALOG (list) · SUMMARY (count+stock) · ACTIVITY
  (live heartbeat, proves App-1 process alive) · CONTROLS (Freeze/Crash).
- Detail (App-2, amber "APP-2 · DETAIL"): SELECTED ITEM · INSPECTOR (item id, name/desc length,
  App-2 uptime ticking) · CONNECTION (dock status) · CONTROLS.
- Reusable badged() panel helper per app (accent bar + "APP-n · ROLE" tag + panel name),
  color-coded by process so provenance is obvious in the combined window.
- Verified via screen capture: 4 panels per app, headers on top, both live clocks running
  independently, selection flowing master→detail over ECF.
- Bugs hit & fixed: (1) duplicate `Component` import (java.awt vs OSGi annotation) → use
  JComponent.LEFT_ALIGNMENT; (2) header added with BorderLayout.NORTH onto a BoxLayout content
  pane (ignored → rendered at bottom) → wrap in a BorderLayout root (header NORTH, body CENTER).
