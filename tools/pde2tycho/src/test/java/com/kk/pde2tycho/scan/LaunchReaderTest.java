package com.kk.pde2tycho.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Launch;
import com.kk.pde2tycho.model.LaunchBundle;

/**
 * PDE stores a launch's bundles as "id@startLevel:autoStart" set entries. The launch
 * type does not matter: the real workspace's "Eclipse Application" launch is as
 * bundle-list based as an "OSGi Framework" one.
 */
class LaunchReaderTest {

	@TempDir
	Path dir;

	/** Trimmed from ws.p.osgi.ds/.metadata/.plugins/org.eclipse.debug.core/.launches/p2.product.launch. */
	static final String RUNTIME_WORKBENCH = """
			<?xml version="1.0" encoding="UTF-8" standalone="no"?>
			<launchConfiguration type="org.eclipse.pde.ui.RuntimeWorkbench">
			    <stringAttribute key="org.eclipse.jdt.launching.JRE_CONTAINER" value="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-21"/>
			    <stringAttribute key="org.eclipse.jdt.launching.PROGRAM_ARGUMENTS" value="-os ${target.os} -ws ${target.ws} -arch ${target.arch} -nl ${target.nl} -consoleLog"/>
			    <stringAttribute key="org.eclipse.jdt.launching.VM_ARGUMENTS" value="-Declipse.ignoreApp=true -Dosgi.noShutdown=true -XstartOnFirstThread"/>
			    <stringAttribute key="productId" value="com.kk.pde.ds.product"/>
			    <setAttribute key="selected_target_bundles">
			        <setEntry value="org.apache.commons.commons-io@default:default"/>
			        <setEntry value="org.apache.felix.http.jetty@default:true"/>
			        <setEntry value="org.apache.felix.scr@2:true"/>
			    </setAttribute>
			    <setAttribute key="selected_workspace_bundles">
			        <setEntry value="com.kk.pde.ds.api@4:true"/>
			    </setAttribute>
			</launchConfiguration>
			""";

	private Path write(String name, String xml) throws IOException {
		Path file = dir.resolve(name);
		Files.writeString(file, xml);
		return file;
	}

	@Test
	void readsBundleListsAndArguments() throws IOException {
		Launch launch = LaunchReader.read(write("p2.product.launch", RUNTIME_WORKBENCH));
		assertEquals("p2.product", launch.name());
		assertFalse(launch.selected());
		assertEquals("com.kk.pde.ds.product", launch.productId());
		assertFalse(launch.defaultAutoStart());
		assertEquals("JavaSE-21", launch.jre());
		assertTrue(launch.vmArgs().startsWith("-Declipse.ignoreApp=true"));
		assertTrue(launch.programArgs().endsWith("-consoleLog"));
		assertEquals(4, launch.bundles().size());
		assertTrue(launch.bundles().contains(new LaunchBundle("com.kk.pde.ds.api", 4, true, true)));
		assertTrue(launch.bundles().contains(new LaunchBundle("org.apache.felix.scr", 2, true, false)));
		assertTrue(launch.bundles().contains(new LaunchBundle("org.apache.felix.http.jetty", null, true, false)));
		assertTrue(launch.bundles().contains(new LaunchBundle("org.apache.commons.commons-io", null, null, false)));
	}

	@Test
	void equinoxLauncherDefaultsToAutoStart() throws IOException {
		Launch launch = LaunchReader.read(write("osgi.launch", """
				<launchConfiguration type="org.eclipse.pde.ui.EquinoxLauncher">
				    <setAttribute key="selected_target_bundles"><setEntry value="org.a@default:default"/></setAttribute>
				</launchConfiguration>
				"""));
		assertTrue(launch.defaultAutoStart());
		assertNull(launch.productId());
	}

	@Test
	void equinoxLauncherHonoursDefaultAutoStartFalse() throws IOException {
		Launch launch = LaunchReader.read(write("osgi.launch", """
				<launchConfiguration type="org.eclipse.pde.ui.EquinoxLauncher">
				    <booleanAttribute key="default_auto_start" value="false"/>
				    <setAttribute key="selected_target_bundles"><setEntry value="org.a@default:default"/></setAttribute>
				</launchConfiguration>
				"""));
		assertFalse(launch.defaultAutoStart());
	}

	@Test
	void launchWithoutBundleListIsNotAPluginLaunch() throws IOException {
		assertNull(LaunchReader.read(write("Review the File.launch", """
				<launchConfiguration type="org.eclipse.jdt.launching.localJavaApplication">
				    <stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="x.Main"/>
				</launchConfiguration>
				""")));
	}

	@Test
	void versionPinnedEntryDropsTheVersion() {
		assertEquals(new LaunchBundle("org.foo", 3, false, false), LaunchReader.parseEntry("org.foo*1.2.3@3:false", false));
	}

	@Test
	void blankProductIdIsNull() throws IOException {
		Launch launch = LaunchReader.read(write("x.launch", RUNTIME_WORKBENCH.replace(
				"value=\"com.kk.pde.ds.product\"", "value=\"\"")));
		assertNull(launch.productId());
	}
}
