import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * DairyDay's CI performance gate for the pricing hot path. Absolute timings differ between laptops and CI machines,
 * so the gate compares each candidate with a baseline measured in the same JVM run, uses medians over many rounds,
 * guards against dead-code elimination with a sink, and reports INCONCLUSIVE instead of failing when the
 * measurements are too noisy. The clock is injectable so the gate's own logic is unit-testable.
 */
public class PerfBudgetGateDemo {

    enum Verdict { PASS, FAIL, INCONCLUSIVE }
    record Stats(double minNanos, double medianNanos, double p90Nanos, double cv) {}
    record Budget(String candidate, String baseline, double maxRatio) {
        Budget { if (maxRatio < 1.0) throw new IllegalArgumentException("maxRatio below 1.0 means the candidate must beat the baseline"); }
    }
    record Report(Map<String, Stats> stats, Map<String, Verdict> verdicts) {}

    static final class Harness {
        private final LongSupplier clock;
        private final int warmupRounds, rounds;
        volatile long sink;                                                                // (1)!
        Harness(LongSupplier clock, int warmupRounds, int rounds) { this.clock = clock; this.warmupRounds = warmupRounds; this.rounds = rounds; }

        Stats measure(LongSupplier work) {
            for (int i = 0; i < warmupRounds; i++) sink += work.getAsLong();               // (2)!
            double[] samples = new double[rounds];
            for (int i = 0; i < rounds; i++) {
                long t = clock.getAsLong();
                sink += work.getAsLong();
                samples[i] = clock.getAsLong() - t;
            }
            Arrays.sort(samples);
            double mean = Arrays.stream(samples).average().orElse(0);
            double sd = Math.sqrt(Arrays.stream(samples).map(s -> (s - mean) * (s - mean)).average().orElse(0));
            return new Stats(samples[0], samples[rounds / 2], samples[(int) Math.ceil(rounds * 0.9) - 1], mean == 0 ? 0 : sd / mean);   // (3)!
        }
    }

    static Report evaluate(Harness h, Map<String, LongSupplier> benchmarks, List<Budget> budgets, double maxCv) {
        Map<String, Stats> stats = new LinkedHashMap<>();
        benchmarks.forEach((name, work) -> stats.put(name, h.measure(work)));
        Map<String, Verdict> verdicts = new LinkedHashMap<>();
        for (Budget b : budgets) {
            Stats c = stats.get(b.candidate()), base = stats.get(b.baseline());
            if (c.minNanos() / base.p90Nanos() > b.maxRatio()) { verdicts.put(b.candidate(), Verdict.FAIL); continue; }   // (4)!
            if (c.cv() > maxCv || base.cv() > maxCv) { verdicts.put(b.candidate(), Verdict.INCONCLUSIVE); continue; }
            double ratio = c.medianNanos() / base.medianNanos();
            verdicts.put(b.candidate(), ratio <= b.maxRatio() ? Verdict.PASS : Verdict.FAIL);                         // (5)!
        }
        return new Report(stats, verdicts);
    }

    // ---------- The code under test ----------
    interface Price { long of(long paise); }
    record Base() implements Price { public long of(long p) { return p; } }
    record Gst(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v + v * 5 / 100; } }
    record Member(Price inner) implements Price { public long of(long p) { long v = inner.of(p); return v - v / 20; } }
    static long flat(long p) { long v = p + p * 5 / 100; return v - v / 20; }

    public static void main(String[] args) {
        long[] lines = new long[200_000];
        for (int i = 0; i < lines.length; i++) lines[i] = 2_000 + (i * 37L) % 98_000;
        Price chain = new Member(new Gst(new Base()));
        Price viaStrings = p -> Long.parseLong(String.format("%d", chain.of(p)));      // a regression someone "simplified" in

        Map<String, LongSupplier> benches = new LinkedHashMap<>();
        benches.put("flat", () -> { long s = 0; for (long l : lines) s += flat(l); return s; });
        benches.put("decorator", () -> { long s = 0; for (long l : lines) s += chain.of(l); return s; });
        benches.put("stringly", () -> { long s = 0; for (long l : lines) s += viaStrings.of(l); return s; });

        List<Budget> budgets = List.of(new Budget("decorator", "flat", 3.0), new Budget("stringly", "flat", 3.0));
        Report report = evaluate(new Harness(System::nanoTime, 30, 25), benches, budgets, 0.5);

        report.stats().forEach((k, s) -> System.err.printf("%-10s median %8.0f ns, p90 %8.0f ns, cv %.2f%n", k, s.medianNanos(), s.p90Nanos(), s.cv()));
        System.out.println("budgets: decorator <= 3.0x flat, stringly <= 3.0x flat");
        report.verdicts().forEach((k, v) -> System.out.println(k + ": " + (v == Verdict.INCONCLUSIVE ? "INCONCLUSIVE (noisy machine, rerun)" : v)));
    }
}
