import java.util.Random;

/**
 * FreshCart rider payouts: a long parameter list and feature envy, refactored to a parameter object that owns
 * its calculation. A characterization test proves behaviour didn't change.
 */
public class RiderPayoutRefactorDemo {

    // ---- Before: seven primitives; the order of the booleans is a trap; the logic lives far from the data. ----
    static long payoutBefore(int deliveries, int km, boolean rain, boolean night, int late, int ratingTenths, boolean newRider) {   // (1)!
        long p = deliveries * 2_500L + km * 600L;
        if (rain) p += deliveries * 1_000L;
        if (night) p += deliveries * 800L;
        p -= late * 1_500L;
        if (ratingTenths >= 47) p += 20_000;
        if (newRider && deliveries >= 10) p += 15_000;
        return Math.max(p, 0);
    }

    // ---- After ----
    record ShiftConditions(boolean rain, boolean night) {}
    record RiderProfile(int ratingTenths, boolean newRider) {}
    record ShiftSummary(int deliveries, int km, int lateDeliveries, ShiftConditions conditions, RiderProfile rider) {   // (2)!
        long payout() {
            long pay = basePay() + surcharges() - latePenalty() + bonuses();
            return Math.max(pay, 0);
        }
        private long basePay() { return deliveries * 2_500L + km * 600L; }
        private long surcharges() {
            return (conditions.rain() ? deliveries * 1_000L : 0) + (conditions.night() ? deliveries * 800L : 0);
        }
        private long latePenalty() { return lateDeliveries * 1_500L; }
        private long bonuses() {
            return (rider.ratingTenths() >= 47 ? 20_000 : 0) + (rider.newRider() && deliveries >= 10 ? 15_000 : 0);
        }
    }

    public static void main(String[] args) {
        Random random = new Random(7);
        int cases = 10_000, mismatches = 0;
        for (int i = 0; i < cases; i++) {                                                // (3)!
            int d = random.nextInt(30), km = random.nextInt(80), late = random.nextInt(20), rating = 30 + random.nextInt(21);
            boolean rain = random.nextBoolean(), night = random.nextBoolean(), fresh = random.nextBoolean();
            long before = payoutBefore(d, km, rain, night, late, rating, fresh);
            long after = new ShiftSummary(d, km, late, new ShiftConditions(rain, night), new RiderProfile(rating, fresh)).payout();
            if (before != after) mismatches++;
        }
        System.out.println("characterization: " + cases + " random shifts, mismatches: " + mismatches);
        var shift = new ShiftSummary(14, 52, 1, new ShiftConditions(true, false), new RiderProfile(48, false));
        System.out.println("Bina's rainy shift: Rs " + shift.payout() / 100);
    }
}
