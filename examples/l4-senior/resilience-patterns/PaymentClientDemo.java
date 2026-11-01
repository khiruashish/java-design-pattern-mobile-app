import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Production-grade: FreshCart's PayLink client composes a circuit breaker, a per-attempt timeout, retries with an
 * idempotency key, a retry budget and a fallback, in a deliberate order. A virtual clock and a scripted PayLink
 * make the output stable.
 */
public class PaymentClientDemo {

    record Attempt(String idempotencyKey, int number) {}
    record Reply(int status, int latencyMs) {}
    record Outcome(String result, int remoteCalls) {}

    static final class PaymentClient {
        static final int TIMEOUT_MS = 800, MAX_ATTEMPTS = 3, WINDOW = 4;
        static final long OPEN_MS = 30_000;
        final Map<String, Integer> metrics = new TreeMap<>();
        private final Function<Attempt, Reply> paylink;
        private int windowCalls, windowFailures; private boolean open; private long openedAt;
        private int retryTenths = 10;                 // retry budget in tenths of a retry: +0.2 per request, 1 per retry
        long now;
        PaymentClient(Function<Attempt, Reply> paylink) { this.paylink = paylink; }

        Outcome charge(String orderId) {
            retryTenths = Math.min(50, retryTenths + 2);
            if (open && now - openedAt < OPEN_MS) return done("rejected-open", "pending: queued for later", 0);   // (1)!
            String key = "pay-" + orderId;                                                        // (2)!
            int calls = 0;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                if (attempt > 1) {
                    if (open || retryTenths < 10) { metrics.merge("retry-skipped", 1, Integer::sum); break; }   // (3)!
                    retryTenths -= 10;
                }
                calls++;
                Reply r = paylink.apply(new Attempt(key, attempt));
                boolean timedOut = r.latencyMs() > TIMEOUT_MS;                                    // (4)!
                now += Math.min(r.latencyMs(), TIMEOUT_MS);
                if (!timedOut && r.status() == 200) { record(true); return done("ok", "paid", calls); }
                if (!timedOut && r.status() == 402) { record(true); return done("declined", "declined: card refused", calls); }
                record(false);
            }
            return done("fallback", "pending: queued for later", calls);                          // (5)!
        }

        private void record(boolean success) {
            if (open) { open = !success; if (open) openedAt = now; windowCalls = 0; windowFailures = 0; return; }   // half-open trial
            windowCalls++; if (!success) windowFailures++;
            if (windowCalls == WINDOW) {
                if (windowFailures * 2 >= WINDOW) { open = true; openedAt = now; }
                windowCalls = 0; windowFailures = 0;
            }
        }
        String state() { return open ? (now - openedAt < OPEN_MS ? "OPEN" : "HALF_OPEN") : "CLOSED"; }
        private Outcome done(String metric, String result, int calls) { metrics.merge(metric, 1, Integer::sum); return new Outcome(result, calls); }
    }

    public static void main(String[] args) {
        Set<String> charged = new HashSet<>();
        int[] chargesMade = {0};
        String[] mode = {"healthy"};
        Function<Attempt, Reply> paylink = a -> {
            if (mode[0].equals("outage")) return new Reply(503, 60);
            if (a.idempotencyKey().equals("pay-FC-2")) return new Reply(402, 90);
            if (charged.add(a.idempotencyKey())) chargesMade[0]++;      // PayLink dedupes by idempotency key
            boolean slowFirstTry = a.idempotencyKey().equals("pay-FC-3") && a.number() == 1;
            return new Reply(200, slowFirstTry ? 2500 : 120);
        };
        PaymentClient client = new PaymentClient(paylink);
        for (String id : new String[] {"FC-1", "FC-2", "FC-3"}) System.out.println(id + " " + client.charge(id) + " " + client.state());
        System.out.println("PayLink charges made: " + chargesMade[0] + " (FC-1 and FC-3; FC-3 timed out, was retried with the same key, charged once)");

        mode[0] = "outage";
        for (int i = 4; i <= 8; i++) System.out.println("FC-" + i + " " + client.charge("FC-" + i) + " " + client.state());
        client.now += 31_000;
        mode[0] = "healthy";
        System.out.println("after 31 s: " + client.state());
        System.out.println("FC-9 " + client.charge("FC-9") + " " + client.state());
        System.out.println("metrics: " + client.metrics);
    }
}
