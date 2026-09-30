package com.kk.pde2tycho.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kk.pde2tycho.model.Kind;
import com.kk.pde2tycho.model.Project;

/**
 * A project's kind comes from its files: feature.xml → feature, a manifest with a
 * Bundle-SymbolicName → plug-in or fragment, only *.target files → target.
 */
class ProjectReaderTest {

	@TempDir
	Path dir;

	private void write(String relative, String content) throws IOException {
		Path file = dir.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	@Test
	void pluginDropsDirectivesAndTakesFirstBree() throws IOException {
		write("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nBundle-SymbolicName: com.x.api;singleton:=true\n"
				+ "Bundle-Version: 1.2.0.qualifier\nBundle-RequiredExecutionEnvironment: JavaSE-17, JavaSE-21\n");
		Project p = ProjectReader.read("api", dir, new ArrayList<>());
		assertEquals(Kind.PLUGIN, p.kind());
		assertEquals("com.x.api", p.bsn());
		assertEquals("1.2.0.qualifier", p.version());
		assertEquals("JavaSE-17", p.bree());
		assertTrue(p.selected());
	}

	@Test
	void fragmentIsDetectedByFragmentHost() throws IOException {
		write("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nBundle-SymbolicName: com.x.frag\n"
				+ "Bundle-Version: 1.0.0\nFragment-Host: com.x.api\n");
		assertEquals(Kind.FRAGMENT, ProjectReader.read("frag", dir, new ArrayList<>()).kind());
	}

	@Test
	void manifestWithoutTrailingNewlineKeepsLastHeader() throws IOException {
		write("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nBundle-SymbolicName: com.x\nBundle-Version: 3.1.4");
		assertEquals("3.1.4", ProjectReader.read("x", dir, new ArrayList<>()).version());
	}

	@Test
	void featureListsItsPlugins() throws IOException {
		write("feature.xml", "<feature id=\"com.x.feature\" version=\"1.0.0.qualifier\">"
				+ "<plugin id=\"com.x.api\" version=\"0.0.0\"/><plugin id=\"com.x.imp\" version=\"0.0.0\"/></feature>");
		Project p = ProjectReader.read("feature", dir, new ArrayList<>());
		assertEquals(Kind.FEATURE, p.kind());
		assertEquals("com.x.feature", p.bsn());
		assertEquals(List.of("com.x.api", "com.x.imp"), p.featurePlugins());
	}

	@Test
	void folderWithATargetFileIsATargetProject() throws IOException {
		write("x.target", "<target/>");
		Project p = ProjectReader.read("t", dir, new ArrayList<>());
		assertEquals(Kind.TARGET, p.kind());
		assertFalse(p.selected());
	}

	@Test
	void anythingElseIsOtherAndNotSelected() throws IOException {
		write("pom.xml", "<project/>");
		Project p = ProjectReader.read("parent", dir, new ArrayList<>());
		assertEquals(Kind.OTHER, p.kind());
		assertFalse(p.selected());
	}

	@Test
	void testFolderNestedUnderSourceRootIsWarned() throws IOException {
		write("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nBundle-SymbolicName: com.x\n");
		write("build.properties", "source.. = src/\nbin.includes = META-INF/,.\n");
		Files.createDirectories(dir.resolve("src/test/java"));
		List<String> warnings = new ArrayList<>();
		ProjectReader.read("x", dir, warnings);
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).contains("src_test/"), warnings.get(0));
	}
}
