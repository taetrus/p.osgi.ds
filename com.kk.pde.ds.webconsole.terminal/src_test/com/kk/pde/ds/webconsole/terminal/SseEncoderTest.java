package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * <p>
 * {@link SseEncoder} writes the text format of Server-Sent Events: lines of
 * {@code field: value}, and an empty line that ends each event. These tests compare the
 * output with the exact expected strings, because a browser parses this format strictly.
 * </p>
 */
class SseEncoderTest {

    @Test
    void encodesIdTypeAndData() {
        assertEquals("id: 7\nevent: out\ndata: hello\n\n", SseEncoder.event(7, "out", "hello"));
    }

    @Test
    void splitsEmbeddedLineBreaks() {
        assertEquals("id: 1\nevent: out\ndata: a\ndata: b\n\n", SseEncoder.event(1, "out", "a\nb"));
    }

    @Test
    void commentStartsWithColon() {
        assertEquals(": ping\n\n", SseEncoder.comment("ping"));
    }
}
