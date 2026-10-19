# Two-App Isolation Spike: Results

This spike is a runnable proof of concept for the design in [monolith-to-multi-jvm-migration.md](monolith-to-multi-jvm-migration.md).
Two panel groups run in **separate JVMs and separate windows**. A shared ECF remote service is the **only** link between them. The spike does **not** send panels between JVMs.
The spike tests the architecture and the recovery behavior before teams start the migration.

## Bundles

| Bundle | Role |
|--------|------|
| `com.kk.pde.ds.spike.api` | The only coupling between the two apps. It holds `ICatalogService` and these serializable value objects: `CatalogItem`, `DockLayout`, `DockState`, `DockBounds`, `AnchorState`. |
| `com.kk.pde.ds.spike.master` | **App-1** (own JVM). It keeps the catalog data and the selection. It exports `ICatalogService` over ECF on `ecftcp://localhost:3289/catalog`. Its frames show the CATALOG, SUMMARY, ACTIVITY and CONTROLS panels. |
| `com.kk.pde.ds.spike.detail` | **App-2** (own JVM). It gets the service through a static EDEF file. It polls the master every 120 ms on a background thread and shows the selection. Its frames show the SELECTED ITEM, INSPECTOR, CONNECTION and CONTROLS panels. |

`ICatalogService` has these methods:

- Catalog and selection: `listItems`, `getItem`, `getSelectedId`, `setSelectedId`, `getSelectedItem`.
- Combined UI, which the master writes: `setLayout`, `setAnchor`, `setClosing`.
- `requestShutdown`. The close button (●) in a master frame calls it. The master sets the closing flag and exits. The detail reads the flag and exits too.
- `getDockState`. The detail reads the layout, the closing flag, the selection and the anchor state in one remote call.

## Frames

Each app opens N borderless frames in one shared grid. The default is N = 2.

- Set N on the master with the `SPIKE_FRAMES` environment variable or with `-Dspike.frames=N`. The environment variable has priority.
- The master calculates a `DockLayout` and publishes it with `setLayout`. The detail reads N from that layout. Do not set N on the detail.
- Master frames use the even grid slots and detail frames use the odd slots.
- The first master frame (M-1) is the anchor. You can drag or minimize only the anchor. The other frames of the two apps move with it.

Each app has a CONTROLS panel with the **Freeze 5s** and **Crash** buttons. Use them to show the isolation.

For the design of the grid and the anchor, see [multi-jvm-isolation-journey.md](multi-jvm-isolation-journey.md), parts 5 to 7.

## Run it

1. Build the products:

   ```bash
   mvn clean verify
   ```

2. In terminal 1, start the master. Start it **before** the detail, because the detail connects to it at startup:

   ```bash
   ./distribution/scripts/run-spike-master.sh
   ```

   To open 3 frames for each app, use `SPIKE_FRAMES=3 ./distribution/scripts/run-spike-master.sh`.

3. In terminal 2, start the detail:

   ```bash
   ./distribution/scripts/run-spike-detail.sh
   ```

4. Select an item in the App-1 CATALOG panel. Make sure that the App-2 SELECTED ITEM panel shows the same item.
5. Push **Freeze 5s** or **Crash** in one app. Make sure that the other app continues to operate.

## Results

- **Cross-JVM coordination: pass.** The master sets the selection in its own JVM. App-2 receives it over the network and logs `Detail now showing remote selection: Hex Bolt M8`. The selection stays in App-1. App-2 reads it through the ECF proxy.
- **Crash isolation: pass.** We killed the App-1 process, and App-2 continued to run. App-2 logged `Lost connection to master ICatalogService` through the DS dynamic unbind and showed that the host is unavailable. A crash in one app does not stop the other app.
- **EDT isolation: pass (manual test).** **Freeze 5s** freezes only the windows of that app. The windows of the other app continue to operate. A freeze in the App-1 UI does not block App-2, because the service implementation does not use the event dispatch thread (EDT) to answer.

## Key finding: a restarted master needs a discovery provider

After we restarted App-1, **App-2 did not connect again automatically.** ECF's `EndpointDescriptionLocator` reads the static EDEF file **one time at startup**. It does not import the endpoint again when the provider comes back. With a static EDEF file, discovery occurs one time only. It is not continuous.

The current `DetailApp` code has no import-again logic. Thus this finding is still applicable.

**Effect on the real migration:** many apps that restart independently need one of these:

- a **discovery provider** (ECF JmDNS/Zeroconf or ZooKeeper)
- a central registry app
- programmatic import of the endpoint again after a disconnect

The migration design names the same options. A static EDEF file is sufficient for a fixed demo with two apps. It is not sufficient for a production system where apps start and stop.

## Notes and limits

- With a static EDEF file, the start order is important: start the master before the detail.
- The spike uses port `:3289`. The greeting demo uses `:3288`. Thus the two demos can run at the same time.
- OSGi `Import-Package` operates for each package separately. The UI bundles import `javax.swing` **and** `javax.swing.event`, `javax.swing.border` and `javax.swing.text`. We found this rule from a `NoClassDefFoundError` on `ListSelectionEvent`.
- The tiled grid aligns only when the two apps use one physical screen, because the frames use screen coordinates.
- The next steps are recovery after a restart and a discovery provider.
