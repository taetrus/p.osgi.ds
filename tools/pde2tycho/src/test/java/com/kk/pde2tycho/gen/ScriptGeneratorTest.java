package com.kk.pde2tycho.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Inventory;

/** Launch VM args go on the java command line, before "$@"/%* and before -jar. */
class ScriptGeneratorTest {

	@TempDir
	Path tmp;

	@Test
	void writesBothScriptsWithTheLaunchArguments() throws Exception {
		Inventory inv = GenFixtures.simple("/a");
		LauncherArgs.Rewritten args = LauncherArgs.rewrite(inv.selectedLaunch().vmArgs(),
				inv.selectedLaunch().programArgs(), new ArrayList<>());
		ScriptGenerator.generate(inv, inv.selectedLaunch(), args, tmp);

		String sh = Files.readString(tmp.resolve("run.sh"));
		assertTrue(sh.contains("exec java -Declipse.ignoreApp=true -Dosgi.noShutdown=true"
				+ " -Dlogback.configurationFile=configuration/logback.xml \"$@\" -jar \"$OSGI_JAR\""
				+ " -configuration configuration -console -consoleLog\n"), sh);
		assertTrue(sh.contains("PRODUCT_DIR=\"$PRODUCTS/macosx/cocoa/$ARCH/Eclipse.app/Contents/Eclipse\""), sh);
		assertFalse(sh.contains("Linux)"), sh);
		assertFalse(sh.contains("-XstartOnFirstThread"), sh);
		assertFalse(sh.contains("\r"), "run.sh must be LF");
		assertTrue(Files.isExecutable(tmp.resolve("run.sh")));

		String bat = Files.readString(tmp.resolve("run.bat"));
		assertTrue(bat.contains("\\target\\products\\com.x.product\\win32\\win32\\x86_64\""), bat);
		assertTrue(bat.contains("\r\n") && !bat.replace("\r\n", "").contains("\n"), "run.bat must be CRLF throughout");
	}

	@Test
	void consoleAndConfigurationAreSuppliedByTheScript() {
		assertEquals("-consoleLog -clean", ScriptGenerator.scriptProgramArgs("-console -configuration x -consoleLog -clean"));
	}

	@Test
	void linuxEnvironmentAddsALinuxCase() throws Exception {
		Inventory inv = GenFixtures.simple("/a");
		inv = inv.withEnvironments(List.of("linux/gtk/x86_64"));
		ScriptGenerator.generate(inv, inv.selectedLaunch(), new LauncherArgs.Rewritten("", "", ""), tmp);
		assertTrue(Files.readString(tmp.resolve("run.sh")).contains("PRODUCT_DIR=\"$PRODUCTS/linux/gtk/$ARCH\""));
		assertFalse(Files.exists(tmp.resolve("run.bat")));
	}
}
