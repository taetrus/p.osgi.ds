package com.kk.pde2tycho.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Turns a PDE launch's VM/program arguments into product and run-script arguments. */
final class LauncherArgs {

	/** Arguments for the product; {@code vmArgsMac} is empty when there are none. */
	record Rewritten(String vmArgs, String vmArgsMac, String programArgs) {
	}

	/** PDE passes the target environment to the launched runtime; a product has its own. */
	private static final Set<String> PDE_ENVIRONMENT_OPTIONS = Set.of("-os", "-ws", "-arch", "-nl");

	private LauncherArgs() {
	}

	static Rewritten rewrite(String vm, String program, List<String> notes) {
		List<String> vmOut = new ArrayList<>();
		List<String> mac = new ArrayList<>();
		for (String token : tokenize(vm)) {
			if (token.contains("${")) {
				notes.add("Dropped VM argument " + token + " (an Eclipse variable; meaningless outside the IDE)");
			} else if (token.equals("-XstartOnFirstThread")) {
				mac.add(token);
				notes.add("-XstartOnFirstThread moved to the product's macOS-only VM arguments and left out of run.sh:"
						+ " it is only needed for SWT, and blocks AWT/Swing under java -jar");
			} else {
				vmOut.add(token);
				if (token.contains("configuration/")) {
					notes.add("VM argument " + token + " refers to a file under configuration/: put that file in"
							+ " distribution/configuration/ and the build copies it into every product");
				}
			}
		}
		List<String> programOut = new ArrayList<>();
		List<String> tokens = tokenize(program);
		for (int i = 0; i < tokens.size(); i++) {
			String token = tokens.get(i);
			if (PDE_ENVIRONMENT_OPTIONS.contains(token)) {
				notes.add("Dropped program argument " + token + " " + (i + 1 < tokens.size() ? tokens.get(i + 1) : "")
						+ " (PDE launch setting)");
				i++;
			} else if (token.contains("${")) {
				notes.add("Dropped program argument " + token + " (an Eclipse variable)");
			} else {
				programOut.add(token);
			}
		}
		return new Rewritten(String.join(" ", vmOut), String.join(" ", mac), String.join(" ", programOut));
	}

	/** Splits on whitespace outside double quotes; quotes stay in the token. */
	static List<String> tokenize(String text) {
		List<String> out = new ArrayList<>();
		if (text == null) {
			return out;
		}
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (char c : text.toCharArray()) {
			if (c == '"') {
				quoted = !quoted;
				current.append(c);
			} else if (Character.isWhitespace(c) && !quoted) {
				if (current.length() > 0) {
					out.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (current.length() > 0) {
			out.add(current.toString());
		}
		return out;
	}
}
