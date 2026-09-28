package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PDE-only settings (${target.os}, -os/-ws/-arch/-nl, workspace variables) mean nothing
 * in a product; -XstartOnFirstThread is SWT-only and moves to the macOS-only slot.
 */
class LauncherArgsTest {

	final List<String> notes = new ArrayList<>();

	@Test
	void rewritesTheRealWorkspaceLaunch() {
		LauncherArgs.Rewritten r = LauncherArgs.rewrite(GenFixtures.launch(List.of()).vmArgs(),
				GenFixtures.launch(List.of()).programArgs(), notes);
		assertEquals("-Declipse.ignoreApp=true -Dosgi.noShutdown=true -Dlogback.configurationFile=configuration/logback.xml",
				r.vmArgs());
		assertEquals("-XstartOnFirstThread", r.vmArgsMac());
		assertEquals("-consoleLog", r.programArgs());
		assertTrue(notes.stream().anyMatch(n -> n.contains("configuration/logback.xml")), notes.toString());
		assertTrue(notes.stream().anyMatch(n -> n.contains("-XstartOnFirstThread")), notes.toString());
	}

	@Test
	void workspaceVariablesAreDropped() {
		LauncherArgs.Rewritten r = LauncherArgs.rewrite("-Dx=${workspace_loc}/y -Dz=1", "-data ${workspace_loc}", notes);
		assertEquals("-Dz=1", r.vmArgs());
		assertEquals("-data", r.programArgs());
	}

	@Test
	void quotedArgumentStaysOneToken() {
		assertEquals(List.of("-Dname=\"a b\"", "-Dc=d"), LauncherArgs.tokenize("  -Dname=\"a b\"   -Dc=d "));
	}
}
