import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulkhead: give each dependency its own limited pool of concurrent calls, so one slow dependency can't use up
 * all the capacity. Simulated: each request holds a slot for its dependency's latency.
 */
public class BulkheadDemo {

    /** Concurrency per dependency, or one shared pool when perDependency is null. */
    static Map<String, Integer> simulate(int sharedThreads, Map<String, Integer> perDependency) {
        Map<String, Integer> latencyMs = Map.of("offers", 2000, "payments", 50);
        Map<String, List<Integer>> busyUntil = new LinkedHashMap<>();                            // (1)!
        Map<String, Integer> rejected = new LinkedHashMap<>(Map.of("offers", 0, "payments", 0));
        List<Integer> shared = new ArrayList<>();
        for (int t = 0; t < 1000; t += 10) {                                                     // a request every 10 ms per dependency
            for (String dep : List.of("offers", "payments")) {
                final int now = t;
                List<Integer> pool = perDependency == null ? shared : busyUntil.computeIfAbsent(dep, k -> new ArrayList<>());
                int limit = perDependency == null ? sharedThreads : perDependency.get(dep);
                pool.removeIf(until -> until <= now);
                if (pool.size() >= limit) rejected.merge(dep, 1, Integer::sum);                   // (2)!
                else pool.add(now + latencyMs.get(dep));
            }
        }
        return rejected;
    }

    public static void main(String[] args) {
        System.out.println("one shared pool of 40:      rejected " + simulate(40, null));
        System.out.println("bulkheads offers 20, payments 20: rejected "
                + simulate(0, Map.of("offers", 20, "payments", 20)));                               // (3)!
    }
}
