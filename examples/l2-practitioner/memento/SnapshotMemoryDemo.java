import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Full snapshots copy everything every time. For big state with small edits, store changes (or share unchanged parts). */
public class SnapshotMemoryDemo {

    record PriceChange(String sku, long oldPaise, long newPaise) { }

    public static void main(String[] args) {
        Map<String, Long> prices = new HashMap<>();
        for (int i = 0; i < 10_000; i++) prices.put("SKU-" + i, 10_000L + i);

        long copiedEntries = 0;
        List<Map<String, Long>> fullSnapshots = new ArrayList<>();                                  // (1)!
        List<PriceChange> deltas = new ArrayList<>();                                               // (2)!
        for (int edit = 0; edit < 50; edit++) {
            fullSnapshots.add(Map.copyOf(prices)); copiedEntries += prices.size();
            String sku = "SKU-" + edit * 7;
            long old = prices.get(sku), now = old * 90 / 100;
            deltas.add(new PriceChange(sku, old, now));
            prices.put(sku, now);
        }
        System.out.println("50 edits, full snapshots: " + copiedEntries + " entries copied");
        System.out.println("50 edits, deltas:         " + deltas.size() + " small records");

        for (int i = deltas.size() - 1; i >= 0; i--) prices.put(deltas.get(i).sku(), deltas.get(i).oldPaise());   // (3)!
        System.out.println("undo all via deltas equals first snapshot: " + prices.equals(fullSnapshots.get(0)));
    }
}
