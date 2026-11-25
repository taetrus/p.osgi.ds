package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * <p>
 * {@link LineAssembler} changes the bytes of a stream into complete lines. It decodes the
 * bytes with a charset, splits the text on each line feed and removes one carriage return
 * before the line feed. It keeps the text after the last line feed until the next write
 * or until {@code flushPartial} gives it out (for example the prompt {@code osgi> }).
 * </p>
 * <p>
 * These tests write bytes and collect the lines that the sink gets. They check the split,
 * a character whose bytes come in two writes, and the split of a very long line.
 * </p>
 */
class LineAssemblerTest {

    private final List<String> lines = new ArrayList<String>();
    private final LineAssembler assembler = new LineAssembler(StandardCharsets.UTF_8, lines::add);

    private void write(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        assembler.write(b, 0, b.length);
    }

    @Test
    void emitsOneLinePerLineFeed() {
        write("a\nb\n");
        assertEquals(Arrays.asList("a", "b"), lines);
    }

    @Test
    void removesCarriageReturnBeforeLineFeed() {
        write("a\r\n");
        assertEquals(Collections.singletonList("a"), lines);
    }

    @Test
    void keepsPartialLineUntilLineFeed() {
        write("ab");
        assertEquals(Collections.emptyList(), lines);
        write("c\n");
        assertEquals(Collections.singletonList("abc"), lines);
    }

    @Test
    void flushPartialEmitsTextWithoutLineEnd() {
        write("osgi> ");
        assembler.flushPartial();
        assertEquals(Collections.singletonList("osgi> "), lines);
        assembler.flushPartial();
        assertEquals(Collections.singletonList("osgi> "), lines);
    }

    @Test
    void decodesCharacterSplitAcrossWrites() {
        byte[] b = "ğ\n".getBytes(StandardCharsets.UTF_8);
        assembler.write(b, 0, 1);
        assembler.write(b, 1, b.length - 1);
        assertEquals(Collections.singletonList("ğ"), lines);
    }

    @Test
    void splitsLineLongerThanLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            sb.append('x');
        }
        write(sb.append('\n').toString());
        assertEquals(3, lines.size());
        assertEquals(8192, lines.get(0).length());
        assertEquals(8192, lines.get(1).length());
        assertEquals(3616, lines.get(2).length());
    }
}
