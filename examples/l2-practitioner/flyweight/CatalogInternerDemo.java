import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Production-grade Flyweight: interning repeated attribute values while loading a 200,000-SKU supplier feed.
 * - generic, thread-safe Interner<T> for immutable values (records, Strings)
 * - a cap on distinct values: a bad feed with unique junk can't grow the cache forever (falls back to no sharing)
 * - metrics: lookups, hits, distinct values, rejected (over cap) - so you can prove the win
 * - only immutable types allowed: mutable flyweights are a shared-state bug waiting to happen
 */
public class CatalogInternerDemo {

    static final class Interner<T> {
        private final Map<T, T> pool = new ConcurrentHashMap<>();
        private final int maxDistinct;
        final LongAdder lookups = new LongAdder(), hits = new LongAdder(), overCap = new LongAdder();

        Interner(Class<T> type, int maxDistinct) {
            if (!(type.isRecord() || type == String.class))                                     // (1)!
                throw new IllegalArgumentException(type.getSimpleName() + " is not a known immutable type; refuse to share it");
            this.maxDistinct = maxDistinct;
        }

        T intern(T value) {
            lookups.increment();
            T existing = pool.get(value);
            if (existing != null) { hits.increment(); return existing; }                        // (2)!
            if (pool.size() >= maxDistinct) { overCap.increment(); return value; }             // (3)!
            T winner = pool.putIfAbsent(value, value);                                          // (4)!
            if (winner != null) { hits.increment(); return winner; }
            return value;
        }

        String stats() {
            return String.format("lookups=%,d hits=%,d distinct=%d overCap=%,d hitRate=%.2f%%",
                    lookups.sum(), hits.sum(), pool.size(), overCap.sum(), 100.0 * hits.sum() / Math.max(1, lookups.sum()));   // (5)!
        }
    }

    record Attributes(String brand, String unit, String category, int gstPercent) { }
    record Sku(String code, String name, long paise, Attributes attrs) { }

    static List<Sku> load(int count, Interner<Attributes> interner, boolean junk) {
        String[] brands = {"GreenValley", "DairyDay", "FarmFresh", "DailyBake"};
        String[] units = {"kg", "L", "pcs"};
        String[] cats = {"Fruit", "Dairy", "Bakery", "Staples", "Snacks"};
        List<Sku> skus = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String brand = junk ? "Brand-" + i : brands[i % brands.length];                     // a broken feed: every row unique
            Attributes a = new Attributes(new String(brand), units[i % 3], cats[i % 5], i % 2 == 0 ? 5 : 12);   // fresh objects, like a parser makes
            skus.add(new Sku("SKU-" + i, "Item " + i, 1000 + i % 500, interner.intern(a)));
        }
        return skus;
    }

    public static void main(String[] args) {
        Interner<Attributes> attrs = new Interner<>(Attributes.class, 10_000);
        List<Sku> catalog = load(200_000, attrs, false);
        System.out.println("good feed: " + attrs.stats());
        System.out.println("rows 0 and 60 share attributes: " + (catalog.get(0).attrs() == catalog.get(60).attrs()));

        Interner<Attributes> guarded = new Interner<>(Attributes.class, 10_000);
        load(50_000, guarded, true);
        System.out.println("junk feed: " + guarded.stats());

        try { new Interner<>(StringBuilder.class, 10); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
