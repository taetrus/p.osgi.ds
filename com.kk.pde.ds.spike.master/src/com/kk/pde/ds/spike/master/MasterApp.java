package com.kk.pde.ds.spike.master;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.spike.api.CatalogItem;
import com.kk.pde.ds.spike.api.ICatalogService;

/**
 * App-1 ("master") — its own JVM, its own JFrame. Shows a list of catalog items;
 * selecting one updates the shared selection via {@link ICatalogService}, which
 * the detail app (App-2) reads remotely.
 *
 * <p>
 * The {@code @Reference} resolves to the <em>local</em> {@code CatalogServiceImpl}
 * (same JVM) — DS injects the real instance, not a remote proxy. The Freeze/Crash
 * buttons exist to demonstrate isolation: freezing or killing this window must not
 * affect App-2 (and vice-versa).
 * </p>
 */
@Component
public class MasterApp {

	private static final Logger log = LoggerFactory.getLogger(MasterApp.class);

	private volatile ICatalogService catalog;

	@Reference
	public void setCatalog(ICatalogService catalog) {
		this.catalog = catalog;
	}

	public void unsetCatalog(ICatalogService catalog) {
		if (this.catalog == catalog) {
			this.catalog = null;
		}
	}

	@Activate
	public void start() {
		log.info("MasterApp.start() — launching App-1 window");
		final ICatalogService svc = this.catalog;
		SwingUtilities.invokeLater(() -> buildFrame(svc));
	}

	private void buildFrame(ICatalogService svc) {
		JFrame frame = new JFrame("Spike — Master (App 1)  [JVM #1]");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(360, 320);
		frame.setLocation(80, 120);

		DefaultListModel<CatalogItem> model = new DefaultListModel<>();
		List<CatalogItem> items = svc.listItems();
		for (CatalogItem it : items) {
			model.addElement(it);
		}
		final JList<CatalogItem> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				CatalogItem sel = list.getSelectedValue();
				if (sel != null) {
					svc.setSelectedId(sel.getId());
				}
			}
		});

		JButton freeze = new JButton("Freeze me 5s");
		freeze.addActionListener(e -> {
			// Deliberately block this app's EDT — App-2 must stay responsive.
			log.warn("App-1 EDT frozen for 5s (isolation demo)");
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		});

		JButton crash = new JButton("Crash me");
		crash.addActionListener(e -> {
			log.error("App-1 crashing on purpose (isolation demo) — App-2 should survive and recover on restart");
			System.exit(7);
		});

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 6));
		buttons.add(freeze);
		buttons.add(crash);

		frame.setLayout(new BorderLayout(8, 8));
		frame.add(new JScrollPane(list), BorderLayout.CENTER);
		frame.add(buttons, BorderLayout.SOUTH);
		frame.setVisible(true);

		if (!items.isEmpty()) {
			list.setSelectedIndex(0);
		}
		log.info("App-1 window visible with {} items", items.size());
	}
}
