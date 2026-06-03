package com.kk.pde.ds.spike.master;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.spike.api.CatalogItem;
import com.kk.pde.ds.spike.api.DockBounds;
import com.kk.pde.ds.spike.api.DockState;
import com.kk.pde.ds.spike.api.ICatalogService;

/**
 * Master-side implementation of {@link ICatalogService}, exported as an ECF
 * remote service so the detail app (a separate JVM) can read the catalog and the
 * current selection.
 *
 * <p>
 * The export is purely declarative (the {@code service.exported.*} properties);
 * there is no ECF code here. Note the dedicated endpoint
 * {@code ecftcp://localhost:3289/catalog} — a different port from the greeting
 * showcase (:3288) so both demos can run side by side.
 * </p>
 *
 * <p>
 * Within the master JVM, {@code MasterApp} binds this same service instance via
 * {@code @Reference} (DS injects the local registration, not a remote proxy) and
 * drives {@link #setSelectedId(String)} when the user clicks. The detail app reads
 * the selection remotely. State lives here, in the master.
 * </p>
 */
@Component(property = {
		"service.exported.interfaces=*",
		"service.exported.configs=ecf.generic.server",
		"ecf.exported.containerfactoryargs=ecftcp://localhost:3289/catalog" })
public class CatalogServiceImpl implements ICatalogService {

	private static final Logger log = LoggerFactory.getLogger(CatalogServiceImpl.class);

	private final List<CatalogItem> items = new ArrayList<>();
	private volatile String selectedId;
	private volatile DockBounds hostBounds;
	private volatile boolean closing;

	@Activate
	public void start() {
		items.add(new CatalogItem("p1", "Hex Bolt M8",   "Zinc-plated steel hex bolt, 8mm x 40mm",        1240));
		items.add(new CatalogItem("p2", "Ball Bearing",  "Sealed deep-groove ball bearing, 22mm OD",      318));
		items.add(new CatalogItem("p3", "O-Ring 12mm",   "Nitrile rubber O-ring, 12mm ID, pack of 50",    27));
		items.add(new CatalogItem("p4", "Copper Wire",   "1.5mm tinned copper wire, 100m spool",          64));
		items.add(new CatalogItem("p5", "Toggle Switch", "SPDT panel-mount toggle switch, 6A",            150));
		log.info("CatalogServiceImpl activated — exporting ICatalogService on ecftcp://localhost:3289/catalog ({} items)", items.size());
	}

	@Override
	public List<CatalogItem> listItems() {
		return new ArrayList<>(items);
	}

	@Override
	public CatalogItem getItem(String id) {
		for (CatalogItem it : items) {
			if (it.getId().equals(id)) {
				return it;
			}
		}
		return null;
	}

	@Override
	public String getSelectedId() {
		return selectedId;
	}

	@Override
	public void setSelectedId(String id) {
		this.selectedId = id;
		log.info("Selection set to '{}' (detail app will pick this up on its next poll)", id);
	}

	@Override
	public CatalogItem getSelectedItem() {
		return selectedId == null ? null : getItem(selectedId);
	}

	@Override
	public void setHostBounds(DockBounds bounds) {
		this.hostBounds = bounds;
	}

	@Override
	public void setClosing(boolean closing) {
		this.closing = closing;
	}

	@Override
	public void requestShutdown() {
		log.info("Shutdown requested (combined-app close) — master exiting, detail will follow");
		this.closing = true;
		// Give the detail a moment to observe the closing flag, then exit the JVM.
		Thread t = new Thread(() -> {
			try { Thread.sleep(450); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
			System.exit(0);
		}, "master-shutdown");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public DockState getDockState() {
		return new DockState(hostBounds, closing, getSelectedItem());
	}

	List<CatalogItem> itemsSnapshot() {
		return Collections.unmodifiableList(new ArrayList<>(items));
	}
}
