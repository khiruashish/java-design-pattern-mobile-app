import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeRuleEngineTest {

    private final SafeRuleEngineDemo.RuleEngine engine =
            new SafeRuleEngineDemo.RuleEngine(Map.of("total", Long.class, "tier", String.class));
    private final Map<String, Object> gold500 = Map.of("total", 500L, "tier", "gold");

    private boolean matches(String rule) { return engine.matches(rule, gold500, new ArrayList<>()); }

    @Test
    void andBindsTighterThanOr() {
        assertTrue(matches("tier == \"silver\" and total > 1000 or total == 500"));
        assertFalse(matches("tier == \"silver\" and (total > 1000 or total == 500)"));
    }

    @Test
    void notAndComparisons() {
        assertTrue(matches("not total < 100"));
        assertTrue(matches("total >= 500 and total <= 500"));
    }

    @Test
    void typeErrorsAreCaughtBeforeEvaluation() {
        assertThrows(IllegalArgumentException.class, () -> engine.compile("total == \"500\""));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("tier > \"a\""));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("unknown == 1"));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("total"));
    }

    @Test
    void limitsProtectAgainstHugeOrDeepRules() {
        assertThrows(IllegalArgumentException.class, () -> engine.compile("(".repeat(20) + "total == 1" + ")".repeat(20)));
        assertThrows(IllegalArgumentException.class, () -> engine.compile("total == 1 or ".repeat(100) + "total == 1"));
    }

    @Test
    void compiledRulesAreCached() {
        matches("total > 1");
        matches("total > 1");
        assertEquals(1, engine.compilations);
    }
}
