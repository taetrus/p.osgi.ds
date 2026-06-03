package com.kk.pde.ds.spike.api;

import java.io.Serializable;

/**
 * One-shot snapshot the detail (follower) polls each tick: where to dock, whether
 * the combined app is closing, and the current selection to display. Bundling them
 * into a single call keeps the follower in sync with one remote round-trip per tick.
 */
public final class DockState implements Serializable {

	private static final long serialVersionUID = 1L;

	private final DockBounds hostBounds;   // null until the master window is shown
	private final boolean closing;         // true once the combined app is shutting down
	private final CatalogItem selected;    // null if nothing selected

	public DockState(DockBounds hostBounds, boolean closing, CatalogItem selected) {
		this.hostBounds = hostBounds;
		this.closing = closing;
		this.selected = selected;
	}

	public DockBounds getHostBounds() { return hostBounds; }
	public boolean isClosing() { return closing; }
	public CatalogItem getSelected() { return selected; }
}
