import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The same "place order" rule written as a Transaction Script and as a Domain Model. */
public class TransactionScriptVsDomainModelDemo {

    // ---- Before: Transaction Script. One procedure per use case, data in plain maps. ----
    static Map<String, Object> placeOrderScript(List<Map<String, Object>> lines, String coupon) {   // (1)!
        long total = 0;
        for (Map<String, Object> line : lines) {
            long price = (Long) line.get("price");
            int qty = (Integer) line.get("qty");
            total += price * qty;
        }
        if ("FRESH10".equals(coupon) && total >= 50_000) total -= total / 10;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "PLACED");
        row.put("total", total);
        return row;   // nothing stops another script setting qty = -3 or status = "SHIPPED" twice
    }

    // ---- After: Domain Model. Data and the rules that protect it live together. ----
    record Line(String sku, long price, int qty) {
        Line {
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive: " + sku);
        }
        long amount() { return price * qty; }
    }

    static final class Order {                                       // (2)!
        enum Status { DRAFT, PLACED, CANCELLED }
        private final List<Line> lines = new ArrayList<>();
        private String coupon;
        private Status status = Status.DRAFT;

        void add(String sku, long price, int qty) {
            requireDraft();
            lines.add(new Line(sku, price, qty));
        }
        void applyCoupon(String code) { requireDraft(); coupon = code; }
        long subtotal() { return lines.stream().mapToLong(Line::amount).sum(); }
        long total() {
            long s = subtotal();
            return "FRESH10".equals(coupon) && s >= 50_000 ? s - s / 10 : s;
        }
        void place() {                                               // (3)!
            requireDraft();
            if (lines.isEmpty()) throw new IllegalStateException("empty order");
            status = Status.PLACED;
        }
        Status status() { return status; }
        private void requireDraft() {
            if (status != Status.DRAFT) throw new IllegalStateException("order is " + status);
        }
    }

    public static void main(String[] args) {
        var scriptResult = placeOrderScript(List.of(
                Map.of("sku", "MILK-1L", "price", 6_800L, "qty", 4),
                Map.of("sku", "ATTA-5KG", "price", 32_000L, "qty", 1)), "FRESH10");
        System.out.println("script: " + scriptResult);

        Order order = new Order();
        order.add("MILK-1L", 6_800, 4);
        order.add("ATTA-5KG", 32_000, 1);
        order.applyCoupon("FRESH10");
        order.place();
        System.out.println("domain: status=" + order.status() + ", total=" + order.total());

        try {
            order.add("EGGS-12", 9_000, 1);
        } catch (IllegalStateException e) {
            System.out.println("domain refuses: " + e.getMessage());
        }
        try {
            new Order().add("EGGS-12", 9_000, -3);
        } catch (IllegalArgumentException e) {
            System.out.println("domain refuses: " + e.getMessage());
        }
    }
}
