package com.kk.pde2tycho.gen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.kk.pde2tycho.model.Inventory;
import com.kk.pde2tycho.model.Launch;

/** run.sh (macOS/Linux, LF) and run.bat (Windows, CRLF) for the environments being built. */
final class ScriptGenerator {

	private ScriptGenerator() {
	}

	static void generate(Inventory inv, Launch launch, LauncherArgs.Rewritten args, Path dir) throws IOException {
		String program = scriptProgramArgs(args.programArgs());
		StringBuilder cases = new StringBuilder();
		if (inv.environments().stream().anyMatch(e -> e.startsWith("macosx/"))) {
			cases.append("        Darwin)\n")
					.append("            PRODUCT_DIR=\"$PRODUCTS/macosx/cocoa/$ARCH/Eclipse.app/Contents/Eclipse\"\n")
					.append("            ;;\n");
		}
		if (inv.environments().stream().anyMatch(e -> e.startsWith("linux/"))) {
			cases.append("        Linux)\n")
					.append("            PRODUCT_DIR=\"$PRODUCTS/linux/gtk/$ARCH\"\n")
					.append("            ;;\n");
		}
		if (cases.length() > 0) {
			Path sh = dir.resolve("run.sh");
			Output.write(sh, Templates.render("run.sh", Map.of("PRODUCT_ID", launch.productId(), "OS_CASES",
					cases.toString(), "VM_ARGS", args.vmArgs(), "PROGRAM_ARGS", program)));
			sh.toFile().setExecutable(true, false);
		}
		if (inv.environments().stream().anyMatch(e -> e.startsWith("win32/"))) {
			Output.write(dir.resolve("run.bat"), Templates.render("run.bat", Map.of("PRODUCT_ID", launch.productId(),
					"VM_ARGS", args.vmArgs(), "PROGRAM_ARGS", program)).replace("\n", "\r\n"));
		}
	}

	/**
	 * The scripts already pass -configuration and -console, so those are dropped from the launch's
	 * list, together with -configuration's value and -console's optional port.
	 */
	static String scriptProgramArgs(String programArgs) {
		List<String> tokens = LauncherArgs.tokenize(programArgs);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < tokens.size(); i++) {
			String token = tokens.get(i);
			if (token.equals("-configuration")) {
				i++;
			} else if (token.equals("-console")) {
				if (i + 1 < tokens.size() && tokens.get(i + 1).matches("\\d+")) {
					i++;
				}
			} else {
				out.add(token);
			}
		}
		return String.join(" ", out);
	}
}
