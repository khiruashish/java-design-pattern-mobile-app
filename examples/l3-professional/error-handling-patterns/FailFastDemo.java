import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Fail fast: detect bad input or configuration at the earliest point, with a precise message. */
public class FailFastDemo {

    // ---- Before: bad config is accepted and fails much later, far from the cause. ----
    static final class LazyConfig {
        final Map<String, String> raw;
        LazyConfig(Map<String, String> raw) { this.raw = raw; }
        Duration paymentTimeout() { return Duration.ofMillis(Long.parseLong(raw.get("payment.timeout.ms"))); }
    }

    // ---- After: validated once, at startup, into a typed object. ----
    record PaymentConfig(String baseUrl, Duration timeout, int maxRetries) {
        PaymentConfig {                                                     // (1)!
            Objects.requireNonNull(baseUrl, "payment.base-url is required");
            if (!baseUrl.startsWith("https://")) throw new IllegalArgumentException("payment.base-url must use https: " + baseUrl);
            if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
                throw new IllegalArgumentException("payment.timeout.ms must be 1..30000, got " + timeout.toMillis());
            if (maxRetries < 0 || maxRetries > 5) throw new IllegalArgumentException("payment.max-retries must be 0..5, got " + maxRetries);
        }
        static PaymentConfig from(Map<String, String> raw) {
            return new PaymentConfig(raw.get("payment.base-url"),
                    Duration.ofMillis(number(raw, "payment.timeout.ms", "3000")),
                    (int) number(raw, "payment.max-retries", "2"));
        }
        private static long number(Map<String, String> raw, String key, String fallback) {
            String value = raw.getOrDefault(key, fallback);
            try { return Long.parseLong(value); }
            catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be a whole number, got '" + value + "'", e); }
        }
    }

    static long refund(long paidPaise, long refundPaise) {
        if (refundPaise <= 0 || refundPaise > paidPaise)                    // (2)!
            throw new IllegalArgumentException("refund " + refundPaise + " must be in 1.." + paidPaise);
        return paidPaise - refundPaise;
    }

    public static void main(String[] args) {
        Map<String, String> typo = Map.of("payment.base-url", "https://api.paylink.example", "payment.timeout.ms", "3s");
        LazyConfig lazy = new LazyConfig(typo);
        System.out.println("lazy config accepted at startup");
        try {
            lazy.paymentTimeout();                                          // (3)!
        } catch (NumberFormatException e) {
            System.out.println("first payment, hours later: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        for (Map<String, String> raw : java.util.List.of(
                typo,
                Map.of("payment.base-url", "http://api.paylink.example"),
                Map.of("payment.base-url", "https://api.paylink.example", "payment.max-retries", "9"),
                Map.of("payment.base-url", "https://api.paylink.example"))) {
            try {
                System.out.println("startup ok: " + PaymentConfig.from(raw));
            } catch (RuntimeException e) {
                System.out.println("startup refused: " + e.getMessage());
            }
        }
        try {
            refund(10_000, 25_000);
        } catch (IllegalArgumentException e) {
            System.out.println("refund refused: " + e.getMessage());
        }
    }
}
