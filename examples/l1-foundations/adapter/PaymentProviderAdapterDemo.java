import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade Adapter (an anti-corruption layer for a payment SDK):
 * - our PaymentGateway interface uses cents, our IDs and a sealed result type
 * - the vendor SDK uses rupee strings, its own codes and a CHECKED exception
 * - the adapter validates, converts units, passes our idempotency key, maps every vendor code,
 *   and never lets a vendor type escape
 */
public class PaymentProviderAdapterDemo {

    // ===================== Our model =====================
    record ChargeRequest(String orderId, String customerId, long amountCents, String idempotencyKey) { }
    enum DeclineReason { INSUFFICIENT_FUNDS, CARD_EXPIRED, SUSPECTED_FRAUD, OTHER }
    sealed interface ChargeResult permits Approved, Declined, Failed { }
    record Approved(String paymentId) implements ChargeResult { }
    record Declined(DeclineReason reason) implements ChargeResult { }
    record Failed(String detail, boolean retryable) implements ChargeResult { }

    interface PaymentGateway { ChargeResult charge(ChargeRequest request); }

    // ===================== Vendor SDK (can't change) =====================
    static final class PayLinkException extends Exception {
        final String code;
        PayLinkException(String code, String msg) { super(msg); this.code = code; }
    }
    static final class PayLinkSdk {
        private final Map<String, String> seenKeys = new HashMap<>();
        private int seq = 500;
        /** amount like "499.50"; returns txn id. */
        String createPayment(String merchantRef, String amount, String customer, String requestId) throws PayLinkException {
            if (seenKeys.containsKey(requestId)) return seenKeys.get(requestId);           // vendor dedupes by requestId
            if (customer.equals("broke")) throw new PayLinkException("E51", "insufficient balance");
            if (customer.equals("old-card")) throw new PayLinkException("E54", "expired");
            if (customer.equals("timeout")) throw new PayLinkException("E91", "issuer timeout");
            if (customer.equals("weird")) throw new PayLinkException("E77", "undocumented");
            String id = "plk_" + (++seq);
            seenKeys.put(requestId, id);
            return id;
        }
    }

    // ===================== The adapter =====================
    static final class PayLinkGateway implements PaymentGateway {
        private static final Map<String, DeclineReason> DECLINES = Map.of(                // (1)!
                "E51", DeclineReason.INSUFFICIENT_FUNDS, "E54", DeclineReason.CARD_EXPIRED, "E59", DeclineReason.SUSPECTED_FRAUD);
        private static final java.util.Set<String> RETRYABLE = java.util.Set.of("E91", "E96");
        private final PayLinkSdk sdk;
        final Map<String, Integer> unmappedCodes = new HashMap<>();

        PayLinkGateway(PayLinkSdk sdk) { this.sdk = sdk; }

        public ChargeResult charge(ChargeRequest r) {
            if (r.amountCents() <= 0) throw new IllegalArgumentException("amount must be positive");   // (2)!
            String rupees = BigDecimal.valueOf(r.amountCents()).movePointLeft(2).toPlainString();      // (3)!
            try {
                return new Approved(sdk.createPayment(r.orderId(), rupees, r.customerId(), r.idempotencyKey()));   // (4)!
            } catch (PayLinkException e) {                                                // (5)!
                DeclineReason reason = DECLINES.get(e.code);
                if (reason != null) return new Declined(reason);
                if (RETRYABLE.contains(e.code)) return new Failed("PayLink " + e.code, true);
                unmappedCodes.merge(e.code, 1, Integer::sum);
                return new Failed("PayLink " + e.code + " (unmapped)", false);
            }
        }
    }

    public static void main(String[] args) {
        PayLinkGateway gateway = new PayLinkGateway(new PayLinkSdk());
        String[][] cases = {{"FC-2001", "cust-1", "49950"}, {"FC-2002", "broke", "12000"}, {"FC-2003", "old-card", "8000"},
                {"FC-2004", "timeout", "8000"}, {"FC-2005", "weird", "8000"}};
        for (String[] c : cases) {
            ChargeResult result = gateway.charge(new ChargeRequest(c[0], c[1], Long.parseLong(c[2]), "idem-" + c[0]));
            String text = switch (result) {                                               // callers handle OUR types only
                case Approved a -> "approved " + a.paymentId();
                case Declined d -> "declined: " + d.reason();
                case Failed f -> "failed: " + f.detail() + (f.retryable() ? ", retry later" : ", do not retry");
            };
            System.out.println(c[0] + " -> " + text);
        }
        ChargeResult retry = gateway.charge(new ChargeRequest("FC-2001", "cust-1", 49_950, "idem-FC-2001"));
        System.out.println("retry of FC-2001 with same key -> " + retry + " (no double charge)");
        System.out.println("unmapped vendor codes to investigate: " + gateway.unmappedCodes);
        try { gateway.charge(new ChargeRequest("FC-2006", "cust-1", 0, "idem-FC-2006")); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
