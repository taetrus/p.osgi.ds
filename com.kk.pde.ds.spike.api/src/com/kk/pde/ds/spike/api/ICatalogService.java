package com.kk.pde.ds.spike.api;

import java.util.List;

/**
 * Shared cross-app contract for the two-JVM isolation spike.
 *
 * <p>
 * This is the <em>only</em> compile-time coupling allowed between the master app
 * (App-1, which implements and exports this service) and the detail app (App-2,
 * which consumes it via an ECF remote proxy). It carries both the catalog
 * <em>data</em> and the shared <em>selection</em> state.
 * </p>
 *
 * <p>
 * In the full migration architecture the selection state would live in the Core
 * app; here the master owns it. The interface is identical either way — where the
 * service runs is a deployment decision, not an API change.
 * </p>
 *
 * <p>
 * All arguments and return types are serializable so the service can be invoked
 * across JVMs over ECF. Consumers must call these methods <strong>off the EDT</strong>
 * (they are remote calls).
 * </p>
 */
public interface ICatalogService {

	/** @return all catalog items (the master's data set). */
	List<CatalogItem> listItems();

	/** @return the item with the given id, or {@code null} if unknown. */
	CatalogItem getItem(String id);

	/** @return the id of the currently selected item, or {@code null} if none. */
	String getSelectedId();

	/** Sets the currently selected item id (called by the master when the user selects). */
	void setSelectedId(String id);

	/** Convenience: @return the currently selected item, or {@code null} if none. */
	CatalogItem getSelectedItem();

	// --- Combined-UI docking (master writes, detail reads) ---

	/** Master publishes its window's current screen bounds so the detail can dock to it. */
	void setHostBounds(DockBounds bounds);

	/** Master signals a clean shutdown so the detail closes with it (vs. a crash). */
	void setClosing(boolean closing);

	/**
	 * Asks the master (anchor) to close the whole combined app — invoked remotely by
	 * the detail's close control. The master sets {@link #setClosing(boolean)} and exits.
	 */
	void requestShutdown();

	/** One-shot snapshot the detail polls: dock bounds + closing flag + current selection. */
	DockState getDockState();
}
