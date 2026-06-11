package com.kk.pde.ds.spike.detail;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.spike.api.AnchorState;
import com.kk.pde.ds.spike.api.CatalogItem;
import com.kk.pde.ds.spike.api.DockBounds;
import com.kk.pde.ds.spike.api.DockLayout;
import com.kk.pde.ds.spike.api.DockState;
import com.kk.pde.ds.spike.api.ICatalogService;

/**
 * App-2 ("detail") — follower of the combined UI. Opens <em>N</em> borderless windows
 * tiled into the master's shared grid (odd slots — {@link DockLayout#detailSlot}). Its
 * panels (SELECTED ITEM, INSPECTOR, CONNECTION, CONTROLS) are split across those frames
 * so each window shows distinct content. Every frame is badged "APP-2 · DETAIL" in amber.
 *
 * <p>
 * Unlike the master, the detail does <em>not</em> build its windows on activation: it
 * has no grid yet. It polls {@link ICatalogService#getDockState()} and, on the first
 * snapshot that carries a {@link DockLayout}, tiles its frames into the same grid. It
 * trusts {@code layout.framesPerApp} — the master is the single source of truth for N.
 * Subsequent polls only refresh the live selection and honor the closing flag.
 * </p>
 */
@Component
public class DetailApp {

	private static final Logger log = LoggerFactory.getLogger(DetailApp.class);

	private static final String APP_TAG = "APP-2 · DETAIL";
	private static final Color ACCENT = new Color(0xFFB454);   // amber = App-2
	private static final Color HEADER_BG = new Color(0x16202C);
	private static final Color PANEL_HEAD = new Color(0x121B26);
	private static final Color BODY_BG = new Color(0x0E151F);
	private static final Color INK = new Color(0xE8EEF6);
	private static final Color MUTED = new Color(0x7C8A9C);
	private static final Color GOOD = new Color(0x2FD4A7);
	private static final int HEADER_H = 34;

