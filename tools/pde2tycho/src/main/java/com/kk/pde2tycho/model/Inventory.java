package com.kk.pde2tycho.model;

import java.util.List;

/** Everything scan found; the hand-editable contract between scan and generate. */
public record Inventory(String workspace, String groupId, String version, String tychoVersion,
		List<String> environments, List<Project> projects, Target target, List<Launch> launches,
		List<String> warnings) {

	/** The single launch marked selected, or null when none or several are. */
	public Launch selectedLaunch() {
		List<Launch> selected = launches.stream().filter(Launch::selected).toList();
		return selected.size() == 1 ? selected.get(0) : null;
	}

	/** Selected projects that become Maven modules: plug-ins, fragments and features. */
	public List<Project> modules() {
		return projects.stream().filter(Project::selected).filter(p -> p.kind().isModule()).toList();
	}

	public Inventory withEnvironments(List<String> envs) {
		return new Inventory(workspace, groupId, version, tychoVersion, envs, projects, target, launches, warnings);
	}
}
