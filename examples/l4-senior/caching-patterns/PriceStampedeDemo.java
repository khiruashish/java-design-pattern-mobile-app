import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Realistic: FreshCart caches prices in a shared cache. When a hot key expires during a sale, hundreds of requests
 * miss at once and all hit the pricing database (a stampede). Single-flight loading and TTL jitter prevent it.
 */
public class PriceStampedeDemo {

    record Entry(int price, long availableAt, long expiresAt) {}

    static final class PriceCache {
        final Map<String, Entry> entries = new HashMap<>();
        private final Map<String, Long> loadingUntil = new HashMap<>();
        private final boolean singleFlight; private final Random jitter;
        int dbLoads;
        PriceCache(boolean singleFlight, Random jitter) { this.singleFlight = singleFlight; this.jitter = jitter; }

        /** A request at time now (ms). A load takes 200 ms; requests during a load wait for it if single-flight is on. */
        String get(String sku, long now) {
            Entry e = entries.get(sku);
            if (e != null && now >= e.availableAt() && now < e.expiresAt()) return "hit";
            if (singleFlight && loadingUntil.getOrDefault(sku, -1L) > now) return "joined in-flight load";   // (1)!
            dbLoads++;
            loadingUntil.put(sku, now + 200);
            long ttl = 60_000 + (jitter == null ? 0 : jitter.nextInt(12_000));                          // (2)!
            long ready = e != null && e.availableAt() > now ? e.availableAt() : now + 200;    // usable once the first load finishes
            entries.put(sku, new Entry(68, ready, ready + ttl));
            return "loaded";
        }
    }

    public static void main(String[] args) {
        for (boolean protectedCache : new boolean[] {false, true}) {
            PriceCache cache = new PriceCache(protectedCache, protectedCache ? new Random(3) : null);
            Map<String, Integer> outcomes = new TreeMap<>();
            for (int i = 0; i < 50; i++) cache.get("SKU-" + i, 0);          // warm 50 hot SKUs at the same moment
            cache.dbLoads = 0;
            int maxLoadsPerSecond = 0;
            for (long second = 60; second < 75; second++) {                // a sale: 20 requests per SKU per second
                int before = cache.dbLoads;
                for (int r = 0; r < 20; r++)
                    for (int i = 0; i < 50; i++) outcomes.merge(cache.get("SKU-" + i, second * 1000 + r * 50), 1, Integer::sum);
                maxLoadsPerSecond = Math.max(maxLoadsPerSecond, cache.dbLoads - before);
            }
            System.out.println((protectedCache ? "single-flight + jitter: " : "plain TTL:              ")
                    + "db loads=" + cache.dbLoads + ", worst second=" + maxLoadsPerSecond + " loads, " + outcomes);   // (3)!
        }
    }
}
