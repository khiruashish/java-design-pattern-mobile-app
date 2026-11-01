import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureFitnessSuiteTest {

    static final Map<String, Set<String>> ALLOWED = Map.of("web", Set.of("ordering"), "ordering", Set.of("shared"), "shared", Set.of());

    static List<String> violations(Map<String, String> sources) {
        return ArchitectureFitnessSuiteDemo.evaluate(new TreeMap<>(sources), ALLOWED).stream().map(Object::toString).toList();
    }

    @Test
    void cleanCodebaseHasNoViolations() {
        assertEquals(List.of(), violations(Map.of(
                "a", "package com.freshcart.web; import com.freshcart.ordering.OrderService;",
                "b", "package com.freshcart.ordering; import com.freshcart.shared.Money;")));
    }

    @Test
    void disallowedDependencyIsReported() {
        assertEquals(List.of("allowed-dependencies: shared -> web"),
                violations(Map.of("a", "package com.freshcart.shared; import com.freshcart.web.Controller;")));
    }

    @Test
    void importsInsideTheSameModuleAreIgnored() {
        assertEquals(List.of(), violations(Map.of("a", "package com.freshcart.ordering; import com.freshcart.ordering.internal.Mapper;")));
    }

    @Test
    void internalPackageAccessAcrossModulesIsReported() {
        var v = violations(Map.of("a", "package com.freshcart.web; import com.freshcart.ordering.internal.Mapper;"));
        assertEquals(List.of("internal-access: a imports ordering.internal"), v);
    }

    @Test
    void cycleIsReportedOnce() {
        var graph = new TreeMap<String, Set<String>>(Map.of("a", Set.of("b"), "b", Set.of("c"), "c", Set.of("a")));
        assertEquals(List.of(List.of("a", "b", "c", "a")), ArchitectureFitnessSuiteDemo.cycles(graph));
    }

    @Test
    void ratchetFailsOnlyOnNewViolations() {
        var known = List.of(new ArchitectureFitnessSuiteDemo.Violation("r", "x"));
        assertTrue(ArchitectureFitnessSuiteDemo.ratchet(known, Set.of("r: x")).startsWith("PASS"));
        var worse = List.of(new ArchitectureFitnessSuiteDemo.Violation("r", "x"), new ArchitectureFitnessSuiteDemo.Violation("r", "y"));
        assertTrue(ArchitectureFitnessSuiteDemo.ratchet(worse, Set.of("r: x")).startsWith("FAIL: 1 new"));
        assertTrue(ArchitectureFitnessSuiteDemo.ratchet(List.of(), Set.of("r: x")).contains("1 fixed"));
    }
}
