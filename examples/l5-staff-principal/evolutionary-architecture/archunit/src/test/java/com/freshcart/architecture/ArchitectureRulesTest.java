package com.freshcart.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    final JavaClasses classes = ArchUnitDemo.freshcartClasses();

    @Test
    void cyclesRuleHolds() {
        ArchUnitDemo.RULES.get("no cycles between modules").check(classes);
    }

    @Test
    void controllerReachingIntoPricingInternalsIsCaught() {
        assertThrows(AssertionError.class, () -> ArchUnitDemo.RULES.get("internal packages are private to their module").check(classes));
        assertThrows(AssertionError.class, () -> ArchUnitDemo.RULES.get("layers: web -> ordering -> pricing").check(classes));
    }

    @Test
    void outputMatchesTheDocs() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            ArchUnitDemo.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        assertEquals(Files.readString(Path.of("expected", "ArchUnitDemo.expected.txt")), captured.toString(StandardCharsets.UTF_8));
    }
}
