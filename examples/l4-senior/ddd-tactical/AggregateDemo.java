import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An Aggregate is a cluster of objects changed together through one root that enforces the invariants. Outsiders
 * hold a reference only to the root, and refer to other aggregates by ID.
 */
public class AggregateDemo {

    record OrderLine(String sku, int qty, long pricePaise) {}

    static final class Order {                                                            // (1)!
        static final int MAX_ITEMS = 30;
        static final long MAX_COD_PAISE = 200_000;
        private final String id; private final String customerId;                         // (2)!
        private final List<OrderLine> lines = new ArrayList<>();
        private boolean cashOnDelivery; private boolean placed;

        Order(String id, String customerId, boolean cod) { this.id = id; this.customerId = customerId; this.cashOnDelivery = cod; }

        void add(String sku, int qty, long price) {
            requireDraft();
            if (itemCount() + qty > MAX_ITEMS) throw new IllegalStateException("max " + MAX_ITEMS + " items per order");
            if (cashOnDelivery && total() + qty * price > MAX_COD_PAISE) throw new IllegalStateException("COD orders are limited to Rs 2000");
            lines.add(new OrderLine(sku, qty, price));
        }
        void place() { requireDraft(); if (lines.isEmpty()) throw new IllegalStateException("empty order"); placed = true; }

        List<OrderLine> lines() { return Collections.unmodifiableList(lines); }           // (3)!
        int itemCount() { return lines.stream().mapToInt(OrderLine::qty).sum(); }
        long total() { return lines.stream().mapToLong(l -> l.qty() * l.pricePaise()).sum(); }
        private void requireDraft() { if (placed) throw new IllegalStateException("order " + id + " already placed"); }
    }

    static void attempt(String label, Runnable r) {
        try { r.run(); System.out.println(label + ": ok"); }
        catch (RuntimeException e) { System.out.println(label + ": refused (" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")"); }
    }

    public static void main(String[] args) {
        Order order = new Order("FC-9", "C-17", true);
        attempt("add 2 ghee", () -> order.add("GHEE-1L", 2, 70_000));
        attempt("add 1 more ghee (COD limit)", () -> order.add("GHEE-1L", 1, 70_000));
        attempt("add 40 eggs (item limit)", () -> order.add("EGG", 40, 700));
        attempt("bypass via lines().add", () -> order.lines().add(new OrderLine("FREE", 99, 0)));
        attempt("place", order::place);
        attempt("add after placing", () -> order.add("MILK-1L", 1, 6_800));
        System.out.println("final: " + order.itemCount() + " items, total " + order.total());
    }
}
