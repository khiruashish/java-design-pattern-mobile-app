import java.util.HashMap;
import java.util.Map;

/**
 * Cache invalidation: TTLs bound staleness, explicit deletes react to writes, versioned keys make old entries
 * unreachable. The classic race: a slow reader puts a stale value back after a writer's delete.
 */
public class CacheInvalidationDemo {

    record Entry(int value, long expiresAt) {}
    static final Map<String, Integer> db = new HashMap<>(Map.of("MILK-1L", 68));
    static final Map<String, Entry> cache = new HashMap<>();

    public static void main(String[] args) {
        // The race, step by step (two app instances, one cache).
        int readerSaw = db.get("MILK-1L");                     // reader: cache miss, reads 68 from the DB ... then pauses (GC)
        db.put("MILK-1L", 70); cache.remove("MILK-1L");         // writer: updates DB to 70, deletes the cache key
        cache.put("MILK-1L", new Entry(readerSaw, Long.MAX_VALUE));                                // (1)!
        System.out.println("delete-on-write, no TTL: cache=" + cache.get("MILK-1L").value() + " db=" + db.get("MILK-1L") + " (stale until next write)");

        // Same race with a TTL: staleness is bounded.
        cache.put("MILK-1L", new Entry(68, 300));                                                  // (2)!
        long now = 301;
        Entry e = cache.get("MILK-1L");
        System.out.println("with 300 s TTL at t=301: " + (e.expiresAt() <= now ? "expired, reload " + db.get("MILK-1L") : "still " + e.value()));

        // Versioned keys: readers put under the version they read; the writer bumps the version.
        Map<String, Integer> version = new HashMap<>(Map.of("MILK-1L", 7));
        int v = version.get("MILK-1L");                       // reader reads version 7 and price 68 ... pauses
        version.put("MILK-1L", 8); db.put("MILK-1L", 72);       // writer: new price, version 8
        cache.put("MILK-1L:v" + v, new Entry(68, Long.MAX_VALUE));                                 // (3)!
        String current = "MILK-1L:v" + version.get("MILK-1L");
        System.out.println("versioned keys: stale entry parked under MILK-1L:v7; readers look up " + current + " -> "
                + (cache.containsKey(current) ? "hit" : "miss, load " + db.get("MILK-1L")));
    }
}
