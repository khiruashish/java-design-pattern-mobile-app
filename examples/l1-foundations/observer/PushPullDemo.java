import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Push model (event carries the data) vs pull model (observer is told "something changed" and asks). */
public class PushPullDemo {

    // ===================== PUSH: the event carries what changed =====================
    record StockChanged(String sku, int oldQty, int newQty) { }                            // (1)!

    static final class PushInventory {
        private final List<Consumer<StockChanged>> listeners = new ArrayList<>();
        void onChange(Consumer<StockChanged> l) { listeners.add(l); }
        void set(String sku, int oldQty, int newQty) { listeners.forEach(l -> l.accept(new StockChanged(sku, oldQty, newQty))); }
    }

    // ===================== PULL: the observer gets the subject and reads what it needs =====================
    interface InventoryView { int quantity(String sku); int lowStockThreshold(); }

    static final class PullInventory implements InventoryView {
        private final java.util.Map<String, Integer> stock = new java.util.HashMap<>();
        private final List<Consumer<InventoryView>> listeners = new ArrayList<>();
        void onChange(Consumer<InventoryView> l) { listeners.add(l); }
        void set(String sku, int qty) { stock.put(sku, qty); listeners.forEach(l -> l.accept(this)); }   // (2)!
        public int quantity(String sku) { return stock.getOrDefault(sku, 0); }
        public int lowStockThreshold() { return 5; }
    }

    public static void main(String[] args) {
        PushInventory push = new PushInventory();
        push.onChange(e -> { if (e.newQty() < 5) System.out.println("push: reorder " + e.sku() + " (" + e.oldQty() + " -> " + e.newQty() + ")"); });
        push.set("EGGS-12", 9, 3);

        PullInventory pull = new PullInventory();
        pull.onChange(view -> {                                                           // (3)!
            int eggs = view.quantity("EGGS-12");
            if (eggs < view.lowStockThreshold()) System.out.println("pull: reorder EGGS-12, now " + eggs + ", threshold " + view.lowStockThreshold());
        });
        pull.set("EGGS-12", 3);
        pull.set("MILK-1L", 40);                                                          // pull observer re-checks eggs needlessly
    }
}
