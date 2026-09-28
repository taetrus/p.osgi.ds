package com.kk.pde2tycho.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.Project;
import com.kk.pde2tycho.resolve.ArtifactResolver;

/**
 * The scanner joins the workspace's pieces: project locations from .metadata, the
 * active target from the PDE preferences, launches from .launches and projects.
 */
class WorkspaceScannerTest {

	@TempDir
	Path tmp;

	TestWorkspace ws;
	final List<String> progress = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		ws = new TestWorkspace(tmp);
	}

	private Inventory scan(ArtifactResolver resolver, List<Path> extra) throws IOException {
		return new WorkspaceScanner(resolver, null, extra, progress::add).scan(ws.root);
	}

	private Inventory scan() throws IOException {
		return scan(ArtifactResolver.OFFLINE, List.of());
	}

	private Path directoryTarget() throws IOException {
		Path t = ws.project("com.x.target", "com.x.target");
		TestWorkspace.write(t.resolve("t.target"), "<?xml version=\"1.0\"?><target name=\"t\"><locations>"
				+ "<location path=\"${project_loc:/com.x.target}/lib\" type=\"Directory\"/></locations></target>");
		ws.prefs("resource:/com.x.target/t.target");
		return t.resolve("lib");
	}

	@Test
	void notAWorkspaceFails() {
		assertThrows(IOException.class,
				() -> new WorkspaceScanner(ArtifactResolver.OFFLINE, null, List.of(), s -> {
				}).scan(tmp.resolve("empty")));
	}

	@Test
	void projectsComeFromLocationFilesOutsideTheWorkspace() throws IOException {
		Path api = ws.bundle("com.x.api", "com.x.api");
		Project p = scan().projects().get(0);
		assertEquals("com.x.api", p.name());
		assertEquals(api.toString(), p.path());
		assertEquals(Kind.PLUGIN, p.kind());
	}

	@Test
	void locationWithSpacesIsDecoded() throws IOException {
		Path dir = ws.project("spaced", "my src dir");
		assertEquals(dir, WorkspaceScanner.readLocation(
				ws.plugins().resolve("org.eclipse.core.resources/.projects/spaced/.location")));
	}

	@Test
	void activeTargetFromResourceHandleKeepsMavenLocationsVerbatim() throws IOException {
		Path t = ws.project("com.x.target", "com.x.target");
		TestWorkspace.write(t.resolve("t.target"), "<?xml version=\"1.0\"?><target name=\"t\"><locations>"
				+ "<location type=\"Maven\"><dependencies/></location></locations></target>");
		ws.prefs("resource:/com.x.target/t.target");
		Inventory inv = scan();
		assertEquals(t.resolve("t.target").toString(), inv.target().source());
		assertEquals(1, inv.target().locations().size());
	}

	@Test
	void directoryJarsAreResolvedOrVendored() throws IOException {
		Path lib = directoryTarget();
		String knownSha1 = JarInfo.sha1(TestJars.bundle(lib.resolve("a.jar"), "org.a", "1.0.0"));
		TestJars.bundle(lib.resolve("b.jar"), "com.b", "2.0.0");
		TestJars.plain(lib.resolve("plain.jar"));
		Inventory inv = scan(sha1 -> sha1.equals(knownSha1) ? Optional.of("org.a:a:1.0.0") : Optional.empty(), List.of());
		assertEquals("org.a:a:1.0.0", inv.target().resolved().get(0).gav());
		assertEquals("com.b", inv.target().vendor().get(0).bsn());
		assertEquals("not on Maven Central", inv.target().vendor().get(0).reason());
		assertTrue(inv.warnings().stream().anyMatch(w -> w.contains("plain.jar")), inv.warnings().toString());
		assertTrue(progress.get(0).contains("3 directory jar(s)"), progress.toString());
	}

	@Test
	void threeLookupFailuresInARowSwitchToOffline() throws IOException {
		Path lib = directoryTarget();
		for (int i = 0; i < 5; i++) {
			TestJars.bundle(lib.resolve("j" + i + ".jar"), "org.j" + i, "1.0.0");
		}
		AtomicInteger calls = new AtomicInteger();
		Inventory inv = scan(sha1 -> {
			calls.incrementAndGet();
			throw new IOException("timed out");
		}, List.of());
		assertEquals(3, calls.get());
		assertEquals(5, inv.target().vendor().size());
		assertEquals("offline", inv.target().vendor().get(4).reason());
		assertTrue(inv.warnings().stream().anyMatch(w -> w.contains("unreachable")), inv.warnings().toString());
	}

	@Test
	void missingTargetIsAWarningNotAFailure() throws IOException {
		Inventory inv = scan();
		assertTrue(inv.target().locations().isEmpty());
		assertTrue(inv.warnings().stream().anyMatch(w -> w.contains("No active target")), inv.warnings().toString());
	}

	@Test
	void singleLaunchIsPreselectedWithDefaultProductId() throws IOException {
		ws.bundle("com.x.api", "com.x.api");
		ws.bundle("com.x.imp", "com.x.imp");
		ws.launch("app", "<launchConfiguration type=\"org.eclipse.pde.ui.EquinoxLauncher\">"
				+ "<setAttribute key=\"selected_workspace_bundles\"><setEntry value=\"com.x.api@4:true\"/></setAttribute>"
				+ "</launchConfiguration>");
		Launch launch = scan().launches().get(0);
		assertTrue(launch.selected());
		assertEquals("com.x.product", launch.productId());
	}

	@Test
	void configIniReportsBundlesPdeAddedAutomatically() throws IOException {
		ws.launch("app", "<launchConfiguration type=\"org.eclipse.pde.ui.EquinoxLauncher\">"
				+ "<setAttribute key=\"selected_target_bundles\"><setEntry value=\"org.a@default:default\"/></setAttribute>"
				+ "</launchConfiguration>");
		Path extra = TestJars.bundle(tmp.resolve("m2/org.extra.jar"), "org.extra", "1.0.0");
		TestWorkspace.write(ws.plugins().resolve("org.eclipse.pde.core/app/config.ini"),
				"osgi.bundles=reference\\:file\\:" + extra + "@2\\:start\n");
		Inventory inv = scan();
		assertTrue(inv.warnings().stream().anyMatch(w -> w.contains("org.extra")), inv.warnings().toString());
	}

	@Test
	void featureWithPluginsOutsideTheWorkspaceStartsDeselected() throws IOException {
		ws.bundle("com.x.api", "com.x.api");
		Path f = ws.project("com.x.feature", "com.x.feature");
		TestWorkspace.write(f.resolve("feature.xml"), "<feature id=\"com.x.feature\" version=\"1.0.0.qualifier\">"
				+ "<plugin id=\"com.x.api\"/><plugin id=\"com.x.missing\"/></feature>");
		Project feature = scan().projects().stream().filter(p -> p.kind() == Kind.FEATURE).findFirst().orElseThrow();
		assertFalse(feature.selected());
	}

	@Test
	void addProjectIncludesAnUnregisteredProject() throws IOException {
		Path extra = Files.createDirectories(tmp.resolve("elsewhere/com.x.extra/META-INF"));
		Files.writeString(extra.resolve("MANIFEST.MF"), "Bundle-SymbolicName: com.x.extra\n");
		Inventory inv = scan(ArtifactResolver.OFFLINE, List.of(extra.getParent()));
		assertTrue(inv.projects().stream().anyMatch(p -> p.bsn() != null && p.bsn().equals("com.x.extra")));
	}

	@Test
	void groupIdIsTheCommonPrefixOfSelectedBundles() {
		Project a = new Project("a", Kind.PLUGIN, "/a", "com.kk.pde.ds.api", "1", null, List.of(), true);
		Project b = new Project("b", Kind.PLUGIN, "/b", "com.kk.pde.ds.mcp.api", "1", null, List.of(), true);
		Project other = new Project("o", Kind.PLUGIN, "/o", "org.other", "1", null, List.of(), false);
		assertEquals("com.kk.pde.ds", WorkspaceScanner.groupId(List.of(a, b, other)));
		assertEquals("migrated", WorkspaceScanner.groupId(List.of()));
	}

	/**
	 * Controller ruling: LaunchReader.parseEntry throws NumberFormatException for a malformed
	 * start level ("x" is not an int). readLaunches must catch that alongside IOException so a
	 * malformed launch becomes a warning and the scan still completes, instead of scan() itself
	 * throwing.
	 */
	@Test
	void malformedLaunchEntryBecomesAWarningNotAFailure() throws IOException {
		ws.launch("bad", "<launchConfiguration type=\"org.eclipse.pde.ui.EquinoxLauncher\">"
				+ "<setAttribute key=\"selected_workspace_bundles\"><setEntry value=\"org.a@x:true\"/></setAttribute>"
				+ "</launchConfiguration>");
		Inventory inv = scan();
		assertTrue(inv.warnings().stream().anyMatch(w -> w.contains("skipped")), inv.warnings().toString());
		assertTrue(inv.launches().isEmpty(), inv.launches().toString());
	}
}
