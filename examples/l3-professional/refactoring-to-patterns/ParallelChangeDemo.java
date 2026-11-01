import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * PayLink to PayLink v2 migration with Parallel Change (expand, migrate, contract): both implementations sit behind
 * one interface; a deterministic percentage rollout routes traffic; the old path runs as control and the new one is
 * compared in shadow mode (a "Scientist"-style experiment); a kill switch reverts instantly.
 */
public class ParallelChangeDemo {

    record Quote(long feePaise, String currency) {}

    interface FeeCalculator { Quote fee(String orderId, long amountPaise); }        // (1)!

    /** Old implementation: flat 2% with a Rs 3 minimum. */
    static final class LegacyFees implements FeeCalculator {
        public Quote fee(String orderId, long amount) { return new Quote(Math.max(amount * 2 / 100, 300), "INR"); }
    }
    /** New implementation: should match, but rounds half up where the legacy code truncates. */
    static final class V2Fees implements FeeCalculator {
        public Quote fee(String orderId, long amount) {
            long fee = Math.round(amount * 0.02);
            return new Quote(Math.max(fee, 300), "INR");
        }
    }

    /** Runs control, optionally the candidate, compares, and never lets the candidate hurt the caller. */
    static final class Experiment<T> {                                              // (2)!
        int compared, mismatched, candidateErrors;
        final List<String> mismatches = new ArrayList<>();
        T run(String key, Supplier<T> control, Supplier<T> candidate) {
            T expected = control.get();
            try {
                T actual = candidate.get();
                compared++;
                if (!Objects.equals(expected, actual)) {
                    mismatched++;
                    if (mismatches.size() < 3) mismatches.add(key + ": " + expected + " vs " + actual);
                }
            } catch (RuntimeException e) {
                candidateErrors++;
            }
            return expected;
        }
    }

    record Rollout(int percent, boolean killSwitch) {
        Rollout { if (percent < 0 || percent > 100) throw new IllegalArgumentException("percent must be 0..100"); }
        boolean useNew(String orderId) {                                            // (3)!
            return !killSwitch && Math.floorMod(orderId.hashCode(), 100) < percent;
        }
    }

    static final class RoutingFees implements FeeCalculator {                      // (4)!
        final FeeCalculator legacy, v2; final Experiment<Quote> shadow = new Experiment<>();
        Rollout rollout; boolean shadowCompare;
        int servedByNew;
        RoutingFees(FeeCalculator legacy, FeeCalculator v2, Rollout rollout, boolean shadowCompare) {
            this.legacy = legacy; this.v2 = v2; this.rollout = rollout; this.shadowCompare = shadowCompare;
        }
        public Quote fee(String orderId, long amount) {
            if (rollout.useNew(orderId)) { servedByNew++; return v2.fee(orderId, amount); }
            if (shadowCompare) return shadow.run(orderId, () -> legacy.fee(orderId, amount), () -> v2.fee(orderId, amount));
            return legacy.fee(orderId, amount);
        }
    }

    static long amountFor(int i) { return 10_000 + (i * 7_919L) % 200_000; }

    public static void main(String[] args) {
        // Phase 1 (expand): v2 exists, 0% traffic, shadow comparison on.
        RoutingFees fees = new RoutingFees(new LegacyFees(), new V2Fees(), new Rollout(0, false), true);
        for (int i = 0; i < 1_000; i++) fees.fee("FC-" + i, amountFor(i));
        System.out.println("shadow: compared " + fees.shadow.compared + ", mismatched " + fees.shadow.mismatched + ", served by v2 " + fees.servedByNew);
        fees.shadow.mismatches.forEach(m -> System.out.println("  " + m));

        // Fix the bug, re-run shadow until clean, then migrate gradually.
        RoutingFees fixed = new RoutingFees(new LegacyFees(),
                (id, amount) -> new Quote(Math.max(amount * 2 / 100, 300), "INR"), new Rollout(0, false), true);   // (5)!
        for (int i = 0; i < 1_000; i++) fixed.fee("FC-" + i, amountFor(i));
        System.out.println("after fix: compared " + fixed.shadow.compared + ", mismatched " + fixed.shadow.mismatched);

        fixed.rollout = new Rollout(25, false);
        for (int i = 0; i < 1_000; i++) fixed.fee("FC-" + i, amountFor(i));
        System.out.println("25% rollout: served by v2 " + fixed.servedByNew + " of 1000");
        System.out.println("same order, same path: " + (fixed.rollout.useNew("FC-42") == fixed.rollout.useNew("FC-42")));

        fixed.rollout = new Rollout(25, true);
        int before = fixed.servedByNew;
        for (int i = 0; i < 1_000; i++) fixed.fee("FC-" + i, amountFor(i));
        System.out.println("kill switch on: served by v2 " + (fixed.servedByNew - before));
        // Phase 3 (contract): at 100% with no mismatches for a while, delete LegacyFees, the router and the flag.
    }
}