	private volatile ICatalogService catalog;
	private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "detail-poll");
		t.setDaemon(true);
		return t;
	});

	private final List<JFrame> frames = new ArrayList<>();
	private final List<DockBounds> homeBounds = new ArrayList<>();   // each frame's un-shifted slot bounds
	private AnchorState lastAnchor = AnchorState.HOME;               // last anchor state we acted on
	private final List<JLabel> headerDots = new ArrayList<>();
	private JLabel nameLabel, qtyLabel, statusLabel;
	private JLabel idLabel, lenLabel, uptimeLabel;
	private JTextArea descArea;
	private Timer poll;
	private boolean built;
	private String lastShownId = " ";
	private int uptime;

	/** A built panel awaiting placement into one of the tiled frames. */
	private static final class Panel {
		final String name;
		final JComponent content;
		Panel(String name, JComponent content) { this.name = name; this.content = content; }
	}

	@Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
	public void setCatalog(ICatalogService catalog) {
		this.catalog = catalog;
		log.info("Connected to master ICatalogService (remote proxy {})", catalog.getClass().getName());
	}

	public void unsetCatalog(ICatalogService catalog) {
		if (this.catalog == catalog) { this.catalog = null; log.warn("Lost connection to master ICatalogService"); }
	}

	@Activate
	public void start() {
		log.info("DetailApp.start() — waiting for master grid layout before opening windows");
		SwingUtilities.invokeLater(() -> {
			poll = new Timer(120, e -> tick());
			poll.start();
			log.info("App-2 polling host every 120ms for layout + selection");
		});
	}

	@Deactivate
	public void stop() {
		if (poll != null) poll.stop();
		exec.shutdownNow();
	}

	/** EDT (Timer) → only submit the remote call to the background executor. */
	private void tick() {
		final ICatalogService svc = this.catalog;
		if (svc == null) { showUnavailable(); return; }
		exec.submit(() -> {
			try {
				DockState st = svc.getDockState();
				SwingUtilities.invokeLater(() -> apply(st));
			} catch (Exception ex) {
				SwingUtilities.invokeLater(this::showUnavailable);
			}
		});
	}

	private void apply(DockState st) {
		if (st == null) { showUnavailable(); return; }
		if (st.isClosing()) {
			log.info("Host signalled combined close — App-2 exiting too");
			if (poll != null) poll.stop();
			for (JFrame f : frames) f.dispose();
			System.exit(0);
			return;
		}
		// First snapshot carrying a layout → tile our frames into the shared grid.
		if (!built && st.getLayout() != null) {
			buildFrames(st.getLayout());
			built = true;
		}
		if (!built) return; // layout not published yet; keep waiting

		applyAnchor(st.getAnchor()); // follow the master's draggable/minimizable anchor

		CatalogItem item = st.getSelected();
		if (item == null) {
			nameLabel.setText("(nothing selected)"); qtyLabel.setText(" "); descArea.setText("");
			idLabel.setText("—"); lenLabel.setText("—");
		} else {
			nameLabel.setText(item.getName());
			qtyLabel.setText("In stock: " + item.getQuantity());
			descArea.setText(item.getDescription());
			idLabel.setText(item.getId());
			lenLabel.setText(item.getName().length() + " / " + item.getDescription().length());
		}
		for (JLabel dot : headerDots) dot.setForeground(GOOD);
		statusLabel.setForeground(GOOD);
		statusLabel.setText("✔ docked to host (App-1)");
		String id = item == null ? null : item.getId();
		if (id == null ? lastShownId != null : !id.equals(lastShownId)) {
			lastShownId = id;
			log.info("Detail now showing remote selection: {}", item == null ? "(none)" : item.getName());
		}
	}

	/**
	 * Follow the master's anchor: shift every detail frame to {@code home + offset} and
	 * hide/show them with the minimize flag. Cheap-guarded — we only touch the windows
	 * when the anchor state actually changed (it's unchanged on almost every 120ms poll).
	 */
	private void applyAnchor(AnchorState a) {
		if (a == null) a = AnchorState.HOME;
		if (a.equals(lastAnchor)) return;          // nothing moved or toggled since last tick
		lastAnchor = a;
		for (int k = 0; k < frames.size(); k++) {
			DockBounds home = homeBounds.get(k);
			JFrame f = frames.get(k);
			f.setLocation(home.getX() + a.getOffsetX(), home.getY() + a.getOffsetY());
			f.setVisible(!a.isMinimized());
		}
		log.info("Followed anchor → offset {},{} {}", a.getOffsetX(), a.getOffsetY(),
				a.isMinimized() ? "(minimized — frames hidden)" : "(visible)");
	}

	private void buildFrames(DockLayout layout) {
		int n = Math.max(1, layout.getFramesPerApp());
		log.info("App-2 building {} frame(s) into the shared grid; {}", n, layout);

		List<Panel> panels = buildPanels();
		int[] frameForPanel = DockLayout.distribute(panels.size(), n);

		for (int k = 0; k < n; k++) {
			DockBounds b = layout.slotBounds(DockLayout.detailSlot(k));
			JFrame frame = new JFrame("Detail · D-" + (k + 1));
			frame.setUndecorated(true);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setBounds(b.getX(), b.getY(), b.getWidth(), b.getHeight());

			JPanel body = bodyPanel();
			boolean any = false;
			for (int i = 0; i < panels.size(); i++) {
				if (frameForPanel[i] == k) {
					body.add(badged(panels.get(i).name, panels.get(i).content));
					body.add(Box.createVerticalStrut(8));
					any = true;
				}
			}
			if (!any) body.add(badged("EXTRA", fixed(wrap(muted(new JLabel("(no panel assigned to this frame)"))), 30)));

			JPanel root = new JPanel(new BorderLayout());
			root.add(makeHeader("D-" + (k + 1)), BorderLayout.NORTH);
			root.add(body, BorderLayout.CENTER);
			frame.setContentPane(root);
			frame.setVisible(true);
			frames.add(frame);
			homeBounds.add(b);
			log.info("App-2 frame {} at slot {} → {},{} {}x{}", "D-" + (k + 1),
					DockLayout.detailSlot(k), b.getX(), b.getY(), b.getWidth(), b.getHeight());
		}

		// App-2's own uptime — proves it runs independently of App-1
		new Timer(1000, e -> { uptime++; if (uptimeLabel != null) uptimeLabel.setText(uptime + "s"); }).start();
	}

	private JPanel bodyPanel() {
		JPanel body = new JPanel();
		body.setBackground(BODY_BG);
		body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		return body;
	}

	/** Builds the four detail panels independently so they can be split across frames. */
	private List<Panel> buildPanels() {
		List<Panel> panels = new ArrayList<>();

		// --- SELECTED ITEM (the master's remote selection) ---
		nameLabel = ink(new JLabel("—"), 16, true);
		qtyLabel = muted(new JLabel(" "));
		descArea = new JTextArea(3, 22);
		descArea.setEditable(false); descArea.setLineWrap(true); descArea.setWrapStyleWord(true);
		descArea.setOpaque(false); descArea.setForeground(INK);
		JPanel sel = col();
		sel.add(muted(new JLabel("owned by App-1, fetched over ECF:")));
		sel.add(nameLabel); sel.add(qtyLabel); sel.add(descArea);
		panels.add(new Panel("SELECTED ITEM", grow(sel, 130)));

		// --- INSPECTOR (derived locally in App-2 + App-2 uptime) ---
		idLabel = mono(new JLabel("—"));
		lenLabel = mono(new JLabel("—"));
		uptimeLabel = mono(new JLabel("0s"));
		JPanel insp = new JPanel(new GridLayout(0, 2, 6, 4));
		insp.setOpaque(false);
		insp.add(muted(new JLabel("item id"))); insp.add(idLabel);
		insp.add(muted(new JLabel("name / desc length"))); insp.add(lenLabel);
		insp.add(muted(new JLabel("App-2 uptime"))); insp.add(uptimeLabel);
		panels.add(new Panel("INSPECTOR", fixed(insp, 84)));

		// --- CONNECTION ---
		statusLabel = muted(new JLabel("Waiting for host…"));
		panels.add(new Panel("CONNECTION", fixed(wrap(statusLabel), 30)));

		// --- CONTROLS (isolation demo, tagged App-2) ---
		JButton freeze = new JButton("Freeze 5s");
		freeze.addActionListener(e -> {
			log.warn("App-2 EDT frozen for 5s (isolation demo)");
			try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		});
		JButton crash = new JButton("Crash");
		crash.addActionListener(e -> { log.error("App-2 crashing on purpose — App-1 unaffected"); System.exit(7); });
		JPanel ctl = new JPanel(new GridLayout(1, 2, 6, 6));
		ctl.setOpaque(false); ctl.add(freeze); ctl.add(crash);
		panels.add(new Panel("CONTROLS", fixed(ctl, 36)));

		return panels;
	}

	private void showUnavailable() {
		for (JLabel dot : headerDots) dot.setForeground(ACCENT);
		if (statusLabel != null) {
			statusLabel.setForeground(ACCENT);
			statusLabel.setText("⚠ Host (App-1) unavailable — waiting…");
		}
	}

	private JPanel makeHeader(String title) {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(HEADER_BG);
		header.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
		header.setPreferredSize(new Dimension(10, HEADER_H));
		JLabel dot = new JLabel("●");
		dot.setForeground(ACCENT);
		headerDots.add(dot);
		JLabel t = new JLabel(title + " · App-2");
		t.setForeground(MUTED);
		t.setHorizontalAlignment(SwingConstants.RIGHT);
		header.add(dot, BorderLayout.WEST);
		header.add(t, BorderLayout.CENTER);
		return header;
	}

	// --- badge + layout helpers (amber accent, "APP-2 · DETAIL") ---

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
		content.setOpaque(false);
		p.add(head, BorderLayout.NORTH);
		p.add(content, BorderLayout.CENTER);
		return p;
	}

	private JPanel col() { JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
	private JComponent wrap(JComponent c) { JPanel w = new JPanel(new BorderLayout()); w.setOpaque(false); w.add(c, BorderLayout.WEST); return w; }
	private JComponent grow(JComponent c, int pref) { c.setPreferredSize(new Dimension(360, pref)); c.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref + 80)); return c; }
	private JComponent fixed(JComponent c, int h) { c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h)); c.setPreferredSize(new Dimension(360, h)); return c; }
	private JLabel ink(JLabel l, float size, boolean bold) { l.setForeground(INK); l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, size)); return l; }
	private JLabel muted(JLabel l) { l.setForeground(MUTED); return l; }
	private JLabel mono(JLabel l) { l.setForeground(INK); l.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); return l; }
}
