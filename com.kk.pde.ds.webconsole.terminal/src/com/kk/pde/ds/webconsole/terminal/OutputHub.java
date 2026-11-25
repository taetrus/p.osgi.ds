package com.kk.pde.ds.webconsole.terminal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded ring buffer of numbered output lines. A writer never waits for a reader.
 * When the buffer is full, the oldest line is dropped.
 */
public final class OutputHub {

    /** One numbered line. The type is "out", "err", "cmd" or "done". */
    public static final class Line {
        public final long seq;
        public final String type;
        public final String text;

        Line(long seq, String type, String text) {
            this.seq = seq;
            this.type = type;
            this.text = text;
        }
    }

    /** Lines after a sequence number, and the count of lines that are no longer in the buffer. */
    public static final class Batch {
        public final long dropped;
        public final List<Line> lines;

        Batch(long dropped, List<Line> lines) {
            this.dropped = dropped;
            this.lines = lines;
        }
    }

    private final Object lock = new Object();
    private final ArrayDeque<Line> buffer = new ArrayDeque<Line>();
    private final int capacity;
    private long lastSeq;

    public OutputHub(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
    }

    /** Adds a line and returns its sequence number. The first number is 1. */
    public long append(String type, String text) {
        synchronized (lock) {
            long seq = ++lastSeq;
            if (buffer.size() == capacity) {
                buffer.removeFirst();
            }
            buffer.addLast(new Line(seq, type, text));
            lock.notifyAll();
            return seq;
        }
    }

    /** Returns the lines with a sequence number greater than afterSeq. */
    public Batch since(long afterSeq) {
        synchronized (lock) {
            return snapshot(afterSeq);
        }
    }

    /** As {@link #since}, but waits for a new line or for the timeout. */
    public Batch await(long afterSeq, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        synchronized (lock) {
            while (lastSeq <= afterSeq) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                long millis = remaining / 1_000_000L;
                int nanos = (int) (remaining % 1_000_000L);
                lock.wait(millis, nanos);
            }
            return snapshot(afterSeq);
        }
    }

    /** The maximum number of lines in the buffer. */
    public int capacity() {
        return capacity;
    }

    public long lastSeq() {
        synchronized (lock) {
            return lastSeq;
        }
    }

    // The caller holds the lock.
    private Batch snapshot(long afterSeq) {
        List<Line> lines = new ArrayList<Line>();
        for (Line line : buffer) {
            if (line.seq > afterSeq) {
                lines.add(line);
            }
        }
        long dropped = 0;
        if (!buffer.isEmpty()) {
            long oldest = buffer.peekFirst().seq;
            dropped = Math.max(0, oldest - 1 - afterSeq);
        }
        return new Batch(dropped, lines);
    }
}
