# Multi-frame tiled grid for the isolation spike

**Date:** 2026-06-05
**Modules:** `com.kk.pde.ds.spike.api`, `com.kk.pde.ds.spike.master`, `com.kk.pde.ds.spike.detail`

## Goal

Turn each app (master JVM, detail JVM) from **one window** into **N windows**, tiled
into a single shared grid that interleaves master and detail frames (M, D, M, D…,
wrapping to new rows), non-overlapping, each frame carrying a distinct subset of the
app's panels.

## Decisions (from brainstorming)

| Question | Decision |
|----------|----------|
| Frames per app | Configurable **N** via `-Dspike.frames` (default 2) |
| Arrangement | **Mixed grid** — interleaved fill order, wraps to new rows |
| Positioning | **Fixed at startup** (no live docking/following) |
| Frame content | **Different content per frame** (split panels across frames) |
| Cross-JVM coordination | Detail **reads layout from `ICatalogService`** |
| Panel split rule | **Contiguous chunking** (frame f gets a contiguous slice) |
| `setHostBounds` | **Removed** (single-window docking replaced by the grid) |

## Layout model — one global slot grid

A global slot index `i` maps to a grid cell:

```
col = i % columns
row = i / columns
x   = originX + col * tileWidth
y   = originY + row * tileHeight
```

- Master frame `k` → global slot `2k`   (even)
- Detail frame `k` → global slot `2k+1` (odd)

Fill order interleaves M0, D0, M1, D1, … wrapping at `columns`. The master computes
the layout (it starts first, measures the screen) and publishes it; the detail reads
it and computes its own odd slots. Identical formula on both sides ⇒ no overlap,
no live messaging.

## API changes (`com.kk.pde.ds.spike.api`)

**New `DockLayout`** (Serializable):

```
framesPerApp, tileWidth, tileHeight, originX, originY, columns
DockBounds slotBounds(int globalSlot)   // the formula above, shared by both apps
static int masterSlot(int frameIndex)   // 2*k
static int detailSlot(int frameIndex)   // 2*k + 1
static int[] distribute(int panelCount, int frames)  // panel i -> frame index (contiguous chunk)
```

**`ICatalogService`**: add `void setLayout(DockLayout)`; remove `setHostBounds(DockBounds)`.

**`DockState`**: replace `hostBounds` field with `DockLayout layout`. `closing` +
`selected` unchanged. Detail already polls `getDockState()`, so layout rides along.

## Master changes (`MasterApp`)

- `@Activate`: read `N` from `-Dspike.frames` (default 2), measure screen, compute
  `DockLayout` (columns = screen width / tileWidth), call `catalog.setLayout(layout)`.
- Build **N frames**; frame `k` at `layout.slotBounds(masterSlot(k))`, titled `M-(k+1)`,
  teal "APP-1" badge.
- Distribute the 4 panels (CATALOG, SUMMARY, ACTIVITY, CONTROLS) by contiguous chunking.
- Drop the `componentListener` bounds-publishing. Drag-to-move stays (cosmetic).

## Detail changes (`DetailApp`)

- `@Activate` starts the poll but **does not build frames yet**.
- First poll where `getDockState().getLayout() != null` → build **N detail frames** on
  the EDT at `slotBounds(detailSlot(k))`, amber "APP-2" badge, titled `D-(k+1)`; set a
  `built` flag. Detail trusts `layout.framesPerApp` (master is the source of truth).
- Distribute the 4 detail panels (SELECTED ITEM, INSPECTOR, CONNECTION, CONTROLS) by chunking.
- Subsequent polls update live labels + honor `closing`. Positioning is one-time.

## Edge cases

- Detail starts before master → no window until layout arrives (master-first is documented).
- `N` > panel count (4) → extra frames get a labeled placeholder panel.
- Grid taller than screen (large N) → frames place by formula; may run off-screen bottom.
  Acceptable for a spike; computed columns/rows logged.

## Testing / verification

- Pure functions `DockLayout.slotBounds`, `masterSlot/detailSlot`, `distribute` — covered
  by startup assertions logged on activation (no test module exists for these spike bundles).
- Visual: `mvn clean verify`, then `run-spike-master.sh -Dspike.frames=2` +
  `run-spike-detail.sh -Dspike.frames=2`; observe 4 tiled non-overlapping frames with
  distinct panels. Repeat with `=3` for wrapping.

## Files touched

- `api`: **new** `DockLayout.java`; edit `ICatalogService.java`, `DockState.java`
- `master`: edit `MasterApp.java`, `CatalogServiceImpl.java`
- `detail`: edit `DetailApp.java`
- `scripts`: comment updates in `run-spike-*.sh/.bat`
