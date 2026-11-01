import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Production: PayLink checkout migrated from GoF-era classes to Java 21 idioms, with the guarantees made explicit:
 * - value objects are records that validate themselves and are deeply immutable
 * - payment outcomes are a sealed hierarchy; mapping to HTTP is an exhaustive switch with no default
 * - payment strategies live in an EnumMap that is checked for completeness at startup
 * - logging uses record metadata, and components marked @Sensitive are masked automatically
 */
public class ModernCheckoutDemo {

    @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
    @interface Sensitive { }

    enum Mode { UPI, CARD, WALLET }

    record PaymentRequest(String orderId, Mode mode, long paise, @Sensitive String instrument, List<String> skus) {   // (1)!
        PaymentRequest {
            Objects.requireNonNull(orderId, "orderId"); Objects.requireNonNull(mode, "mode");
            if (paise <= 0) throw new IllegalArgumentException("amount must be positive");
            if (instrument == null || instrument.isBlank()) throw new IllegalArgumentException("instrument required");
            skus = List.copyOf(skus);
        }
    }

    sealed interface PaymentResult permits Captured, Declined, NeedsOtp { }                            // (2)!
    record Captured(String paymentId, long paise) implements PaymentResult { }
    record Declined(String code, boolean retryable) implements PaymentResult { }
    record NeedsOtp(String challengeId, @Sensitive String otpHint) implements PaymentResult { }

    @FunctionalInterface interface Processor { PaymentResult process(PaymentRequest r); }

    static Map<Mode, Processor> processors() {                                                         // (3)!
        Map<Mode, Processor> m = new EnumMap<>(Mode.class);
        m.put(Mode.UPI, r -> r.instrument().endsWith("@okbank") ? new Captured("upi_" + r.orderId(), r.paise()) : new Declined("VPA_INVALID", false));
        m.put(Mode.CARD, r -> r.paise() > 200_000 ? new NeedsOtp("ch_" + r.orderId(), "sent to ******0123") : new Captured("card_" + r.orderId(), r.paise()));
        m.put(Mode.WALLET, r -> new Declined("INSUFFICIENT_BALANCE", true));
        List<Mode> missing = Arrays.stream(Mode.values()).filter(x -> !m.containsKey(x)).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("no processor for " + missing);
        return Map.copyOf(m);
    }

    record HttpResponse(int status, String body) { }

    static HttpResponse toHttp(PaymentResult r) {                                                    // (4)!
        return switch (r) {
            case Captured(var id, var paise) -> new HttpResponse(201, "{\"paymentId\":\"" + id + "\",\"amount\":" + paise + "}");
            case Declined(var code, var retryable) when retryable -> new HttpResponse(409, "{\"error\":\"" + code + "\",\"retry\":true}");
            case Declined(var code, var retryable) -> new HttpResponse(402, "{\"error\":\"" + code + "\"}");
            case NeedsOtp(var challenge, var hint) -> new HttpResponse(202, "{\"challenge\":\"" + challenge + "\"}");
        };
    }

    static String toLog(Record r) {                                                                  // (5)!
        return r.getClass().getSimpleName() + Arrays.stream(r.getClass().getRecordComponents())
                .map(c -> c.getName() + "=" + (c.isAnnotationPresent(Sensitive.class) ? "***" : value(c, r)))
                .collect(Collectors.joining(", ", "{", "}"));
    }
    static Object value(RecordComponent c, Record r) {
        try { return c.getAccessor().invoke(r); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static void main(String[] args) {
        Map<Mode, Processor> processors = processors();
        List<PaymentRequest> requests = List.of(
                new PaymentRequest("FC-1", Mode.UPI, 64_900, "asha@okbank", List.of("MILK")),
                new PaymentRequest("FC-2", Mode.CARD, 250_000, "4242424242424242", List.of("TV-STAND")),
                new PaymentRequest("FC-3", Mode.WALLET, 12_000, "wallet-77", List.of("BREAD")),
                new PaymentRequest("FC-4", Mode.UPI, 9_000, "ravi@unknown", List.of("EGGS")));
        for (PaymentRequest req : requests) {
            PaymentResult result = processors.get(req.mode()).process(req);
            System.out.println(toLog(req));
            System.out.println("  -> " + toLog((Record) result) + " -> " + toHttp(result));
        }
        try { new PaymentRequest("FC-5", Mode.UPI, 0, "x@okbank", List.of()); }
        catch (IllegalArgumentException e) { System.out.println("rejected at construction: " + e.getMessage()); }
    }
}
