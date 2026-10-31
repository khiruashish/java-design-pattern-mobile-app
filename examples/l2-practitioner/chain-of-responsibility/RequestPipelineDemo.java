import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Production-grade request pipeline (what servlet filters / Spring interceptors / gRPC interceptors give you):
 * - handlers declare an explicit order; the chain is sorted, validated (no duplicate names or orders) and built ONCE
 * - each handler can enrich the request, short-circuit with a response, or call next and post-process
 * - an error handler near the front turns exceptions into a 500 with the request id; per-handler metrics
 * - the idempotency handler replays the stored response for a repeated Idempotency-Key
 */
public class RequestPipelineDemo {

    record Request(String method, String path, Map<String, String> headers, Map<String, String> attrs) {
        Request with(String k, String v) { Map<String, String> a = new HashMap<>(attrs); a.put(k, v); return new Request(method, path, headers, a); }
    }
    record Response(int status, String body) { }

    interface Next { Response call(Request r); }
    interface Handler { String name(); int order(); Response handle(Request r, Next next); }       // (1)!

    static final class Pipeline {
        private final Next entry; final Map<String, AtomicIntegerArray> metrics = new TreeMap<>();   // name -> [calls, shortCircuits]

        Pipeline(List<Handler> handlers, Next endpoint) {
            Set<String> names = new HashSet<>(); Set<Integer> orders = new HashSet<>();
            for (Handler h : handlers)
                if (!names.add(h.name()) || !orders.add(h.order())) throw new IllegalArgumentException("duplicate handler name or order: " + h.name());   // (2)!
            List<Handler> sorted = new ArrayList<>(handlers);
            sorted.sort(Comparator.comparingInt(Handler::order));
            Next chain = endpoint;
            for (int i = sorted.size() - 1; i >= 0; i--) {
                Handler h = sorted.get(i); Next next = chain; AtomicIntegerArray m = new AtomicIntegerArray(2);
                metrics.put(String.format("%02d-%s", h.order(), h.name()), m);
                chain = req -> {
                    m.incrementAndGet(0);
                    boolean[] calledNext = {false};                                              // per call: safe under concurrency
                    Response r = h.handle(req, x -> { calledNext[0] = true; return next.call(x); });
                    if (!calledNext[0]) m.incrementAndGet(1);                                     // (3)!
                    return r;
                };
            }
            this.entry = chain;
        }

        Response serve(Request r) {
            try { return entry.call(r); }
            catch (RuntimeException e) { return new Response(500, "internal error"); }        // last resort if a handler itself fails
        }
    }

    record RequestId(int[] seq) implements Handler {
        public String name() { return "request-id"; } public int order() { return 10; }
        public Response handle(Request r, Next next) { return next.call(r.with("requestId", "req-" + (++seq[0]))); }
    }
    record Errors() implements Handler {
        public String name() { return "errors"; } public int order() { return 15; }
        public Response handle(Request r, Next next) {
            try { return next.call(r); }
            catch (RuntimeException e) { return new Response(500, "internal error, ref " + r.attrs().get("requestId")); }   // (4)!
        }
    }
    record Auth(Map<String, String> tokens) implements Handler {
        public String name() { return "auth"; } public int order() { return 20; }
        public Response handle(Request r, Next next) {
            String user = tokens.get(r.headers().getOrDefault("Authorization", ""));
            return user == null ? new Response(401, "invalid token") : next.call(r.with("user", user));
        }
    }
    record Idempotency(Map<String, Response> seen) implements Handler {
        public String name() { return "idempotency"; } public int order() { return 30; }
        public Response handle(Request r, Next next) {
            String key = r.headers().get("Idempotency-Key");
            if (key == null || !r.method().equals("POST")) return next.call(r);
            Response earlier = seen.get(key);
            if (earlier != null) return earlier;                                                   // (5)!
            Response fresh = next.call(r);
            if (fresh.status() < 500) seen.put(key, fresh);
            return fresh;
        }
    }

    public static void main(String[] args) {
        int[] orderSeq = {9300};
        Next endpoint = r -> {
            if (r.path().equals("/boom")) throw new IllegalStateException("bug in handler");
            return r.method().equals("POST") ? new Response(201, "order FC-" + (++orderSeq[0]) + " for " + r.attrs().get("user")) : new Response(200, "hello " + r.attrs().get("user"));
        };
        Pipeline p = new Pipeline(List.of(new Idempotency(new HashMap<>()), new Auth(Map.of("t-asha", "asha")), new Errors(), new RequestId(new int[1])), endpoint);

        Map<String, String> auth = Map.of("Authorization", "t-asha", "Idempotency-Key", "k-1");
        System.out.println(p.serve(new Request("POST", "/orders", auth, Map.of())));
        System.out.println(p.serve(new Request("POST", "/orders", auth, Map.of())) + "  <- retry replayed, no second order");
        System.out.println(p.serve(new Request("GET", "/me", Map.of("Authorization", "bad"), Map.of())));
        System.out.println(p.serve(new Request("GET", "/boom", Map.of("Authorization", "t-asha"), Map.of())));
        p.metrics.forEach((k, v) -> System.out.println("  " + k + ": calls=" + v.get(0) + " shortCircuits=" + v.get(1)));

        try { new Pipeline(List.of(new Auth(Map.of()), new Auth(Map.of())), endpoint); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
