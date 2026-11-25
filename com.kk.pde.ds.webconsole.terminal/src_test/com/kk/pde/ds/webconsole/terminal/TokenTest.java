package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * <p>
 * {@link Token} makes and checks the random secret that protects the terminal
 * endpoints. {@code generate} gives 32 hex characters (16 random bytes).
 * {@code matches} compares in constant time and refuses a null or an empty value.
 * </p>
 */
class TokenTest {

    @Test
    void generatesThirtyTwoHexCharacters() {
        assertTrue(Token.generate().matches("[0-9a-f]{32}"));
    }

    @Test
    void twoTokensDiffer() {
        assertNotEquals(Token.generate(), Token.generate());
    }

    @Test
    void matchesEqual() {
        assertTrue(Token.matches("abc", "abc"));
    }

    @Test
    void rejectsDifferentNullAndEmpty() {
        assertFalse(Token.matches("abc", "abd"));
        assertFalse(Token.matches("abc", null));
        assertFalse(Token.matches(null, "abc"));
        assertFalse(Token.matches("", ""));
        assertFalse(Token.matches("abc", ""));
    }
}
