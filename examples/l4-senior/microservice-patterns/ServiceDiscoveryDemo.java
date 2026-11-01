import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service discovery: instances register with a heartbeat; entries expire if heartbeats stop; clients look up live
 * instances and load-balance across them.
 */
public class ServiceDiscoveryDemo {

    record Instance(String service, String address) {}

    static final class Registry {                                                           // (1)!
        private final Map<String, Long> lastHeartbeat = new TreeMap<>();
        private final long ttlSeconds;
        Registry(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        void heartbeat(Instance i, long now) { lastHeartbeat.put(i.service() + "@" + i.address(), now); }
        List<String> live(String service, long now) {
            List<String> out = new ArrayList<>();
            lastHeartbeat.forEach((key, seen) -> {
                if (key.startsWith(service + "@") && now - seen <= ttlSeconds) out.add(key.substring(service.length() + 1));   // (2)!
            });
            return out;
        }
    }

    static final class RoundRobinClient {                                                   // (3)!
        private final Registry registry; private final String service; private int next;
        RoundRobinClient(Registry registry, String service) { this.registry = registry; this.service = service; }
        String pick(long now) {
            List<String> live = registry.live(service, now);
            if (live.isEmpty()) throw new IllegalStateException("no live instances of " + service);
            return live.get(next++ % live.size());
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry(30);
        var a = new Instance("pricing", "10.0.0.11:8080");
        var b = new Instance("pricing", "10.0.0.12:8080");
        var c = new Instance("pricing", "10.0.0.13:8080");
        for (var i : List.of(a, b, c)) registry.heartbeat(i, 0);
        RoundRobinClient client = new RoundRobinClient(registry, "pricing");

        List<String> picks = new ArrayList<>();
        for (int i = 0; i < 3; i++) picks.add(client.pick(10));
        System.out.println("t=10s picks: " + picks);

        registry.heartbeat(a, 25); registry.heartbeat(c, 25);    // b crashed and stopped sending heartbeats
        System.out.println("t=40s live: " + registry.live("pricing", 40));
        picks.clear();
        for (int i = 0; i < 4; i++) picks.add(client.pick(40));
        System.out.println("t=40s picks: " + picks);
        try { client.pick(100); } catch (IllegalStateException e) { System.out.println("t=100s: " + e.getMessage()); }
    }
}
