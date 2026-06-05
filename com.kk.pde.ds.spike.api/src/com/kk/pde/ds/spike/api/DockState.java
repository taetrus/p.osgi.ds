package com.kk.pde.ds.spike.api;

import java.io.Serializable;

/**
 * One-shot snapshot the detail (follower) polls each tick: the shared grid layout (so
 * it can tile its own frames), whether the combined app is closing, and the current
 * selection to display. Bundling them into a single call keeps the follower in sync
 * with one remote round-trip per tick.
 */
public final class DockState implements Serializable {

	private static final long serialVersionUID = 2L;

	private final DockLayout layout;       // null until the master has published the grid
	private final boolean closing;         // true once the combined app is shutting down
	private final CatalogItem selected;    // null if nothing selected

	public DockState(DockLayout layout, boolean closing, CatalogItem selected) {
		this.layout = layout;
		this.closing = closing;
		this.selected = selected;
	}

	public DockLayout getLayout() { return layout; }
	public boolean isClosing() { return closing; }
	public CatalogItem getSelected() { return selected; }
}
