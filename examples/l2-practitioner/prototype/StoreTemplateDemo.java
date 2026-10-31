import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Production-grade Prototype: onboarding a new dark store by copying a template store's configuration.
 * - deep copy of configuration (zones, slots, staff roles)
 * - NEW identity for the copy; operational state (orders today, ratings) is reset, not copied
 * - secrets (payment terminal key) are never copied; the new store must enter its own
 * - the template version is recorded for audit; templates are immutable once registered
 * - validation of the customised copy before it can go live
 */
public class StoreTemplateDemo {

    record Slot(String label, int capacity) { }                                           // immutable: safe to share

    static final class StoreConfig {
        final String storeId; String city; final List<String> pincodes; final List<Slot> slots; final Map<String, Integer> staffByRole;
        String paymentTerminalKey;                                                         // secret
        int ordersToday; double rating;                                                    // operational state
        String templateRef;

        StoreConfig(String storeId, String city, List<String> pincodes, List<Slot> slots, Map<String, Integer> staff) {
            this.storeId = storeId; this.city = city; this.pincodes = new ArrayList<>(pincodes); this.slots = new ArrayList<>(slots); this.staffByRole = new TreeMap<>(staff);
        }

        /** The prototype operation: copy configuration, never identity, state or secrets. */
        StoreConfig newStoreFromThis(Supplier<String> ids, String templateVersion) {      // (1)!
            StoreConfig copy = new StoreConfig(ids.get(), city, pincodes, slots, staffByRole);   // (2)!
            copy.paymentTerminalKey = null;                                                // (3)!
            copy.ordersToday = 0; copy.rating = 0;
            copy.templateRef = storeId + "@" + templateVersion;
            return copy;
        }

        List<String> problemsBeforeGoLive() {                                              // (4)!
            List<String> p = new ArrayList<>();
            if (pincodes.isEmpty()) p.add("no pincodes");
            if (slots.stream().mapToInt(Slot::capacity).sum() < 50) p.add("total slot capacity below 50");
            if (staffByRole.getOrDefault("picker", 0) < 4) p.add("at least 4 pickers needed");
            if (paymentTerminalKey == null) p.add("payment terminal key not set");
            return p;
        }

        public String toString() {
            return storeId + " " + city + " pincodes=" + pincodes + " slots=" + slots.size() + " staff=" + staffByRole
                    + " key=" + (paymentTerminalKey == null ? "<unset>" : "****") + " orders=" + ordersToday + " from=" + templateRef;
        }
    }

    static final class TemplateRegistry {
        private final Map<String, StoreConfig> templates = new TreeMap<>();
        private final Map<String, Integer> versions = new TreeMap<>();
        void publish(String name, StoreConfig template) {
            int v = versions.merge(name, 1, Integer::sum);
            templates.put(name, template.newStoreFromThis(() -> "tpl-" + name, "v" + v));  // (5)!
        }
        StoreConfig newStore(String name, Supplier<String> ids) {
            StoreConfig t = templates.get(name);
            if (t == null) throw new IllegalArgumentException("unknown template " + name);
            return t.newStoreFromThis(ids, "v" + versions.get(name));
        }
    }

    public static void main(String[] args) {
        StoreConfig pune01 = new StoreConfig("pune-01", "pune", List.of("411001", "411004"),
                List.of(new Slot("08-10", 30), new Slot("18-20", 40)), Map.of("picker", 6, "packer", 3, "rider", 12));
        pune01.paymentTerminalKey = "pt_live_secret"; pune01.ordersToday = 842; pune01.rating = 4.6;

        TemplateRegistry registry = new TemplateRegistry();
        registry.publish("standard-dark-store", pune01);
        pune01.slots.add(new Slot("22-23", 10));                                          // later edits to pune-01 don't leak into the template

        int[] seq = {0};
        Supplier<String> ids = () -> "store-" + (++seq[0]);                                // UUIDs in production: () -> UUID.randomUUID().toString()
        StoreConfig nashik = registry.newStore("standard-dark-store", ids);
        nashik.city = "nashik"; nashik.pincodes.clear(); nashik.pincodes.add("422001");
        nashik.staffByRole.put("picker", 3);

        System.out.println("source:   " + pune01);
        System.out.println("new:      " + nashik);
        System.out.println("go-live blockers: " + nashik.problemsBeforeGoLive());
        nashik.staffByRole.put("picker", 4); nashik.paymentTerminalKey = "pt_live_nashik";
        System.out.println("go-live blockers after fixes: " + nashik.problemsBeforeGoLive());
        System.out.println("template still has " + registry.newStore("standard-dark-store", ids).slots.size() + " slots");
    }
}
