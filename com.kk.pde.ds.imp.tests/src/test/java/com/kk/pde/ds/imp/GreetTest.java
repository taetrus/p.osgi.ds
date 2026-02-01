package com.kk.pde.ds.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

public class GreetTest {

    @Test
    void testGreetOutputsHelloWorld() {
        Greet greet = new Greet();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos));
        try {
            greet.greet();
        } finally {
            System.setOut(original);
        }
        assertEquals("Hello world!\n", baos.toString());
    }
}
