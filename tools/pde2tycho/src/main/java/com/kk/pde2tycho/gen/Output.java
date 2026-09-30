package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes generated text files, creating parent directories. */
final class Output {

	private Output() {
	}

	static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}
}
