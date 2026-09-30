package com.kk.pde2tycho.model;

import java.util.List;

/**
 * One workspace project. {@code bsn}/{@code version} are the Bundle-SymbolicName and
 * Bundle-Version (for a feature: its id and version); {@code featurePlugins} lists a
 * feature's plug-in ids and is empty otherwise.
 */
public record Project(String name, Kind kind, String path, String bsn, String version, String bree,
		List<String> featurePlugins, boolean selected) {

	public Project withSelected(boolean value) {
		return new Project(name, kind, path, bsn, version, bree, featurePlugins, value);
	}
}
