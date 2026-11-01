import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: moving loyalty points from the legacy database to the new Loyalty service without downtime.
 * Legacy stays the source of truth. Every write copies the customer's absolute balance (not the delta) to the new
 * store, backfill copies balances in batches, failed copies are retried by customer ID, and a reconciliation
 * compares both stores before reads switch over. Copying absolute values makes every step idempotent.
 */
public class LoyaltyMigrationDemo {

    static final Map<String, Integer> legacy = new TreeMap<>(), loyaltyService = new TreeMap<>();
    static boolean newServiceDown = false;
    static final List<String> retryQueue = new ArrayList<>();

    static void award(String customer, int points) {                                                   // (1)!
        legacy.merge(customer, points, Integer::sum);
        if (newServiceDown) { retryQueue.add(customer); return; }
        loyaltyService.put(customer, legacy.get(customer));
    }

    static void backfill(int batchSize) {                                                              // (2)!
        int copied = 0;
        for (var e : legacy.entrySet()) {
            if (copied == batchSize) break;
            if (!e.getValue().equals(loyaltyService.get(e.getKey()))) { loyaltyService.put(e.getKey(), e.getValue()); copied++; }
        }
        System.out.println("backfill batch copied " + copied + " balance(s)");
    }

    static List<String> reconcile() {                                                                 // (3)!
        List<String> mismatches = new ArrayList<>();
        legacy.forEach((c, p) -> { Integer q = loyaltyService.get(c); if (!p.equals(q)) mismatches.add(c + " legacy=" + p + " new=" + q); });
        return mismatches;
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 5; i++) legacy.put("C-" + i, i * 100);                 // existing balances before dual write
        award("C-2", 20);                                                            // dual write starts
        backfill(3);
        backfill(3);
        newServiceDown = true;
        award("C-6", 50);                                                            // a new customer; the copy fails
        newServiceDown = false;
        award("C-2", 5);                                                             // writes keep arriving during the migration
        System.out.println("mismatches: " + reconcile());
        retryQueue.forEach(c -> loyaltyService.put(c, legacy.get(c)));
        retryQueue.clear();
        System.out.println("after replaying the retry queue: " + reconcile() + " -> safe to switch reads");
    }
}
