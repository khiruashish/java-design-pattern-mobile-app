import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Contract tests: ONE set of tests describing what every implementation of an interface must do,
 * run against EACH implementation. Here: a price cache with a plain map, a bounded LRU, and a buggy "optimised" version.
 */
public class ContractTestDemo {

    interface PriceCache {
        void put(String sku, long paise);
        Optional<Long> get(String sku);
        void evict(String sku);
    }

    static final class MapCache implements PriceCache {
        private final Map<String, Long> m = new LinkedHashMap<>();
        public void put(String s, long p) { m.put(s, p); }
        public Optional<Long> get(String s) { return Optional.ofNullable(m.get(s)); }
        public void evict(String s) { m.remove(s); }
    }

    static final class LruCache implements PriceCache {
        private final Map<String, Long> m = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 100; }
        };
        public void put(String s, long p) { m.put(s, p); }
        public Optional<Long> get(String s) { return Optional.ofNullable(m.get(s)); }
        public void evict(String s) { m.remove(s); }
    }

    /** "Optimised": ignores a put when the key exists, to save writes. Breaks price updates. */
    static final class WriteOnceCache implements PriceCache {
        private final Map<String, Long> m = new LinkedHashMap<>();
        public void put(String s, long p) { m.putIfAbsent(s, p); }
        public Optional<Long> get(String s) { return Optional.ofNullable(m.get(s)); }
        public void evict(String s) { m.remove(s); }
    }

    /** The contract, written once against the interface. */
    record Contract(Supplier<PriceCache> factory) {                                                     // (1)!
        List<String> run() {
            List<String> failures = new ArrayList<>();
            check(failures, "missing key is empty", () -> factory.get().get("NOPE").isEmpty());
            check(failures, "put then get returns the price", () -> { var c = factory.get(); c.put("MILK", 6_400); return c.get("MILK").equals(Optional.of(6_400L)); });
            check(failures, "a second put replaces the price", () -> { var c = factory.get(); c.put("MILK", 6_400); c.put("MILK", 6_800); return c.get("MILK").equals(Optional.of(6_800L)); });   // (2)!
            check(failures, "evict removes the entry", () -> { var c = factory.get(); c.put("MILK", 6_400); c.evict("MILK"); return c.get("MILK").isEmpty(); });
            return failures;
        }
        static void check(List<String> failures, String name, Supplier<Boolean> test) { if (!test.get()) failures.add(name); }
    }

    public static void main(String[] args) {
        Map<String, Supplier<PriceCache>> impls = new LinkedHashMap<>();
        impls.put("MapCache", MapCache::new);
        impls.put("LruCache", LruCache::new);
        impls.put("WriteOnceCache", WriteOnceCache::new);
        impls.forEach((name, factory) -> {                                                              // (3)!
            List<String> failures = new Contract(factory).run();
            System.out.println(name + ": " + (failures.isEmpty() ? "meets the contract" : "BREAKS " + failures));
        });
    }
}
