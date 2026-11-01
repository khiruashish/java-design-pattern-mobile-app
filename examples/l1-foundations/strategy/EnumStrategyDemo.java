/** Enum strategies: a closed set of algorithms, one per constant, selectable by name from config. */
public class EnumStrategyDemo {

    enum PaymentFee {                                                                     // (1)!
        UPI { long feeCents(long amount) { return 0; } },
        CARD { long feeCents(long amount) { return Math.round(amount * 0.018); } },
        WALLET { long feeCents(long amount) { return Math.min(Math.round(amount * 0.01), 1_000); } },
        CASH_ON_DELIVERY { long feeCents(long amount) { return 2_500; } };

        abstract long feeCents(long amountCents);                                        // (2)!
    }

    enum RoundingRule {                                                                   // (3)!
        NEAREST_RUPEE(c -> Math.round(c / 100.0) * 100),
        UP_TO_RUPEE(c -> (c + 99) / 100 * 100),
        NONE(c -> c);

        private final java.util.function.LongUnaryOperator op;
        RoundingRule(java.util.function.LongUnaryOperator op) { this.op = op; }
        long round(long cents) { return op.applyAsLong(cents); }
    }

    public static void main(String[] args) {
        long amount = 74_950;
        for (PaymentFee method : PaymentFee.values()) {
            System.out.println(method + " fee on 74950: " + method.feeCents(amount));
        }
        RoundingRule rule = RoundingRule.valueOf("UP_TO_RUPEE");                            // (4)!
        System.out.println(rule + ": 74950 + 1349 -> " + rule.round(amount + PaymentFee.CARD.feeCents(amount)));
        try { PaymentFee.valueOf("CRYPTO"); } catch (IllegalArgumentException e) { System.out.println("rejected: unknown payment method CRYPTO"); }
    }
}
