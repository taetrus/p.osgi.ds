package com.kk.pde.ds.webconsole.terminal;

/** Writes the Server-Sent Events text format. */
public final class SseEncoder {

    private SseEncoder() {
    }

    public static String event(long id, String type, String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("id: ").append(id).append('\n');
        sb.append("event: ").append(type).append('\n');
        for (String part : data.split("\r\n|\r|\n", -1)) {
            sb.append("data: ").append(part).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    public static String comment(String text) {
        return ": " + text + "\n\n";
    }
}
