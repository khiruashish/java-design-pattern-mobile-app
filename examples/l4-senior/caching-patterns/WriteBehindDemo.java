import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Write-behind (write-back): writes go to the cache and are acknowledged immediately; the store is updated later in
 * batches. Fast and coalescing, but anything not yet flushed is lost if the cache node dies.
 */
public class WriteBehindDemo {

    static final class WriteBehindCache {
        final Map<String, Integer> entries = new LinkedHashMap<>();
        private final Map<String, Integer> dirty = new LinkedHashMap<>();                          // (1)!
        final List<String> storeWrites = new ArrayList<>();
        void put(String key, int value) { entries.put(key, value); dirty.put(key, value); }
        void flush() {                                                                             // (2)!
            if (dirty.isEmpty()) return;
            storeWrites.add("batch " + dirty);
            dirty.clear();
        }
        int unflushed() { return dirty.size(); }
    }

    public static void main(String[] args) {
        WriteBehindCache viewCounts = new WriteBehindCache();
        for (int i = 1; i <= 100; i++) viewCounts.put("views:MILK-1L", i);
        for (int i = 1; i <= 40; i++) viewCounts.put("views:CURD-500G", i);
        viewCounts.flush();
        System.out.println("140 writes became " + viewCounts.storeWrites.size() + " store write: " + viewCounts.storeWrites);
        viewCounts.put("views:MILK-1L", 101);
        viewCounts.put("views:EGGS-12", 1);
        System.out.println("node crashes with " + viewCounts.unflushed() + " unflushed entries: those updates are lost");   // (3)!
    }
}
