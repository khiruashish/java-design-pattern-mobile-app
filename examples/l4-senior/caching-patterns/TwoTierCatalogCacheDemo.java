import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Production-grade: catalog lookups go through a small in-process cache (L1, per app instance) and a shared cache
 * (L2, a Redis stand-in) before the database. Writes delete the L2 entry and broadcast an invalidation so every
 * instance drops its L1 copy. Missing SKUs are cached briefly too, and keys carry a schema version.
 */
public class TwoTierCatalogCacheDemo {

    record Product(String sku, String name, int price) {}
    record Entry(Optional<Product> value, long expiresAt) {}

    static final class SharedCache {                                                              // (1)!
        final Map<String, Entry> entries = new HashMap<>();
        final List<AppInstance> subscribers = new ArrayList<>();
        void publishInvalidation(String key) { subscribers.forEach(s -> s.onInvalidate(key)); }
    }

    static final class Database {
        final Map<String, Product> rows = new HashMap<>();
        int reads;
        Optional<Product> find(String sku) { reads++; return Optional.ofNullable(rows.get(sku)); }
    }

    static final class AppInstance {
        static final String KEY_PREFIX = "catalog:v2:";                                           // (2)!
        static final long L1_TTL = 30_000, L2_TTL = 600_000, NEGATIVE_TTL = 60_000;
        final String name;
        private final SharedCache l2; private final Database db;
        private final Map<String, Entry> l1 = new LinkedHashMap<>(16, 0.75f, true) {             // (3)!
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) { return size() > 3; }
        };
        AppInstance(String name, SharedCache l2, Database db) { this.name = name; this.l2 = l2; this.db = db; l2.subscribers.add(this); }

        String get(String sku, long now) {
            String key = KEY_PREFIX + sku;
            Entry e = l1.get(key);
            if (e != null && now < e.expiresAt()) return describe(sku, e, "L1");
            e = l2.entries.get(key);
            if (e != null && now < e.expiresAt()) { l1.put(key, new Entry(e.value(), Math.min(e.expiresAt(), now + L1_TTL))); return describe(sku, e, "L2"); }
            Optional<Product> loaded = db.find(sku);
            Entry fresh = new Entry(loaded, now + (loaded.isPresent() ? L2_TTL : NEGATIVE_TTL));       // (4)!
            l2.entries.put(key, fresh);
            l1.put(key, new Entry(loaded, Math.min(fresh.expiresAt(), now + L1_TTL)));
            return describe(sku, fresh, "DB");
        }

        void updatePrice(String sku, int price) {
            Product p = db.rows.get(sku);
            db.rows.put(sku, new Product(sku, p.name(), price));
            l2.entries.remove(KEY_PREFIX + sku);                                                  // (5)!
            l2.publishInvalidation(KEY_PREFIX + sku);
        }

        void onInvalidate(String key) { l1.remove(key); }

        private String describe(String sku, Entry e, String source) {
            return name + " " + sku + " from " + source + ": " + e.value().map(p -> p.name() + " Rs " + p.price()).orElse("not found");
        }
    }

    public static void main(String[] args) {
        Database db = new Database();
        db.rows.put("MILK-1L", new Product("MILK-1L", "DairyDay Toned Milk 1L", 68));
        db.rows.put("CURD-500G", new Product("CURD-500G", "DairyDay Curd 500g", 45));
        SharedCache redis = new SharedCache();
        AppInstance a = new AppInstance("app-a", redis, db), b = new AppInstance("app-b", redis, db);

        System.out.println(a.get("MILK-1L", 0));
        System.out.println(a.get("MILK-1L", 1_000));
        System.out.println(b.get("MILK-1L", 2_000));
        System.out.println(a.get("NOSUCH-1", 3_000));
        System.out.println(b.get("NOSUCH-1", 4_000));
        a.updatePrice("MILK-1L", 70);
        System.out.println(b.get("MILK-1L", 5_000));
        System.out.println(a.get("MILK-1L", 6_000));
        System.out.println(b.get("MILK-1L", 40_000));
        System.out.println("database reads: " + db.reads + " for 8 lookups");
    }
}
