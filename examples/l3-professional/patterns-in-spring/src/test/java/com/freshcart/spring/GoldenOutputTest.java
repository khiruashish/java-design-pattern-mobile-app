package com.freshcart.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs every *Demo main method and compares its output with expected/<Demo>.expected.txt (shown on the docs page). */
class GoldenOutputTest {

    static Stream<String> demos() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("expected"))) {
            return files.map(p -> p.getFileName().toString().replace(".expected.txt", "")).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("demos")
    void outputMatchesTheDocs(String demo) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Class.forName("com.freshcart.spring." + demo).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(original);
        }
        String expected = Files.readString(Path.of("expected", demo + ".expected.txt"));
        assertEquals(expected, captured.toString(StandardCharsets.UTF_8));
    }
}
