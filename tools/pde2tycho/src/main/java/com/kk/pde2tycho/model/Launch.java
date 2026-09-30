package com.kk.pde2tycho.model;

import java.util.List;

/**
 * A plug-in based PDE launch configuration. {@code defaultAutoStart} is what a
 * "default" auto-start entry means for this launch type.
 */
public record Launch(String name, boolean selected, String productId, boolean defaultAutoStart,
		List<LaunchBundle> bundles, String vmArgs, String programArgs, String jre) {

	public Launch withSelection(boolean isSelected, String id) {
		return new Launch(name, isSelected, id, defaultAutoStart, bundles, vmArgs, programArgs, jre);
	}
}
