import java.util.ArrayList;
import java.util.List;

/** Making test data readable: Object Mother for common named cases, Test Data Builder for "like the default, except...". */
public class TestDataBuilderDemo {

    record Item(String sku, int qty, long unitPaise) { }
    record Order(String id, String customerTier, String city, List<Item> items, boolean paid, String couponCode, boolean express) {
        long total() { return items.stream().mapToLong(i -> i.qty() * i.unitPaise()).sum(); }
    }

    // ---------- before: every test spells out every field ----------
    static Order before() {
        return new Order("FC-1", "silver", "Pune", List.of(new Item("MILK", 2, 6_400)), true, null, false);   // (1)!
    }

    // ---------- Object Mother: named, ready-made examples ----------
    static final class Orders {                                                                      // (2)!
        static Order typicalPaidOrder() { return anOrder().build(); }
        static Order goldExpressOrder() { return anOrder().tier("gold").express().build(); }
        static Order unpaidOrder() { return anOrder().unpaid().build(); }
    }

    // ---------- Test Data Builder: sensible defaults, override only what matters ----------
    static OrderBuilder anOrder() { return new OrderBuilder(); }
    static final class OrderBuilder {                                                                // (3)!
        private String id = "FC-1", tier = "silver", city = "Pune", coupon = null;
        private final List<Item> items = new ArrayList<>(List.of(new Item("MILK", 2, 6_400)));
        private boolean paid = true, express = false;
        OrderBuilder id(String v) { id = v; return this; }
        OrderBuilder tier(String v) { tier = v; return this; }
        OrderBuilder city(String v) { city = v; return this; }
        OrderBuilder coupon(String v) { coupon = v; return this; }
        OrderBuilder unpaid() { paid = false; return this; }
        OrderBuilder express() { express = true; return this; }
        OrderBuilder withItem(String sku, int qty, long unitPaise) { items.add(new Item(sku, qty, unitPaise)); return this; }
        OrderBuilder withOnlyItem(String sku, int qty, long unitPaise) { items.clear(); return withItem(sku, qty, unitPaise); }
        Order build() { return new Order(id, tier, city, List.copyOf(items), paid, coupon, express); }
    }

    public static void main(String[] args) {
        System.out.println("before:  " + before());
        System.out.println("mother:  " + Orders.goldExpressOrder());
        Order bigCart = anOrder().withOnlyItem("RICE-5KG", 2, 42_000).coupon("FRESH10").build();
        System.out.println("builder: total=" + bigCart.total() + " coupon=" + bigCart.couponCode() + " (everything else default)");
        System.out.println("unpaid?  " + !Orders.unpaidOrder().paid());
    }
}
