package com.kk.pde.ds.spike.master;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.spike.api.CatalogItem;
import com.kk.pde.ds.spike.api.DockBounds;
import com.kk.pde.ds.spike.api.ICatalogService;

/**
 * App-1 ("master") — docking anchor of the combined UI. Hosts several panels, each
 * badged "APP-1 · MASTER" in a teal accent so its owning process is obvious when the
 * two apps are docked together. Publishes its live screen bounds through
 * {@link ICatalogService} so the detail app glues itself to the right edge.
 */
@Component
public class MasterApp {

	private static final Logger log = LoggerFactory.getLogger(MasterApp.class);

	static final int HEADER_H = 34;
	private static final String APP_TAG = "APP-1 · MASTER";
	private static final Color ACCENT = new Color(0x2FD4A7);   // teal = App-1
	private static final Color HEADER_BG = new Color(0x16202C);
	private static final Color PANEL_HEAD = new Color(0x121B26);
	private static final Color BODY_BG = new Color(0x0E151F);
	private static final Color INK = new Color(0xE8EEF6);
	private static final Color MUTED = new Color(0x7C8A9C);

	private volatile ICatalogService catalog;
	private JFrame frame;
	private JTextArea activity;
	private JLabel summary;
	private int beat;

	@Reference
	public void setCatalog(ICatalogService catalog) { this.catalog = catalog; }

	public void unsetCatalog(ICatalogService catalog) {
		if (this.catalog == catalog) this.catalog = null;
	}

	@Activate
	public void start() {
		log.info("MasterApp.start() — launching App-1 anchor window");
		final ICatalogService svc = this.catalog;
		SwingUtilities.invokeLater(() -> buildFrame(svc));
	}

	private void buildFrame(ICatalogService svc) {
		frame = new JFrame("Combined Inventory");
		frame.setUndecorated(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 520 + HEADER_H);
		frame.setLocation(180, 130);

		JPanel root = new JPanel(new BorderLayout());
		root.add(makeHeader(), BorderLayout.NORTH);
		root.add(makeBody(svc), BorderLayout.CENTER);
		frame.setContentPane(root);

		frame.addComponentListener(new ComponentAdapter() {
			@Override public void componentMoved(ComponentEvent e) { publish(svc); }
			@Override public void componentResized(ComponentEvent e) { publish(svc); }
		});

		frame.setVisible(true);
		publish(svc);

		// live heartbeat — proves App-1 is doing its own work in its own process
		new Timer(1000, e -> {
			beat++;
			if (activity != null) {
				activity.append("App-1 ▸ heartbeat #" + beat + "\n");
				activity.setCaretPosition(activity.getDocument().getLength());
			}
		}).start();

		log.info("App-1 anchor visible at {},{} {}x{}", frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight());
	}

	private void publish(ICatalogService svc) {
		if (svc != null && frame != null) {
			svc.setHostBounds(new DockBounds(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight()));
		}
	}

	private JPanel makeHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(HEADER_BG);
		header.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		header.setPreferredSize(new Dimension(10, HEADER_H));

		JButton close = new JButton("●");
		close.setForeground(new Color(0xFF5B57));
		close.setBackground(HEADER_BG);
		close.setBorder(BorderFactory.createEmptyBorder());
		close.setFocusPainted(false);
		close.setOpaque(true);
		close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		close.setToolTipText("Close combined app");
		close.addActionListener(e -> { if (catalog != null) catalog.requestShutdown(); });

		JLabel title = new JLabel("▦  Combined Inventory App");
		title.setForeground(INK);
		title.setHorizontalAlignment(SwingConstants.CENTER);

		header.add(close, BorderLayout.WEST);
		header.add(title, BorderLayout.CENTER);

