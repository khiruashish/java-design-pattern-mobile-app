import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FreshCart's cart as an aggregate: value objects for money and quantities, invariants enforced by the root
 * (per-item limits, one active coupon, frozen after checkout), and domain events recorded for other contexts.
 */
public class CartAggregateDemo {

    record Money(long paise) {
        Money { if (paise < 0) throw new IllegalArgumentException("negative money"); }
        Money plus(Money o) { return new Money(paise + o.paise); }
        Money times(int q) { return new Money(paise * q); }
        @Override public String toString() { return "Rs " + paise / 100; }
    }
    record Sku(String code) {}
    record Line(Sku sku, int qty, Money unitPrice) { Money amount() { return unitPrice.times(qty); } }

    sealed interface CartEvent permits ItemAdded, CouponApplied, CheckedOut {}
    record ItemAdded(String cartId, String sku, int qty) implements CartEvent {}
    record CouponApplied(String cartId, String code) implements CartEvent {}
    record CheckedOut(String cartId, long totalPaise) implements CartEvent {}

    static final class Cart {
        static final Map<String, Integer> PER_ITEM_LIMIT = Map.of("MANGO-1KG", 2);
        private final String id;
        private final Map<Sku, Line> lines = new LinkedHashMap<>();                       // (1)!
        private String coupon; private boolean checkedOut;
        private final List<CartEvent> events = new ArrayList<>();
        Cart(String id) { this.id = id; }

        void add(Sku sku, int qty, Money unitPrice) {
            requireOpen();
            int newQty = qty + (lines.containsKey(sku) ? lines.get(sku).qty() : 0);
            int limit = PER_ITEM_LIMIT.getOrDefault(sku.code(), 10);
            if (newQty > limit) throw new IllegalStateException("limit " + limit + " for " + sku.code());   // (2)!
            lines.put(sku, new Line(sku, newQty, unitPrice));
            events.add(new ItemAdded(id, sku.code(), qty));
        }
        void applyCoupon(String code) {
            requireOpen();
            if (coupon != null) throw new IllegalStateException("only one coupon per order (" + coupon + " applied)");
            coupon = code;
            events.add(new CouponApplied(id, code));
        }
        Money total() {
            Money subtotal = lines.values().stream().map(Line::amount).reduce(new Money(0), Money::plus);
            return "FRESH10".equals(coupon) ? new Money(subtotal.paise() * 90 / 100) : subtotal;
        }
        void checkout() {
            requireOpen();
            if (lines.isEmpty()) throw new IllegalStateException("cart is empty");
            checkedOut = true;
            events.add(new CheckedOut(id, total().paise()));                              // (3)!
        }
        List<Line> lines() { return Collections.unmodifiableList(new ArrayList<>(lines.values())); }
        List<CartEvent> pullEvents() { var out = List.copyOf(events); events.clear(); return out; }
        private void requireOpen() { if (checkedOut) throw new IllegalStateException("cart " + id + " is checked out"); }
    }

    public static void main(String[] args) {
        Cart cart = new Cart("CART-5");
        cart.add(new Sku("MANGO-1KG"), 1, new Money(45_000));
        cart.add(new Sku("MILK-1L"), 3, new Money(6_800));
        for (Runnable r : List.<Runnable>of(
                () -> cart.add(new Sku("MANGO-1KG"), 2, new Money(45_000)),
                () -> cart.applyCoupon("FRESH10"),
                () -> cart.applyCoupon("SAVE50"),
                cart::checkout,
                () -> cart.add(new Sku("EGGS-12"), 1, new Money(9_000)))) {
            try { r.run(); } catch (IllegalStateException e) { System.out.println("refused: " + e.getMessage()); }
        }
        System.out.println("lines: " + cart.lines().stream().map(l -> l.qty() + "x" + l.sku().code()).toList() + ", total " + cart.total());
        cart.pullEvents().forEach(e -> System.out.println("event: " + e));
    }
}
