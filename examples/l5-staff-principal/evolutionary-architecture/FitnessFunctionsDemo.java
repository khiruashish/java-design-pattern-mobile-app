import java.util.List;
import java.util.function.Predicate;

/**
 * Fitness functions: executable checks that an architectural characteristic still holds (latency, coupling, size,
 * dependency rules). They run in CI (triggered) or in production (continual), so architecture can't silently drift.
 */
public class FitnessFunctionsDemo {

    record Snapshot(int checkoutP95Ms, int dependencyCycles, int largestModuleKloc, double testCoverage, int criticalCves) {}
    record FitnessFunction(String name, String kind, Predicate<Snapshot> holds, String threshold) {}   // (1)!

    static final List<FitnessFunction> SUITE = List.of(
            new FitnessFunction("checkout p95 latency", "continual, holistic", s -> s.checkoutP95Ms() <= 300, "<= 300 ms"),
            new FitnessFunction("no module cycles", "triggered, atomic", s -> s.dependencyCycles() == 0, "0 cycles"),
            new FitnessFunction("module size", "triggered, atomic", s -> s.largestModuleKloc() <= 40, "<= 40 kloc"),
            new FitnessFunction("domain test coverage", "triggered, atomic", s -> s.testCoverage() >= 0.80, ">= 80%"),
            new FitnessFunction("no critical CVEs", "triggered + nightly", s -> s.criticalCves() == 0, "0"));

    static boolean evaluate(String label, Snapshot s) {
        System.out.println(label);
        boolean allPass = true;
        for (FitnessFunction f : SUITE) {
            boolean ok = f.holds().test(s);                                                            // (2)!
            allPass &= ok;
            System.out.printf("  %-4s %-22s %-20s (%s)%n", ok ? "ok" : "FAIL", f.name(), f.threshold(), f.kind());
        }
        return allPass;
    }

    public static void main(String[] args) {
        evaluate("release 41:", new Snapshot(240, 0, 31, 0.84, 0));
        boolean ok = evaluate("release 42 candidate:", new Snapshot(260, 1, 44, 0.83, 0));
        System.out.println("release 42 " + (ok ? "can ship" : "blocked until the fitness functions pass"));   // (3)!
    }
}
