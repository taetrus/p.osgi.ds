package com.kk.pde.ds.webconsole.terminal;

/**
 * Cleans one line of console output. It keeps color sequences (SGR) and tabs and
 * removes every other escape sequence and control character.
 */
public final class TextSanitizer {

    private static final char ESC = '\u001b';
    private static final String RESET = "\u001b[0m";

    private TextSanitizer() {
    }

    public static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == ESC) {
                i = skipEscape(text, i, out);
            } else {
                if (c == '\t' || (c >= 0x20 && c != 0x7f && (c < 0x80 || c > 0x9f))) {
                    out.append(c);
                }
                i++;
            }
        }
        return out.toString();
    }

    // Handles the escape at index i. Appends it to out only when it is an SGR sequence.
    // Returns the index after the sequence.
    private static int skipEscape(String s, int i, StringBuilder out) {
        int n = s.length();
        if (i + 1 >= n) {
            return n; // lone trailing ESC
        }
        char next = s.charAt(i + 1);
        if (next == '[') {
            int j = i + 2;
            while (j < n && (s.charAt(j) >= 0x30 && s.charAt(j) <= 0x3f)) {
                j++; // parameter bytes
            }
            while (j < n && (s.charAt(j) >= 0x20 && s.charAt(j) <= 0x2f)) {
                j++; // intermediate bytes
            }
            if (j >= n) {
                return n; // unfinished sequence
            }
            char fin = s.charAt(j);
            if (fin >= 0x40 && fin <= 0x7e) {
                if (fin == 'm' && onlySgrParams(s, i + 2, j)) {
                    out.append(s, i, j + 1);
                }
                return j + 1;
            }
            return j; // not a final byte: drop the sequence start only
        }
        if (next == ']') {
            int j = i + 2;
            while (j < n) {
                char c = s.charAt(j);
                if (c == 0x07) {
                    return j + 1;
                }
                if (c == ESC && j + 1 < n && s.charAt(j + 1) == '\\') {
                    return j + 2;
                }
                j++;
            }
            return n; // unterminated OSC
        }
        if (next == ESC) {
            return i + 1; // the next ESC starts its own sequence
        }
        if (next >= 0x30 && next <= 0x5f) {
            return i + 2; // two-character escape (ESC 7, ESC =, ESC M ...)
        }
        return i + 1; // lone ESC before an ordinary character: drop the ESC only
    }

    private static boolean onlySgrParams(String s, int from, int to) {
        for (int k = from; k < to; k++) {
            char c = s.charAt(k);
            if (!(c >= '0' && c <= '9') && c != ';') {
                return false;
            }
        }
        return true;
    }

    /** Wraps a plain line in red or yellow when it shows an error or a warning. */
    public static String colorByLevel(String cleanLine) {
        if (cleanLine.contains("\u001b[")) {
            return cleanLine;
        }
        if (cleanLine.contains(" ERROR ") || isEquinoxError(cleanLine)) {
            return "\u001b[31m" + cleanLine + RESET;
        }
        if (cleanLine.contains(" WARN ")) {
            return "\u001b[33m" + cleanLine + RESET;
        }
        return cleanLine;
    }

    // "!ENTRY <bundle> <severity> ...": severity 4 is an error.
    private static boolean isEquinoxError(String line) {
        if (!line.startsWith("!ENTRY ")) {
            return false;
        }
        String[] parts = line.split(" ", 4);
        return parts.length >= 3 && "4".equals(parts[2]);
    }
}
