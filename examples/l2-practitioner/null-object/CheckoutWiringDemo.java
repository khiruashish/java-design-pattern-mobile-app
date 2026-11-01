import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production: FreshCart checkout has REQUIRED collaborators (payment) and OPTIONAL ones (SMS, metrics, loyalty).
 * - optional features that are switched off get Null Objects, chosen once at startup from config
 * - required collaborators never get a Null Object: wiring fails fast
 * - disabled features are reported at startup, and suppressed calls are counted, so "doing nothing" stays visible
 * - a Special Case for a DOWN loyalty service returns a safe answer AND says it is degraded
 */
public class CheckoutWiringDemo {

    interface PaymentGateway { String charge(String orderId, long paise); }
    interface Notifier { void send(String orderId, String text); }
    interface Metrics { void increment(String name); }
    interface Loyalty { long pointsFor(String customerId, long paise); boolean degraded(); }

    /** A Null Object that counts what it swallowed, so silence is measurable. */
    static final class DisabledNotifier implements Notifier {                                       // (1)!
        final AtomicLong suppressed = new AtomicLong();
        public void send(String orderId, String text) { suppressed.incrementAndGet(); }
    }
    enum NoMetrics implements Metrics { INSTANCE; public void increment(String name) { } }

    /** Special case: the loyalty service is unreachable. Award nothing now, but say so. */
    static final class LoyaltyUnavailable implements Loyalty {                                     // (2)!
        final List<String> owed = new ArrayList<>();
        public long pointsFor(String customerId, long paise) { owed.add(customerId + ":" + paise); return 0; }
        public boolean degraded() { return true; }
    }

    record Collaborators(PaymentGateway payments, Notifier notifier, Metrics metrics, Loyalty loyalty, List<String> disabled) { }

    static Collaborators wire(Map<String, String> config, Map<String, Object> realBeans) {          // (3)!
        PaymentGateway payments = (PaymentGateway) realBeans.get("payments");
        Objects.requireNonNull(payments, "payments is required; refusing to start with a no-op payment gateway");
        List<String> disabled = new ArrayList<>();
        Notifier n = on(config, "sms") ? (Notifier) realBeans.get("sms") : off(disabled, "sms", new DisabledNotifier());
        Metrics m = on(config, "metrics") ? (Metrics) realBeans.get("metrics") : off(disabled, "metrics", NoMetrics.INSTANCE);
        Loyalty l = on(config, "loyalty") ? (Loyalty) realBeans.get("loyalty") : off(disabled, "loyalty", new LoyaltyUnavailable());
        return new Collaborators(payments, n, m, l, List.copyOf(disabled));
    }
    static boolean on(Map<String, String> c, String k) { return Boolean.parseBoolean(c.getOrDefault(k + ".enabled", "false")); }
    static <T> T off(List<String> disabled, String name, T nullObject) { disabled.add(name); return nullObject; }

    record Receipt(String orderId, String paymentRef, long points, boolean loyaltyDegraded) { }

    static Receipt checkout(Collaborators c, String orderId, String customerId, long paise) {        // (4)!
        String ref = c.payments().charge(orderId, paise);
        c.metrics().increment("checkout.success");
        c.notifier().send(orderId, "Order " + orderId + " confirmed");
        long points = c.loyalty().pointsFor(customerId, paise);
        return new Receipt(orderId, ref, points, c.loyalty().degraded());
    }

    public static void main(String[] args) {
        Map<String, Object> beans = new LinkedHashMap<>();
        beans.put("payments", (PaymentGateway) (id, p) -> "PAY-" + id);
        beans.put("sms", (Notifier) (id, t) -> System.out.println("  sms: " + t));

        Map<String, String> config = new LinkedHashMap<>();
        config.put("sms.enabled", "true");
        config.put("metrics.enabled", "false");
        config.put("loyalty.enabled", "false");

        Collaborators c = wire(config, beans);
        System.out.println("startup: disabled features " + c.disabled());                          // (5)!
        System.out.println(checkout(c, "FC-2001", "u1", 64_900));

        config.put("sms.enabled", "false");
        Collaborators quiet = wire(config, beans);
        checkout(quiet, "FC-2002", "u2", 30_000);
        checkout(quiet, "FC-2003", "u3", 12_000);
        System.out.println("sms suppressed: " + ((DisabledNotifier) quiet.notifier()).suppressed.get());
        System.out.println("loyalty points owed later: " + ((LoyaltyUnavailable) quiet.loyalty()).owed);

        try { wire(config, Map.of()); }
        catch (NullPointerException e) { System.out.println("startup failed: " + e.getMessage()); }
    }
}
