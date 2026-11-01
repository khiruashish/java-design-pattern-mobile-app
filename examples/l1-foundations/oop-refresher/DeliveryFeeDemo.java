import java.util.List;

public class DeliveryFeeDemo {

    /** How FreshCart prices delivery. Orders depend on this abstraction, not on concrete rules. */
    interface DeliveryFeePolicy {
        long feeInCents(long basketTotalInCents);
        String name();
    }

    record FlatFee(long feeInCents) implements DeliveryFeePolicy {
        public long feeInCents(long basketTotalInCents) { return feeInCents; }
        public String name() { return "flat"; }
    }

    record FreeOverThreshold(long thresholdInCents, DeliveryFeePolicy otherwise) implements DeliveryFeePolicy { // (1)!
        public long feeInCents(long basketTotalInCents) {
            return basketTotalInCents >= thresholdInCents ? 0 : otherwise.feeInCents(basketTotalInCents);
        }
        public String name() { return "free over " + DeliveryFeeDemo.format(thresholdInCents); }
    }

    record ExpressSurcharge(DeliveryFeePolicy base, long surchargeInCents) implements DeliveryFeePolicy {
        public long feeInCents(long basketTotalInCents) {
            return base.feeInCents(basketTotalInCents) + surchargeInCents;
        }
        public String name() { return base.name() + " + express"; }
    }

    /** An order HAS-A fee policy (composition) instead of IS-A StandardOrder / ExpressOrder (inheritance). */
    record Order(String id, long basketTotalInCents, DeliveryFeePolicy feePolicy) { // (2)!
        long totalInCents() {
            return basketTotalInCents + feePolicy.feeInCents(basketTotalInCents);
        }
    }

    static String format(long cents) {
        return "$%d.%02d".formatted(cents / 100, cents % 100);
    }

    public static void main(String[] args) {
        DeliveryFeePolicy standard = new FreeOverThreshold(5_000, new FlatFee(499));
        DeliveryFeePolicy express = new ExpressSurcharge(standard, 300);  // (3)!

        List<Order> orders = List.of(
                new Order("FC-1001", 3_250, standard),
                new Order("FC-1002", 7_820, standard),
                new Order("FC-1003", 7_820, express));

        for (Order order : orders) {
            System.out.printf("%s  basket %-7s fee %-6s (%s)  total %s%n",
                    order.id(),
                    format(order.basketTotalInCents()),
                    format(order.feePolicy().feeInCents(order.basketTotalInCents())),
                    order.feePolicy().name(),
                    format(order.totalInCents()));
        }
    }
}
