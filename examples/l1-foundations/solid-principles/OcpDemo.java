import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OCP: payment methods are added by registering a new rule, never by editing the checkout. */
public class OcpDemo {

    // ===================== BEFORE: closed for extension, open for modification =====================
    static long surchargeBefore(String method, long amountInCents) {                     // (1)!
        return switch (method) {
            case "CARD" -> Math.round(amountInCents * 0.015);
            case "WALLET" -> 0;
            case "COD" -> 99;
            default -> throw new IllegalArgumentException("unsupported payment method " + method);
        };
    }

    // ===================== AFTER: open for extension, closed for modification =====================
    interface PaymentMethod {                                                             // (2)!
        String code();
        long surchargeInCents(long amountInCents);
        default boolean supports(long amountInCents) { return true; }
    }

    record Card(double feeRate) implements PaymentMethod {
        public String code() { return "CARD"; }
        public long surchargeInCents(long amount) { return Math.round(amount * feeRate); }
    }
    record Wallet() implements PaymentMethod {
        public String code() { return "WALLET"; }
        public long surchargeInCents(long amount) { return 0; }
    }
    record CashOnDelivery(long feeInCents, long maxOrderInCents) implements PaymentMethod {
        public String code() { return "COD"; }
        public long surchargeInCents(long amount) { return feeInCents; }
        @Override public boolean supports(long amount) { return amount <= maxOrderInCents; }   // (3)!
    }
    /** Added for the India launch. Nothing above this line changed. */
    record Upi() implements PaymentMethod {                                               // (4)!
        public String code() { return "UPI"; }
        public long surchargeInCents(long amount) { return 0; }
    }

    /** The closed part: never edited when a method is added. */
    static final class PaymentRegistry {
        private final Map<String, PaymentMethod> methods = new LinkedHashMap<>();

        PaymentRegistry register(PaymentMethod method) {
            if (methods.putIfAbsent(method.code(), method) != null) {                     // (5)!
                throw new IllegalStateException("duplicate payment method " + method.code());
            }
            return this;
        }

        long totalWithSurcharge(String code, long amountInCents) {
            PaymentMethod m = methods.get(code);
            if (m == null) throw new IllegalArgumentException("unsupported payment method " + code + ", available: " + methods.keySet());
            if (!m.supports(amountInCents)) throw new IllegalArgumentException(code + " not available for this amount");
            return amountInCents + m.surchargeInCents(amountInCents);
        }
    }

    public static void main(String[] args) {
        System.out.println("before CARD: " + (4_000 + surchargeBefore("CARD", 4_000)));

        PaymentRegistry registry = new PaymentRegistry()
                .register(new Card(0.015)).register(new Wallet())
                .register(new CashOnDelivery(99, 10_000)).register(new Upi());

        for (String code : List.of("CARD", "WALLET", "COD", "UPI")) {
            System.out.println("after  " + code + ": " + registry.totalWithSurcharge(code, 4_000));
        }
        for (Runnable bad : List.<Runnable>of(
                () -> registry.totalWithSurcharge("COD", 25_000),
                () -> registry.totalWithSurcharge("CRYPTO", 4_000),
                () -> registry.register(new Upi()))) {
            try { bad.run(); } catch (RuntimeException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
