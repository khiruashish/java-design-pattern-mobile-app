import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

/** The gate's decisions are tested with a scripted clock, so these tests are deterministic on any machine. */
class PerfBudgetGateTest {

    /** Each measured round reads the clock twice; this clock returns 0 then the scripted duration. */
    static LongSupplier scripted(long... durations) {
        Deque<Long> readings = new ArrayDeque<>();
        for (long d : durations) { readings.add(0L); readings.add(d); }
        return readings::poll;
    }

    static PerfBudgetGateDemo.Report run(long[] baseline, long[] candidate, double maxRatio) {
        long[] all = new long[baseline.length + candidate.length];
        System.arraycopy(baseline, 0, all, 0, baseline.length);
        System.arraycopy(candidate, 0, all, baseline.length, candidate.length);
        var harness = new PerfBudgetGateDemo.Harness(scripted(all), 0, baseline.length);
        Map<String, LongSupplier> benches = new LinkedHashMap<>();
        benches.put("base", () -> 1);
        benches.put("cand", () -> 1);
        return PerfBudgetGateDemo.evaluate(harness, benches, List.of(new PerfBudgetGateDemo.Budget("cand", "base", maxRatio)), 0.5);
    }

    @Test
    void passesWithinBudget() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {150, 150, 150, 150, 150}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.PASS, r.verdicts().get("cand"));
    }

    @Test
    void failsOverBudget() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {250, 250, 250, 250, 250}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.FAIL, r.verdicts().get("cand"));
    }

    @Test
    void noisyMeasurementsNearTheBudgetAreInconclusive() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {20, 400, 30, 380, 150}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.INCONCLUSIVE, r.verdicts().get("cand"));
    }

    @Test
    void hugeRegressionsFailEvenWhenNoisy() {
        var r = run(new long[] {100, 100, 100, 100, 100}, new long[] {5_000, 90_000, 6_000, 80_000, 7_000}, 2.0);
        assertEquals(PerfBudgetGateDemo.Verdict.FAIL, r.verdicts().get("cand"));
    }

    @Test
    void statsUseMedianAndP90() {
        var r = run(new long[] {10, 20, 30, 40, 1000}, new long[] {10, 10, 10, 10, 10}, 2.0);
        var s = r.stats().get("base");
        assertEquals(30.0, s.medianNanos());
        assertEquals(1000.0, s.p90Nanos());
        assertEquals(10.0, s.minNanos());
    }

    @Test
    void budgetBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PerfBudgetGateDemo.Budget("a", "b", 0.9));
    }
}
