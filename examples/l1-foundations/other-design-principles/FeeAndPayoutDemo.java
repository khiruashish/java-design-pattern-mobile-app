import java.util.List;

/**
 * Delivery fee (what the customer pays) and rider payout (what FreshCart pays the rider) both look like
 * "base + per-km rate". They are NOT the same knowledge: they change for different reasons and are
 * owned by different teams. Merging them would be "wrong DRY".
 */
public class FeeAndPayoutDemo {

    /** Owned by Pricing. Changes with marketing campaigns. */
    record DeliveryFeePolicy(long baseInCents, long perKmInCents, long freeOverInCents) {   // (1)!
        DeliveryFeePolicy {
            if (baseInCents < 0 || perKmInCents < 0 || freeOverInCents < 0) throw new IllegalArgumentException("fee settings must not be negative");
        }
        long feeInCents(double distanceKm, long basketInCents) {
            requireDistance(distanceKm);
            if (basketInCents >= freeOverInCents) return 0;
            return baseInCents + Math.round(distanceKm * perKmInCents);
        }
    }

    /** Owned by Rider Operations. Changes with labour agreements and fuel prices. */
    record RiderPayoutPolicy(long baseInCents, long perKmInCents, long minimumInCents) {      // (2)!
        RiderPayoutPolicy {
            if (baseInCents < 0 || perKmInCents < 0 || minimumInCents < 0) throw new IllegalArgumentException("payout settings must not be negative");
        }
        long payoutInCents(double distanceKm) {
            requireDistance(distanceKm);
            return Math.max(minimumInCents, baseInCents + Math.round(distanceKm * perKmInCents));
        }
    }

    /** Genuinely shared knowledge: what a valid delivery distance is. Defined once. */
    static void requireDistance(double km) {                                                 // (3)!
        if (Double.isNaN(km) || km <= 0 || km > 25) throw new IllegalArgumentException("distance must be in (0, 25] km: " + km);
    }

    record Delivery(String orderId, double distanceKm, long basketInCents) { }

    static String money(long c) { return "$%d.%02d".formatted(c / 100, c % 100); }

    public static void main(String[] args) {
        DeliveryFeePolicy fee = new DeliveryFeePolicy(199, 60, 5_000);
        RiderPayoutPolicy payout = new RiderPayoutPolicy(150, 45, 300);

        for (Delivery d : List.of(new Delivery("FC-7001", 2.0, 2_400), new Delivery("FC-7002", 6.5, 2_400),
                                  new Delivery("FC-7003", 6.5, 7_200), new Delivery("FC-7004", 40.0, 2_400))) {
            try {
                long f = fee.feeInCents(d.distanceKm(), d.basketInCents());
                long p = payout.payoutInCents(d.distanceKm());
                System.out.printf("%s %4.1f km  fee %-6s payout %-6s margin %s%n",
                        d.orderId(), d.distanceKm(), money(f), money(p), (f - p < 0 ? "-" : "") + money(Math.abs(f - p)));
            } catch (IllegalArgumentException e) {
                System.out.println(d.orderId() + " rejected: " + e.getMessage());
            }
        }
    }
}
