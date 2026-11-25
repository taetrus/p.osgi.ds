package com.kk.pde.ds.webconsole.terminal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Makes and checks the random secret of the terminal endpoints. */
public final class Token {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Token() {
    }

    /** Returns 32 hex characters (16 random bytes). */
    public static String generate() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        char[] out = new char[32];
        for (int i = 0; i < 16; i++) {
            out[2 * i] = HEX[(bytes[i] >> 4) & 0xf];
            out[2 * i + 1] = HEX[bytes[i] & 0xf];
        }
        return new String(out);
    }

    /** Compares in constant time. A null or empty value never matches. */
    public static boolean matches(String expected, String given) {
        if (expected == null || given == null || expected.isEmpty() || given.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                given.getBytes(StandardCharsets.UTF_8));
    }
}
