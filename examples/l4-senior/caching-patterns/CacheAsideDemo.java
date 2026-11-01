import java.util.HashMap;
import java.util.Map;

/**
 * Cache-aside (lazy loading): the application checks the cache, loads from the database on a miss and stores the
 * result. On writes it updates the database and deletes the cached entry, so the next read reloads it.
 */
public class CacheAsideDemo {

    static final Map<String, Integer> database = new HashMap<>(Map.of("MILK-1L", 68, "CURD-500G", 45));
    static final Map<String, Integer> cache = new HashMap<>();
    static int dbReads;

    static int price(String sku) {
        Integer cached = cache.get(sku);                                                           // (1)!
        if (cached != null) return cached;
        dbReads++;
        int fromDb = database.get(sku);
        cache.put(sku, fromDb);                                                                    // (2)!
        return fromDb;
    }

    static void updatePrice(String sku, int newPrice) {
        database.put(sku, newPrice);
        cache.remove(sku);                                                                         // (3)!
    }

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) price("MILK-1L");
        System.out.println("5 reads of MILK-1L -> " + dbReads + " database read");
        updatePrice("MILK-1L", 70);
        System.out.println("after update: Rs " + price("MILK-1L") + ", database reads " + dbReads);
        System.out.println("CURD-500G not cached until asked: " + cache.containsKey("CURD-500G"));
    }
}
