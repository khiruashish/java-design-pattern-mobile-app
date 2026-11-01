import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** FreshCart payments: translate PayLink's errors, retry only what's retryable, and give the app a clear outcome. */
public class PaymentErrorHandlingDemo {

    // ---- What the PayLink HTTP client throws (third-party vocabulary) ----
    static final class PayLinkHttpException extends RuntimeException {
        final int status; final String code;
        PayLinkHttpException(int status, String code) { super("PayLink " + status + " " + code); this.status = status; this.code = code; }
    }

    // ---- Our vocabulary ----
    sealed interface PaymentOutcome {}
    record Paid(String paymentId) implements PaymentOutcome {}
    record Declined(String reasonForCustomer) implements PaymentOutcome {}
    record TryAgainLater(String reasonForCustomer) implements PaymentOutcome {}

    static final class PaymentException extends RuntimeException {
        final boolean retryable;
        PaymentException(String m, boolean retryable, Throwable cause) { super(m, cause); this.retryable = retryable; }
    }

    static final Map<String, String> CUSTOMER_MESSAGES = Map.of(
            "insufficient_funds", "Your bank declined the payment: insufficient funds",
            "card_expired", "This card has expired",
            "do_not_honor", "Your bank declined the payment");

    /** Gateway adapter: translates PayLink errors into PaymentException, classifying retryable ones. */
    static String charge(PayLinkClient client, String orderId, long paise) {                       // (1)!
        try {
            return client.charge(orderId, paise);
        } catch (PayLinkHttpException e) {
            boolean retryable = e.status == 429 || e.status >= 500;
            throw new PaymentException(e.code, retryable, e);
        }
    }

    interface PayLinkClient { String charge(String idempotencyKey, long paise); }

    /** Service: bounded retries for retryable errors only, with the order ID as idempotency key. */
    static PaymentOutcome pay(PayLinkClient client, String orderId, long paise, List<String> log) {   // (2)!
        for (int attempt = 1; ; attempt++) {
            try {
                return new Paid(charge(client, orderId, paise));
            } catch (PaymentException e) {
                log.add(orderId + " attempt " + attempt + ": " + e.getMessage() + (e.retryable ? " (retryable)" : ""));
                if (!e.retryable)
                    return new Declined(CUSTOMER_MESSAGES.getOrDefault(e.getMessage(), "Payment failed"));   // (3)!
                if (attempt == 3)
                    return new TryAgainLater("Payments are slow right now. You have not been charged.");
            }
        }
    }

    /** Scripted fake: each call returns or throws the next scripted answer. */
    static PayLinkClient scripted(Object... answers) {
        int[] i = {0};
        return (key, paise) -> {
            Object next = answers[Math.min(i[0]++, answers.length - 1)];
            if (next instanceof PayLinkHttpException e) throw e;
            return (String) next;
        };
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        System.out.println(pay(scripted("pay_001"), "FC-1", 51_800, log));
        System.out.println(pay(scripted(new PayLinkHttpException(503, "unavailable"), "pay_002"), "FC-2", 12_000, log));
        System.out.println(pay(scripted(new PayLinkHttpException(402, "insufficient_funds")), "FC-3", 99_900, log));
        System.out.println(pay(scripted(new PayLinkHttpException(429, "rate_limited")), "FC-4", 5_000, log));
        System.out.println("log:");
        log.forEach(l -> System.out.println("  " + l));
    }
}
