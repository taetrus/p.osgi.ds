package com.kk.pde.ds.spike.api;

import java.io.Serializable;

/**
 * Screen-coordinate bounds of the master (anchor) window, published so the detail
 * (follower) window can dock itself against the master's right edge to form one
 * apparent combined UI.
 *
 * <p>Serializable value object — it crosses JVMs over ECF. Docking only makes
 * visual sense when both apps share one physical display (screen coordinates must
 * line up).</p>
 */
public final class DockBounds implements Serializable {

	private static final long serialVersionUID = 1L;

	private final int x, y, width, height;

	public DockBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public int getX() { return x; }
	public int getY() { return y; }
	public int getWidth() { return width; }
	public int getHeight() { return height; }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof DockBounds)) return false;
		DockBounds b = (DockBounds) o;
		return x == b.x && y == b.y && width == b.width && height == b.height;
	}

	@Override
	public int hashCode() {
		return ((x * 31 + y) * 31 + width) * 31 + height;
	}
}
