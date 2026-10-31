import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Minimal Facade: "Reorder" in the app is one call; behind it are order history, catalog and basket. */
public class ReorderFacadeDemo {

    static final class OrderHistory { List<String> lastOrderSkus(String customer) { return List.of("MILK-1L", "BREAD", "PANEER-200"); } }
    static final class Catalog { Set<String> available() { return Set.of("MILK-1L", "BREAD"); } }
    static final class Basket {
        final Map<String, List<String>> baskets = new java.util.HashMap<>();
        void add(String customer, String sku) { baskets.computeIfAbsent(customer, c -> new ArrayList<>()).add(sku); }
    }

    record ReorderResult(List<String> added, List<String> unavailable) { }

    static final class ReorderFacade {
        private final OrderHistory history; private final Catalog catalog; private final Basket basket;
        ReorderFacade(OrderHistory h, Catalog c, Basket b) { history = h; catalog = c; basket = b; }

        ReorderResult reorder(String customer) {                                          // (1)!
            List<String> added = new ArrayList<>(), missing = new ArrayList<>();
            Set<String> available = catalog.available();
            for (String sku : history.lastOrderSkus(customer)) {
                if (available.contains(sku)) { basket.add(customer, sku); added.add(sku); } else missing.add(sku);
            }
            return new ReorderResult(added, missing);                                     // (2)!
        }
    }

    public static void main(String[] args) {
        Basket basket = new Basket();
        ReorderFacade reorder = new ReorderFacade(new OrderHistory(), new Catalog(), basket);
        System.out.println(reorder.reorder("asha"));
        System.out.println("basket: " + basket.baskets);
    }
}
