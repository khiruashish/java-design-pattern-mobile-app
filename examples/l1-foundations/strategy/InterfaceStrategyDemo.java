import java.util.List;

/** Classic Strategy: the delivery-fee algorithm is an interface; the checkout holds one and delegates. */
public class InterfaceStrategyDemo {

    record Basket(long subtotalCents, double distanceKm) { }

    // ===================== BEFORE: an if/else chain inside checkout =====================
    static long feeBefore(String rule, Basket b) {
        if (rule.equals("flat")) return 4_900;
        else if (rule.equals("distance")) return 2_000 + Math.round(b.distanceKm() * 800);
        else if (rule.equals("free-over")) return b.subtotalCents() >= 50_000 ? 0 : 4_900;
        throw new IllegalArgumentException(rule);                                         // (1)!
    }

    // ===================== AFTER: one interface, one class per algorithm =====================
    interface DeliveryFeeStrategy { long feeCents(Basket basket); }                     // (2)!

    record FlatFee(long cents) implements DeliveryFeeStrategy {
        public long feeCents(Basket b) { return cents; }
    }
    record DistanceFee(long baseCents, long perKmCents) implements DeliveryFeeStrategy {
        public long feeCents(Basket b) { return baseCents + Math.round(b.distanceKm() * perKmCents); }
    }
    record FreeOverThreshold(long thresholdCents, DeliveryFeeStrategy otherwise) implements DeliveryFeeStrategy {   // (3)!
        public long feeCents(Basket b) { return b.subtotalCents() >= thresholdCents ? 0 : otherwise.feeCents(b); }
    }

    static final class Checkout {
        private final DeliveryFeeStrategy feeStrategy;                                    // (4)!
        Checkout(DeliveryFeeStrategy feeStrategy) { this.feeStrategy = feeStrategy; }
        long total(Basket b) { return b.subtotalCents() + feeStrategy.feeCents(b); }
    }

    public static void main(String[] args) {
        Basket small = new Basket(32_000, 3.5), big = new Basket(62_000, 3.5);
        System.out.println("before (distance): " + feeBefore("distance", small));
        List<DeliveryFeeStrategy> strategies = List.of(
                new FlatFee(4_900), new DistanceFee(2_000, 800), new FreeOverThreshold(50_000, new DistanceFee(2_000, 800)));
        for (DeliveryFeeStrategy s : strategies) {
            Checkout checkout = new Checkout(s);
            System.out.println(s + " -> small " + checkout.total(small) + ", big " + checkout.total(big));
        }
    }
}
