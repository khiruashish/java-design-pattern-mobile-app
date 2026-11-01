import java.util.List;
import java.util.function.Predicate;

/** Task 1 solution: coupon rules as composable specifications, each coupon a discount strategy. */
public class CouponEngineDemo {

    record Item(String sku, String category, long paise, int qty) { long total() { return paise * qty; } }
    record Cart(String customer, boolean firstOrder, List<Item> items) {
        long subtotal() { return items.stream().mapToLong(Item::total).sum(); }
    }

    interface Discount { long apply(Cart cart); }
    record Coupon(String code, Predicate<Cart> eligibility, Discount discount) {}

    static Predicate<Cart> minBasket(long paise) { return c -> c.subtotal() >= paise; }
    static Predicate<Cart> hasCategory(String cat) { return c -> c.items().stream().anyMatch(i -> i.category().equals(cat)); }
    static final Predicate<Cart> FIRST_ORDER = Cart::firstOrder;

    static Discount percentOff(int pct) { return c -> c.subtotal() * pct / 100; }
    static Discount percentOffCategory(String cat, int pct) {
        return c -> c.items().stream().filter(i -> i.category().equals(cat)).mapToLong(Item::total).sum() * pct / 100;
    }
    static Discount flat(long paise, long cap) { return c -> Math.min(paise, Math.min(cap, c.subtotal())); }

    static final List<Coupon> COUPONS = List.of(
            new Coupon("WELCOME100", FIRST_ORDER.and(minBasket(30000)), flat(10000, 10000)),
            new Coupon("DAIRY15", hasCategory("dairy").and(minBasket(20000)), percentOffCategory("dairy", 15)),
            new Coupon("BIG5", minBasket(100000), percentOff(5)));

    /** Picks the best eligible coupon; ties go to the coupon listed first. */
    static String best(Cart cart) {
        Coupon best = null;
        long bestValue = 0;
        for (Coupon c : COUPONS) {
            if (!c.eligibility().test(cart)) continue;
            long value = c.discount().apply(cart);
            if (value > bestValue) { best = c; bestValue = value; }
        }
        return best == null ? "no coupon applies" : best.code() + " saves Rs " + bestValue / 100.0;
    }

    public static void main(String[] args) {
        Cart small = new Cart("asha", false, List.of(new Item("MILK-1L", "dairy", 6800, 2)));
        Cart firstOrder = new Cart("dev", true, List.of(new Item("MILK-1L", "dairy", 6800, 3), new Item("RICE-5KG", "staples", 64000, 1)));
        Cart dairyHeavy = new Cart("chen", false, List.of(new Item("PANEER-200G", "dairy", 9000, 4), new Item("CURD-500G", "dairy", 4500, 2)));
        Cart big = new Cart("bilal", false, List.of(new Item("OIL-5L", "staples", 90000, 1), new Item("MILK-1L", "dairy", 6800, 5)));
        for (Cart c : List.of(small, firstOrder, dairyHeavy, big)) System.out.println(c.customer() + " (Rs " + c.subtotal() / 100.0 + "): " + best(c));
    }
}
