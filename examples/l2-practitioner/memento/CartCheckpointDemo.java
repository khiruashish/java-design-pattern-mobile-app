import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Realistic Memento: checkpoint the cart before "Add recipe ingredients", so one tap can undo the whole bulk change. */
public class CartCheckpointDemo {

    static final class Cart {
        private final Map<String, Integer> lines = new LinkedHashMap<>();
        private long deliveryFeePaise = 2_500;

        void add(String sku, int qty) { lines.merge(sku, qty, Integer::sum); recalcFee(); }
        private void recalcFee() { deliveryFeePaise = lines.size() >= 5 ? 0 : 2_500; }               // derived state also restored

        record Checkpoint(Map<String, Integer> lines, long fee) { }                                 // (1)!
        Checkpoint checkpoint() { return new Checkpoint(Collections.unmodifiableMap(new LinkedHashMap<>(lines)), deliveryFeePaise); }
        void rollback(Checkpoint c) { lines.clear(); lines.putAll(c.lines()); deliveryFeePaise = c.fee(); }   // (2)!
        public String toString() { return lines + " fee Rs " + deliveryFeePaise / 100; }
    }

    static void addRecipe(Cart cart, List<String> ingredients) { ingredients.forEach(i -> cart.add(i, 1)); }

    public static void main(String[] args) {
        Cart cart = new Cart();
        cart.add("MILK-1L", 2);
        cart.add("PANEER-200G", 1);
        System.out.println("cart:         " + cart);

        Cart.Checkpoint before = cart.checkpoint();                                                  // (3)!
        addRecipe(cart, List.of("TOMATO-1KG", "ONION-1KG", "PANEER-200G", "BUTTER-100G", "CREAM-200ML"));
        System.out.println("after recipe: " + cart);
        cart.rollback(before);
        System.out.println("undo recipe:  " + cart);
    }
}
