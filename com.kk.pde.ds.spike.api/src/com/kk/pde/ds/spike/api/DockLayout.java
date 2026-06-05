package com.kk.pde.ds.spike.api;

import java.io.Serializable;

/**
 * The shared tiling scheme for the multi-frame combined UI.
 *
 * <p>
 * Both JVMs draw their windows into <em>one</em> global grid of slots. A slot index
 * {@code i} maps to a grid cell ({@code col = i % columns}, {@code row = i / columns})
 * and from there to absolute screen bounds. The master app (App-1) owns the even
 * slots, the detail app (App-2) owns the odd slots, so the fill order interleaves
 * M0, D0, M1, D1, … wrapping to a new row at every {@code columns} frames.
 * </p>
 *
 * <p>
 * The master computes this layout once (it starts first and measures the screen) and
 * publishes it through {@link ICatalogService#setLayout(DockLayout)}. The detail reads
 * it back in its first poll and computes its own slots from the <em>same</em> formula.
 * Because the math is identical on both sides, the windows tile without overlap and
 * without any per-frame messaging — the only thing shared is these few numbers.
 * </p>
 *
 * <p>Serializable value object — it crosses JVMs over ECF's Generic transport.</p>
 */
public final class DockLayout implements Serializable {

	private static final long serialVersionUID = 1L;

	private final int framesPerApp;
	private final int tileWidth;
	private final int tileHeight;
	private final int originX;
	private final int originY;
	private final int columns;

	public DockLayout(int framesPerApp, int tileWidth, int tileHeight, int originX, int originY, int columns) {
		this.framesPerApp = framesPerApp;
		this.tileWidth = tileWidth;
		this.tileHeight = tileHeight;
		this.originX = originX;
		this.originY = originY;
		this.columns = Math.max(1, columns);
	}

	public int getFramesPerApp() { return framesPerApp; }
	public int getTileWidth() { return tileWidth; }
	public int getTileHeight() { return tileHeight; }
	public int getOriginX() { return originX; }
	public int getOriginY() { return originY; }
	public int getColumns() { return columns; }

	/** Global slot index of the master app's {@code frameIndex}-th frame (even slots). */
	public static int masterSlot(int frameIndex) { return frameIndex * 2; }

	/** Global slot index of the detail app's {@code frameIndex}-th frame (odd slots). */
	public static int detailSlot(int frameIndex) { return frameIndex * 2 + 1; }

	/**
	 * Absolute screen bounds for a global slot — the single formula both apps share.
	 * Slots flow left-to-right and wrap to a new row every {@link #getColumns()} frames.
	 */
	public DockBounds slotBounds(int globalSlot) {
		int col = globalSlot % columns;
		int row = globalSlot / columns;
		return new DockBounds(originX + col * tileWidth, originY + row * tileHeight, tileWidth, tileHeight);
	}

	/**
	 * Distributes {@code panelCount} panels across {@code frames} frames as balanced
	 * contiguous chunks, returning {@code frameForPanel[i]} = the frame that owns panel
	 * {@code i}. Contiguous (not round-robin) so related panels stay together: with 4
	 * panels and 2 frames, frame 0 gets panels {0,1} and frame 1 gets {2,3}.
	 *
	 * <p>When {@code frames > panelCount} some frames own no panels (callers show a
	 * placeholder). When {@code frames <= 0} every panel maps to frame 0.</p>
	 */
	public static int[] distribute(int panelCount, int frames) {
		int[] frameForPanel = new int[Math.max(0, panelCount)];
		if (frames <= 1) {
			return frameForPanel; // all zeros — everything in frame 0
		}
		for (int i = 0; i < panelCount; i++) {
			// Balanced contiguous partition: panel i falls into chunk (i * frames) / panelCount.
			frameForPanel[i] = (int) (((long) i * frames) / panelCount);
		}
		return frameForPanel;
	}

	@Override
	public String toString() {
		return "DockLayout[framesPerApp=" + framesPerApp + ", tile=" + tileWidth + "x" + tileHeight
				+ ", origin=" + originX + "," + originY + ", columns=" + columns + "]";
	}
}
