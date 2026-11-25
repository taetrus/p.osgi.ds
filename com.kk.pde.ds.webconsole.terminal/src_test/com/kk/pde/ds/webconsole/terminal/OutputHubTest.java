package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * <p>
 * {@link OutputHub} is a bounded ring buffer of numbered output lines. A writer calls
 * {@code append} and never waits for a reader. Each reader remembers the last sequence
 * number it saw and asks for the lines after it.
 * </p>
 * <p>
 * These tests prove four things: sequence numbers start at 1 and grow by one; the
 * buffer drops the oldest lines when it is full; a reader learns how many lines it
 * missed; and {@code await} wakes up when a line arrives or when the time is over.
 * </p>
 */
class OutputHubTest {

    @Test
    void appendGivesIncreasingSequenceFromOne() {
        OutputHub hub = new OutputHub(10);
        assertEquals(1, hub.append("out", "a"));
        assertEquals(2, hub.append("out", "b"));
        assertEquals(3, hub.append("out", "c"));
        assertEquals(3, hub.lastSeq());
    }

    @Test
    void sinceReturnsOnlyNewerLines() {
        OutputHub hub = new OutputHub(10);
        for (int i = 0; i < 5; i++) {
            hub.append("out", "l" + i);
        }
        OutputHub.Batch batch = hub.since(3);
        assertEquals(2, batch.lines.size());
        assertEquals(4, batch.lines.get(0).seq);
        assertEquals(5, batch.lines.get(1).seq);
        assertEquals(0, batch.dropped);
    }

    @Test
    void ringDropsOldest() {
        OutputHub hub = new OutputHub(3);
        for (int i = 0; i < 5; i++) {
            hub.append("out", "l" + i);
        }
        OutputHub.Batch batch = hub.since(0);
        assertEquals(3, batch.lines.size());
        assertEquals(3, batch.lines.get(0).seq);
        assertEquals(4, batch.lines.get(1).seq);
        assertEquals(5, batch.lines.get(2).seq);
    }

    @Test
    void sinceReportsDroppedLines() {
        OutputHub hub = new OutputHub(3);
        for (int i = 0; i < 10; i++) {
            hub.append("out", "l" + i);
        }
        OutputHub.Batch batch = hub.since(2);
        assertEquals(5, batch.dropped);
        assertEquals(3, batch.lines.size());
        assertEquals(8, batch.lines.get(0).seq);
        assertEquals(10, batch.lines.get(2).seq);
    }

    @Test
    void awaitReturnsWhenALineArrives() throws Exception {
        OutputHub hub = new OutputHub(10);
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
            hub.append("out", "late");
        });
        long start = System.nanoTime();
        writer.start();
        OutputHub.Batch batch = hub.await(0, 2000);
        long millis = (System.nanoTime() - start) / 1_000_000;
        writer.join();
        assertEquals(1, batch.lines.size());
        assertTrue(millis < 1000, "took " + millis + " ms");
    }

    @Test
    void awaitTimesOutWithEmptyBatch() throws Exception {
        OutputHub hub = new OutputHub(10);
        OutputHub.Batch batch = hub.await(0, 50);
        assertTrue(batch.lines.isEmpty());
        assertEquals(0, batch.dropped);
    }

    @Test
    void appendNeverBlocksAndDropsOldest() {
        OutputHub hub = new OutputHub(100);
        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            hub.append("out", "x");
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis < 2000, "took " + millis + " ms");
        assertEquals(100, hub.since(0).lines.size());
    }
}
