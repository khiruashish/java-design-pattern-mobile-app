package com.freshcart.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Runs Resilience4jDemo and compares its output with expected/Resilience4jDemo.expected.txt (shown on the docs page). */
class GoldenOutputTest {

    @Test
    void outputMatchesTheDocs() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Resilience4jDemo.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        assertEquals(Files.readString(Path.of("expected", "Resilience4jDemo.expected.txt")), captured.toString(StandardCharsets.UTF_8));
    }
}