		final Point[] a = { null };
		MouseAdapter drag = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) { a[0] = e.getPoint(); }
			@Override public void mouseReleased(MouseEvent e) { a[0] = null; }
			@Override public void mouseDragged(MouseEvent e) {
				Point p = a[0];
				if (p != null) frame.setLocation(frame.getX() + e.getX() - p.x, frame.getY() + e.getY() - p.y);
			}
		};
		header.addMouseListener(drag); header.addMouseMotionListener(drag);
		title.addMouseListener(drag); title.addMouseMotionListener(drag);
		return header;
	}

	private JPanel makeBody(ICatalogService svc) {
		JPanel body = new JPanel();
		body.setBackground(BODY_BG);
		body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		// --- Panel 1: CATALOG (the list) ---
		DefaultListModel<CatalogItem> model = new DefaultListModel<>();
		List<CatalogItem> items = svc.listItems();
		int totalQty = 0;
		for (CatalogItem it : items) { model.addElement(it); totalQty += it.getQuantity(); }
		final JList<CatalogItem> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				CatalogItem sel = list.getSelectedValue();
				if (sel != null && catalog != null) catalog.setSelectedId(sel.getId());
			}
		});
		JScrollPane listScroll = new JScrollPane(list);
		listScroll.setPreferredSize(new Dimension(360, 150));
		body.add(badged("CATALOG", grow(listScroll, 150)));
		body.add(Box.createVerticalStrut(8));

		// --- Panel 2: SUMMARY (computed in App-1) ---
		summary = new JLabel(items.size() + " items · " + totalQty + " in stock");
		summary.setForeground(INK);
		body.add(badged("SUMMARY", fixed(wrap(summary), 30)));
		body.add(Box.createVerticalStrut(8));

		// --- Panel 3: ACTIVITY (live, in App-1's process) ---
		activity = new JTextArea(4, 22);
		activity.setEditable(false);
		activity.setOpaque(false);
		activity.setForeground(ACCENT);
		activity.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		JScrollPane actScroll = new JScrollPane(activity);
		actScroll.setBorder(null);
		actScroll.setOpaque(false);
		actScroll.getViewport().setOpaque(false);
		body.add(badged("ACTIVITY", grow(actScroll, 90)));
		body.add(Box.createVerticalStrut(8));

		// --- Footer: isolation controls (tagged App-1) ---
		JButton freeze = new JButton("Freeze 5s");
		freeze.addActionListener(e -> {
			log.warn("App-1 EDT frozen for 5s — detail stays responsive");
			try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		});
		JButton crash = new JButton("Crash");
		crash.addActionListener(e -> { log.error("App-1 crashing on purpose — detail should survive"); System.exit(7); });
		JPanel ctl = new JPanel(new GridLayout(1, 2, 6, 6));
		ctl.setOpaque(false);
		ctl.add(freeze); ctl.add(crash);
		body.add(badged("CONTROLS", fixed(ctl, 36)));

		if (!items.isEmpty()) list.setSelectedIndex(0);
		return body;
	}

	/** Wraps content in a badged container with a teal accent + "APP-1 · MASTER" tag. */
	private JComponent badged(String name, JComponent content) {
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(BODY_BG);
		p.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT));
		p.setAlignmentX(JComponent.LEFT_ALIGNMENT);

		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(PANEL_HEAD);
		head.setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
		JLabel tag = new JLabel(APP_TAG);
		tag.setForeground(ACCENT);
		tag.setFont(tag.getFont().deriveFont(Font.BOLD, 10f));
		JLabel nm = new JLabel(name);
		nm.setForeground(MUTED);
		nm.setFont(nm.getFont().deriveFont(10f));
		nm.setHorizontalAlignment(SwingConstants.RIGHT);
		head.add(tag, BorderLayout.WEST);
		head.add(nm, BorderLayout.EAST);

		content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		if (content instanceof JComponent) content.setOpaque(false);

		p.add(head, BorderLayout.NORTH);
		p.add(content, BorderLayout.CENTER);
		return p;
	}

	private JComponent wrap(JComponent c) {
		JPanel w = new JPanel(new BorderLayout());
		w.setOpaque(false);
		w.add(c, BorderLayout.WEST);
		return w;
	}

	private JComponent grow(JComponent c, int pref) {
		c.setPreferredSize(new Dimension(360, pref));
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref + 60));
		return c;
	}

	private JComponent fixed(JComponent c, int h) {
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		c.setPreferredSize(new Dimension(360, h));
		return c;
	}
}
