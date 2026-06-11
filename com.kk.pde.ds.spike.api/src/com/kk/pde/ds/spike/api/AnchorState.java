package com.kk.pde.ds.spike.api;

import java.io.Serializable;

/**
 * The live "follow the anchor" signal for the combined UI.
 *
 * <p>
 * One master frame (App-1's first frame) is the <em>anchor</em>: the only window the
 * user can drag or minimize. When it moves, every other window — the master's own
 * frames and the detail's frames in the other JVM — must move with it while keeping its
 * position <em>relative</em> to the anchor.
 * </p>
 *
 * <p>
 * Rather than broadcasting each window's absolute position, the anchor broadcasts just
 * this: a single screen-space {@code (offsetX, offsetY)} delta from the anchor's home
 * slot, plus a {@code minimized} flag. Every follower renders at
 * {@code DockLayout.slotBounds(slot) + offset} — because each frame applies the same
 * delta to its <em>own</em> home position, relative spacing is preserved automatically.
 * This is the dynamic twin of {@link DockLayout}: that shares the static grid, this
 * shares the one number that moves it.
 * </p>
 *
 * <p>Serializable value object — it crosses JVMs over ECF inside {@link DockState}.</p>
 */
public final class AnchorState implements Serializable {

	private static final long serialVersionUID = 1L;

	/** The neutral starting state: anchor at its home slot, expanded. */
	public static final AnchorState HOME = new AnchorState(0, 0, false);

	private final int offsetX;
	private final int offsetY;
	private final boolean minimized;

	public AnchorState(int offsetX, int offsetY, boolean minimized) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.minimized = minimized;
	}

	/** Horizontal shift of the anchor from its home slot, in screen pixels. */
	public int getOffsetX() { return offsetX; }

	/** Vertical shift of the anchor from its home slot, in screen pixels. */
	public int getOffsetY() { return offsetY; }

	/** {@code true} when the whole constellation should collapse (followers hidden). */
	public boolean isMinimized() { return minimized; }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AnchorState)) return false;
		AnchorState a = (AnchorState) o;
		return offsetX == a.offsetX && offsetY == a.offsetY && minimized == a.minimized;
	}

	@Override
	public int hashCode() {
		return (offsetX * 31 + offsetY) * 31 + (minimized ? 1 : 0);
	}

	@Override
	public String toString() {
		return "AnchorState[offset=" + offsetX + "," + offsetY + ", minimized=" + minimized + "]";
	}
}
