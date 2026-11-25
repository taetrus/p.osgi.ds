package com.kk.pde.ds.webconsole.terminal;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * Changes the bytes of a stream into complete lines. It splits on each line feed and removes
 * one carriage return before it. A line longer than {@link #MAX_LINE} characters goes out in
 * parts. The bytes of a character can come in different writes.
 */
final class LineAssembler {

    /** The maximum length of one line, in characters. */
    static final int MAX_LINE = 8192;

    /** Gets each complete line, without the line end. */
    interface Sink {
        void line(String text);
    }

    private final CharsetDecoder decoder;
    private final Sink sink;
    private final StringBuilder line = new StringBuilder();
    private final CharBuffer chars = CharBuffer.allocate(1024);
    private byte[] carry = new byte[0];

    LineAssembler(Charset charset, Sink sink) {
        this.decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.sink = sink;
    }

    synchronized void write(byte[] b, int off, int len) {
        ByteBuffer in;
        if (carry.length == 0) {
            in = ByteBuffer.wrap(b, off, len);
        } else {
            in = ByteBuffer.allocate(carry.length + len);
            in.put(carry).put(b, off, len);
            in.flip();
        }
        while (true) {
            CoderResult result = decoder.decode(in, chars, false);
            chars.flip();
            while (chars.hasRemaining()) {
                accept(chars.get());
            }
            chars.clear();
            if (result.isUnderflow()) {
                break;
            }
        }
        // The bytes of an incomplete character wait for the next write.
        carry = new byte[in.remaining()];
        in.get(carry);
    }

    /** Gives the text with no line end, if there is one. */
    synchronized void flushPartial() {
        if (line.length() > 0) {
            emit();
        }
    }

    private void accept(char c) {
        if (c == '\n') {
            int last = line.length() - 1;
            if (last >= 0 && line.charAt(last) == '\r') {
                line.setLength(last);
            }
            emit();
            return;
        }
        if (line.length() == MAX_LINE) {
            emit();
        }
        line.append(c);
    }

    private void emit() {
        String text = line.toString();
        line.setLength(0);
        sink.line(text);
    }
}
