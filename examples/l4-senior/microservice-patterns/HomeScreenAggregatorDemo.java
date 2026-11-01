import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Production-grade: the gateway's home-screen endpoint composes three services found through discovery. Calls run in
 * parallel under one deadline; required parts fail the request, optional parts degrade; every call carries the
 * request ID; per-dependency outcomes are recorded. Latency is simulated with a virtual clock so output is stable.
 */
public class HomeScreenAggregatorDemo {

    record Reply(long latencyMs, String body) {}
    record Downstream(String service, boolean required) {}
    interface Transport { Reply call(String address, String path, Map<String, String> headers); }

    static final class Registry {
        private final Map<String, List<String>> live = new TreeMap<>();
        void register(String service, String address) { live.computeIfAbsent(service, k -> new ArrayList<>()).add(address); }
        void deregister(String service, String address) { live.getOrDefault(service, new ArrayList<>()).remove(address); }
        List<String> lookup(String service) { return live.getOrDefault(service, List.of()); }
    }

    record HomeScreen(int status, Map<String, String> sections, List<String> degraded, long latencyMs) {}

    static final class HomeScreenAggregator {
        static final List<Downstream> PARTS = List.of(
                new Downstream("catalog", true), new Downstream("cart", true), new Downstream("offers", false));   // (1)!
        private final Registry registry; private final Transport transport; private final long deadlineMs;
        private final Map<String, Integer> rr = new TreeMap<>();
        final List<String> outcomes = new ArrayList<>();
        HomeScreenAggregator(Registry registry, Transport transport, long deadlineMs) {
            this.registry = registry; this.transport = transport; this.deadlineMs = deadlineMs;
        }

        HomeScreen render(String requestId, String customerId) {
            Map<String, String> headers = Map.of("X-Request-Id", requestId, "X-Customer", customerId);   // (2)!
            Map<String, String> sections = new LinkedHashMap<>();
            List<String> degraded = new ArrayList<>();
            long slowest = 0; boolean failed = false;
            for (Downstream d : PARTS) {                                    // conceptually parallel: total = slowest call
                List<String> instances = registry.lookup(d.service());
                String outcome; Reply reply = null;
                if (instances.isEmpty()) outcome = "no-instance";                                            // (3)!
                else {
                    int i = rr.merge(d.service(), 1, Integer::sum) - 1;
                    reply = transport.call(instances.get(i % instances.size()), "/" + d.service() + "/home", headers);
                    slowest = Math.max(slowest, Math.min(reply.latencyMs(), deadlineMs));
                    outcome = reply.latencyMs() > deadlineMs ? "timeout" : reply.body().startsWith("5") ? "error" : "ok";   // (4)!
                }
                outcomes.add(requestId + " " + d.service() + "=" + outcome);
                if (outcome.equals("ok")) sections.put(d.service(), reply.body());
                else if (d.required()) failed = true;
                else degraded.add(d.service());
            }
            if (failed) return new HomeScreen(503, Map.of(), List.of(), slowest);
            return new HomeScreen(200, sections, degraded, slowest);                                    // (5)!
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();
        registry.register("catalog", "10.0.1.1"); registry.register("catalog", "10.0.1.2");
        registry.register("cart", "10.0.2.1"); registry.register("offers", "10.0.3.1");
        Map<String, Long> latency = new TreeMap<>(Map.of("10.0.1.1", 40L, "10.0.1.2", 45L, "10.0.2.1", 30L, "10.0.3.1", 60L));
        Transport transport = (address, path, h) -> new Reply(latency.get(address), path + " for " + h.get("X-Customer") + " via " + address);
        HomeScreenAggregator gw = new HomeScreenAggregator(registry, transport, 150);

        print(gw.render("req-1", "C-1"));
        latency.put("10.0.3.1", 400L);                  // offers is struggling
        print(gw.render("req-2", "C-1"));
        registry.deregister("cart", "10.0.2.1");        // the only cart instance went away
        print(gw.render("req-3", "C-1"));
        gw.outcomes.forEach(o -> System.out.println("  outcome " + o));
    }

    static void print(HomeScreen s) {
        System.out.println(s.status() + " in " + s.latencyMs() + " ms, degraded=" + s.degraded());
        s.sections().forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }
}
