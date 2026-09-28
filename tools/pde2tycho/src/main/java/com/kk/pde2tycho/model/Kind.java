package com.kk.pde2tycho.model;

/** What a workspace project is, decided from its files (feature.xml, MANIFEST.MF, *.target). */
public enum Kind {
	PLUGIN, FRAGMENT, FEATURE, TARGET, OTHER;

	/** Plug-ins, fragments and features become Maven modules; the rest are never copied. */
	public boolean isModule() {
		return this == PLUGIN || this == FRAGMENT || this == FEATURE;
	}
}
