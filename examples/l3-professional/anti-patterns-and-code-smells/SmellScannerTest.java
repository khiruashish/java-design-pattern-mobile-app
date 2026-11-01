import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SmellScannerTest {

    static final SmellScannerDemo.Thresholds T = new SmellScannerDemo.Thresholds(10, 3, 5, 10);

    static List<String> rules(String source) {
        return SmellScannerDemo.scan("X.java", source, T).stream().map(SmellScannerDemo.Finding::rule).toList();
    }

    @Test
    void cleanCodeHasNoFindings() {
        assertEquals(List.of(), rules("""
                class Clean {
                    private final int a;
                    int twice(int x) {
                        return x * 2;
                    }
                }
                """));
    }

    @Test
    void detectsLongMethodAndParameterList() {
        StringBuilder body = new StringBuilder("class X {\n    void work(int a, int b, int c, int d) {\n");
        for (int i = 0; i < 12; i++) body.append("        a++;\n");
        body.append("    }\n}\n");
        assertEquals(List.of("LongParameterList", "LongMethod"), rules(body.toString()));
    }

    @Test
    void emptyCatchIsFoundButHandledCatchIsNot() {
        assertEquals(List.of("EmptyCatch"), rules("class X {\n    void a() {\n        try { f(); } catch (Exception e) { }\n    }\n}\n"));
        assertEquals(List.of(), rules("class X {\n    void a() {\n        try { f(); } catch (Exception e) { log(e); }\n    }\n}\n"));
    }

    @Test
    void gateFailsOnlyOnNewFindingsAtOrAboveTheThreshold() {
        var legacy = new SmellScannerDemo.Finding("A.java", "GodClass", SmellScannerDemo.Severity.CRITICAL, "x");
        var info = new SmellScannerDemo.Finding("B.java", "Singletonitis", SmellScannerDemo.Severity.INFO, "y");
        assertTrue(SmellScannerDemo.gate(List.of(legacy, info), Set.of(legacy.key()), SmellScannerDemo.Severity.MAJOR).pass());
        assertFalse(SmellScannerDemo.gate(List.of(legacy), Set.of(), SmellScannerDemo.Severity.MAJOR).pass());
    }

    @Test
    void gateReportsFixedBaselineEntriesSoTheBaselineCanShrink() {
        var result = SmellScannerDemo.gate(List.of(), Set.of("Old.java:LongMethod:m"), SmellScannerDemo.Severity.MAJOR);
        assertEquals(List.of("Old.java:LongMethod:m"), result.fixedSinceBaseline());
    }

    @Test
    void thresholdsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new SmellScannerDemo.Thresholds(1, 1, 1, 1));
    }

    @Test
    void refactoredPayoutMatchesOriginalAtBoundaries() {
        for (int rating : new int[] {46, 47}) for (int d : new int[] {9, 10}) {
            long before = RiderPayoutRefactorDemo.payoutBefore(d, 0, false, false, 100, rating, true);
            long after = new RiderPayoutRefactorDemo.ShiftSummary(d, 0, 100,
                    new RiderPayoutRefactorDemo.ShiftConditions(false, false), new RiderPayoutRefactorDemo.RiderProfile(rating, true)).payout();
            assertEquals(before, after);
        }
    }
}
