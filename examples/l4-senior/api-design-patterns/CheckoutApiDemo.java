import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Production-grade: FreshCart's POST /{version}/orders. Requires an Idempotency-Key; replays stored responses;
 * rejects a reused key with a different body; returns 409 while the first request is still running; lets clients
 * retry after server errors; expires keys after 24 hours; renders v1 and v2 from one domain result; all errors are
 * Problem Details.
 */
public class CheckoutApiDemo {

    record Response(int status, Map<String, String> headers, String body) {
        @Override public String toString() { return status + " " + headers + " " + body; }
    }
    record Placed(String orderId, long totalPaise) {}
    record IdempotencyRecord(String fingerprint, boolean inProgress, Response response, long createdAt) {}

    static final class CheckoutApi {
        static final long KEY_TTL_MS = 24 * 60 * 60 * 1000L;
        final Map<String, IdempotencyRecord> keys = new HashMap<>();
        private final Function<String, Placed> placeOrder;
        long now;
        Runnable whileProcessing = () -> {};
        CheckoutApi(Function<String, Placed> placeOrder) { this.placeOrder = placeOrder; }

        Response post(String version, Map<String, String> headers, String body) {
            String key = headers.get("Idempotency-Key");
            if (key == null) return problem(400, "idempotency-key-missing", "Send an Idempotency-Key header with every POST /orders");   // (1)!
            String fingerprint = version + " POST /orders " + body;
            IdempotencyRecord existing = keys.get(key);
            if (existing != null && now - existing.createdAt() > KEY_TTL_MS) { keys.remove(key); existing = null; }
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) return problem(422, "idempotency-key-reused", "This key was used for a different request");
                if (existing.inProgress()) return withHeader(problem(409, "request-in-progress", "The original request is still being processed"), "Retry-After", "1");   // (2)!
                return withHeader(existing.response(), "Idempotent-Replayed", "true");                // (3)!
            }
            keys.put(key, new IdempotencyRecord(fingerprint, true, null, now));
            whileProcessing.run();
            Response response;
            try {
                response = render(version, placeOrder.apply(body));
            } catch (IllegalArgumentException e) {
                response = problem(422, "order-invalid", e.getMessage());
            } catch (RuntimeException e) {
                keys.remove(key);                                                                       // (4)!
                return problem(503, "temporarily-unavailable", "Please retry with the same Idempotency-Key");
            }
            keys.put(key, new IdempotencyRecord(fingerprint, false, response, now));
            return response;
        }

        static Response render(String version, Placed p) {                                             // (5)!
            String total = version.equals("v1") ? String.format("%d.%02d", p.totalPaise() / 100, p.totalPaise() % 100)
                    : "{\"amountPaise\":" + p.totalPaise() + ",\"currency\":\"INR\"}";
            return new Response(201, new TreeMap<>(Map.of("Location", "/" + version + "/orders/" + p.orderId())),
                    "{\"orderId\":\"" + p.orderId() + "\",\"total\":" + total + "}");
        }
        static Response problem(int status, String type, String detail) {
            return new Response(status, new TreeMap<>(Map.of("Content-Type", "application/problem+json")),
                    "{\"type\":\"/problems/" + type + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
        }
        static Response withHeader(Response r, String name, String value) {
            Map<String, String> h = new TreeMap<>(r.headers()); h.put(name, value);
            return new Response(r.status(), h, r.body());
        }
    }

    public static void main(String[] args) {
        int[] created = {0};
        boolean[] paymentsDown = {false};
        CheckoutApi api = new CheckoutApi(body -> {
            if (body.contains("qty=0")) throw new IllegalArgumentException("Quantity must be at least 1");
            if (paymentsDown[0]) throw new IllegalStateException("PayLink unavailable");
            created[0]++;
            return new Placed("FC-" + (2000 + created[0]), 13600);
        });
        Map<String, String> k1 = Map.of("Idempotency-Key", "k-1");
        api.whileProcessing = () -> System.out.println("  concurrent duplicate: " + api.post("v2", k1, "MILK-1L qty=2"));
        System.out.println("first:      " + api.post("v2", k1, "MILK-1L qty=2"));
        api.whileProcessing = () -> {};
        System.out.println("retry:      " + api.post("v2", k1, "MILK-1L qty=2"));
        System.out.println("other body: " + api.post("v2", k1, "MILK-1L qty=3"));
        System.out.println("no key:     " + api.post("v2", Map.of(), "MILK-1L qty=2"));
        System.out.println("invalid:    " + api.post("v2", Map.of("Idempotency-Key", "k-2"), "MILK-1L qty=0"));
        paymentsDown[0] = true;
        System.out.println("5xx:        " + api.post("v1", Map.of("Idempotency-Key", "k-3"), "CURD-500G qty=1"));
        paymentsDown[0] = false;
        System.out.println("retry 5xx:  " + api.post("v1", Map.of("Idempotency-Key", "k-3"), "CURD-500G qty=1"));
        api.now += CheckoutApi.KEY_TTL_MS + 1;
        System.out.println("after 24 h: " + api.post("v2", k1, "MILK-1L qty=2"));
        System.out.println("orders created: " + created[0]);
    }
}
