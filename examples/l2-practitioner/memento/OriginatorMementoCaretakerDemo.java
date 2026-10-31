import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Memento: capture an object's state without exposing its internals, so it can be restored later. */
public class OriginatorMementoCaretakerDemo {

    // ===================== Before: the caller copies internals through getters =====================
    static final class CartBefore {
        Map<String, Integer> lines = new LinkedHashMap<>(); String coupon;                      // (1)!
    }

    // ===================== After: the cart makes and restores its own mementos =====================
    static final class Cart {                                                                     // originator
        private final Map<String, Integer> lines = new LinkedHashMap<>();
        private String coupon;

        void add(String sku, int qty) { lines.merge(sku, qty, Integer::sum); }
        void applyCoupon(String c) { coupon = c; }

        static final class Memento {                                                               // (2)!
            private final Map<String, Integer> lines; private final String coupon;
            private Memento(Map<String, Integer> lines, String coupon) { this.lines = Collections.unmodifiableMap(new LinkedHashMap<>(lines)); this.coupon = coupon; }
        }
        Memento save() { return new Memento(lines, coupon); }                                      // (3)!
        void restore(Memento m) { lines.clear(); lines.putAll(m.lines); coupon = m.coupon; }
        public String toString() { return lines + (coupon == null ? "" : " coupon=" + coupon); }
    }

    public static void main(String[] args) {
        CartBefore b = new CartBefore(); b.lines.put("MILK", 1);
        Map<String, Integer> copy = b.lines;                                                       // "saved" - but it's the same map
        b.lines.put("BREAD", 1);
        System.out.println("before: the 'saved' copy changed too: " + copy);

        Cart cart = new Cart();
        Deque<Cart.Memento> history = new ArrayDeque<>();                                           // (4)!
        cart.add("MILK", 2); cart.add("BREAD", 1);
        history.push(cart.save());
        cart.add("CAKE", 1); cart.applyCoupon("BDAY20");
        System.out.println("after edits: " + cart);
        cart.restore(history.pop());
        System.out.println("restored:    " + cart);
    }
}
