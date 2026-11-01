import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Read-through and write-through: the cache sits in front of the store. Callers only talk to the cache; it loads
 * missing entries itself and writes changes through to the store before acknowledging.
 */
public class ReadWriteThroughDemo {

    interface Store { String load(String key); void save(String key, String value); }

    static final class ThroughCache {
        private final Map<String, String> entries = new HashMap<>();
        private final Store store;
        ThroughCache(Store store) { this.store = store; }
        String get(String key) { return entries.computeIfAbsent(key, store::load); }                // (1)!
        void put(String key, String value) {
            store.save(key, value);                                                                 // (2)!
            entries.put(key, value);                                                                // (3)!
        }
    }

    public static void main(String[] args) {
        Map<String, String> table = new HashMap<>(Map.of("C-1", "Koramangala, BLR"));
        List<String> storeCalls = new ArrayList<>();
        Store addresses = new Store() {
            public String load(String k) { storeCalls.add("load " + k); return table.get(k); }
            public void save(String k, String v) { storeCalls.add("save " + k); table.put(k, v); }
        };
        ThroughCache cache = new ThroughCache(addresses);
        System.out.println(cache.get("C-1"));
        System.out.println(cache.get("C-1"));
        cache.put("C-1", "Indiranagar, BLR");
        System.out.println(cache.get("C-1"));
        System.out.println("store calls: " + storeCalls);
    }
}
