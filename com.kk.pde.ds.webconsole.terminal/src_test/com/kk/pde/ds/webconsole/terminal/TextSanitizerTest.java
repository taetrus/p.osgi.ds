package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * <p>
 * {@link TextSanitizer} cleans one line of console output before the browser shows it.
 * It keeps color sequences (SGR) and tabs. It removes every other escape sequence and
 * control character, so a command cannot move the cursor or set the window title.
 * {@code colorByLevel} then adds a color to a plain line that shows an error or a
 * warning.
 * </p>
 * <p>
 * These tests give a dirty string and check the clean result. They also check that a
 * line that already has a color is not changed again.
 * </p>
 */
class TextSanitizerTest {

    private static final String RED = "\u001b[31m";
    private static final String YELLOW = "\u001b[33m";
    private static final String RESET = "\u001b[0m";

    @Test
    void keepsPlainTextAndTab() {
        assertEquals("a\tb", TextSanitizer.clean("a\tb"));
    }

    @Test
    void keepsSgrColor() {
        String s = "\u001b[31mred\u001b[0m";
        assertEquals(s, TextSanitizer.clean(s));
    }

    @Test
    void removesCursorAndClearSequences() {
        assertEquals("abc", TextSanitizer.clean("a\u001b[2Jb\u001b[10;5Hc"));
    }

    @Test
    void removesOscTitle() {
        assertEquals("x", TextSanitizer.clean("\u001b]0;evil\u0007x"));
    }

    @Test
    void removesOtherC0() {
        assertEquals("ab", TextSanitizer.clean("a\u0007\u0008b\u0000"));
    }

    @Test
    void removesLoneEscape() {
        assertEquals("ab", TextSanitizer.clean("a\u001bb"));
    }

    @Test
    void removesC1Controls() {
        assertEquals("a2Jb0;tcd", TextSanitizer.clean("a\u009b2Jb\u009d0;t\u0007c\u0090d"));
    }

    @Test
    void colorByLevelMarksErrorRed() {
        String r = TextSanitizer.colorByLevel("12:00 ERROR boom");
        assertTrue(r.startsWith(RED));
        assertTrue(r.endsWith(RESET));
    }

    @Test
    void colorByLevelMarksWarnYellow() {
        String r = TextSanitizer.colorByLevel("12:00 WARN careful");
        assertTrue(r.startsWith(YELLOW));
        assertTrue(r.endsWith(RESET));
    }

    @Test
    void colorByLevelMarksEquinoxErrorEntry() {
        String r = TextSanitizer.colorByLevel("!ENTRY org.example 4 0 2026-11-25 12:00:00.000");
        assertTrue(r.startsWith(RED));
        assertTrue(r.endsWith(RESET));
    }

    @Test
    void colorByLevelLeavesColoredLine() {
        String s = "\u001b[32mgreen ERROR text\u001b[0m";
        assertEquals(s, TextSanitizer.colorByLevel(s));
    }

    @Test
    void colorByLevelLeavesInfoLine() {
        String s = "12:00 INFO all good";
        assertEquals(s, TextSanitizer.colorByLevel(s));
    }
}
