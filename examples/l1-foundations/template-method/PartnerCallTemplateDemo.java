import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Production-grade Template Method: every call to a partner API (courier, payments) follows the same
 * resilience recipe: validate, call, classify errors, retry with exponential backoff, map errors, record metrics.
 * Each partner only implements the call itself and how to classify its own errors.
 */
public class PartnerCallTemplateDemo {

    interface Sleeper { void sleep(Duration d); }                                         // injected, so tests don't wait

    static final class PartnerException extends RuntimeException {
        PartnerException(String partner, String op, int attempts, Throwable cause) {
            super(partner + " " + op + " failed after " + attempts + " attempt(s): " + cause.getMessage(), cause);
        }
    }

    static final class TransientError extends RuntimeException { TransientError(String m) { super(m); } }

    abstract static class PartnerCall<REQ, RES> {
        private final Sleeper sleeper;
        final Map<String, LongAdder> metrics = new ConcurrentHashMap<>();
        final List<Duration> backoffs = new ArrayList<>();

        PartnerCall(Sleeper sleeper) { this.sleeper = sleeper; }

        final RES execute(REQ request) {                                                   // (1)!
            validate(request);
            Duration delay = initialBackoff();
            for (int attempt = 1; ; attempt++) {
                try {
                    RES result = doCall(request);
                    count("ok");
                    return result;
                } catch (RuntimeException e) {
                    boolean retry = isRetryable(e) && attempt < maxAttempts();            // (2)!
                    count(retry ? "retry" : "failed");
                    if (!retry) throw new PartnerException(partner(), operation(), attempt, e);
                    backoffs.add(delay);
                    sleeper.sleep(delay);
                    delay = delay.multipliedBy(2);                                         // (3)!
                }
            }
        }

        // ---- steps every partner must provide ----
        protected abstract String partner();
        protected abstract String operation();
        protected abstract RES doCall(REQ request);

        // ---- hooks with safe defaults ----
        protected void validate(REQ request) { }
        protected boolean isRetryable(RuntimeException e) { return e instanceof TransientError; }   // (4)!
        protected int maxAttempts() { return 3; }
        protected Duration initialBackoff() { return Duration.ofMillis(200); }

        private void count(String outcome) { metrics.computeIfAbsent(partner() + "." + operation() + "." + outcome, k -> new LongAdder()).increment(); }
    }

    // ===================== Partner 1: courier booking =====================
    record Booking(String orderId, String pincode) { }

    static final class QuickDropBooking extends PartnerCall<Booking, String> {
        private final Deque<String> scriptedResponses;                                     // simulates the remote API
        QuickDropBooking(Sleeper s, String... responses) { super(s); scriptedResponses = new ArrayDeque<>(List.of(responses)); }
        protected String partner() { return "quickdrop"; }
        protected String operation() { return "book"; }
        @Override protected void validate(Booking b) {
            if (!b.pincode().matches("\\d{6}")) throw new IllegalArgumentException("bad pincode " + b.pincode());
        }
        protected String doCall(Booking b) {
            String r = scriptedResponses.poll();
            if (r.equals("503")) throw new TransientError("HTTP 503");
            if (r.equals("422")) throw new IllegalStateException("HTTP 422 address not serviceable");
            return "booking " + r + " for " + b.orderId();
        }
    }

    // ===================== Partner 2: payment status (different retry policy) =====================
    static final class PayLinkStatus extends PartnerCall<String, String> {
        private int calls;
        PayLinkStatus(Sleeper s) { super(s); }
        protected String partner() { return "paylink"; }
        protected String operation() { return "status"; }
        @Override protected int maxAttempts() { return 5; }                               // (5)!
        @Override protected Duration initialBackoff() { return Duration.ofMillis(50); }
        @Override protected boolean isRetryable(RuntimeException e) { return super.isRetryable(e) || e.getMessage().contains("PENDING"); }
        protected String doCall(String paymentId) {
            if (++calls < 4) throw new IllegalStateException("status PENDING");
            return paymentId + " CAPTURED";
        }
    }

    public static void main(String[] args) {
        List<String> slept = new ArrayList<>();
        Sleeper fakeSleeper = d -> slept.add(d.toMillis() + "ms");

        var ok = new QuickDropBooking(fakeSleeper, "503", "503", "QD-778");
        System.out.println(ok.execute(new Booking("FC-1601", "411001")) + ", backoffs " + ok.backoffs.stream().map(d -> d.toMillis() + "ms").toList());

        var notServiceable = new QuickDropBooking(fakeSleeper, "422");
        try { notServiceable.execute(new Booking("FC-1602", "411001")); } catch (PartnerException e) { System.out.println(e.getMessage()); }

        var down = new QuickDropBooking(fakeSleeper, "503", "503", "503");
        try { down.execute(new Booking("FC-1603", "411001")); } catch (PartnerException e) { System.out.println(e.getMessage()); }

        try { ok.execute(new Booking("FC-1604", "41")); } catch (IllegalArgumentException e) { System.out.println("rejected before calling: " + e.getMessage()); }

        var pay = new PayLinkStatus(fakeSleeper);
        System.out.println(pay.execute("pay_91") + ", backoffs " + pay.backoffs.stream().map(d -> d.toMillis() + "ms").toList());

        var all = new TreeMap<String, Long>();
        for (var c : List.of(ok, notServiceable, down)) c.metrics.forEach((k, v) -> all.merge(k, v.sum(), Long::sum));
        pay.metrics.forEach((k, v) -> all.merge(k, v.sum(), Long::sum));
        System.out.println(all);
        System.out.println("total time slept (fake): " + slept);
    }
}
