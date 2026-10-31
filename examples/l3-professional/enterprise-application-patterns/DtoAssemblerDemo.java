import java.util.List;

/** DTOs carry exactly the data a boundary needs; an assembler converts between DTOs and domain objects. */
public class DtoAssemblerDemo {

    // Domain objects: rich, with internal data that must not leak.
    record Line(String sku, String name, int qty, long pricePaise, long costPaise) {}
    record Order(String id, String customerPhone, String address, List<Line> lines, String internalFraudScore) {
        long total() { return lines.stream().mapToLong(l -> l.qty() * l.pricePaise()).sum(); }
    }

    // DTOs: flat, immutable, shaped for one screen or API.
    record OrderSummaryDto(String orderId, String maskedPhone, int items, String total) {}       // (1)!
    record PlaceOrderRequest(String phone, String address, List<String> skus) {}                 // (2)!

    static final class OrderAssembler {                                                          // (3)!
        static OrderSummaryDto toSummary(Order o) {
            String phone = o.customerPhone();
            String masked = "******" + phone.substring(phone.length() - 4);
            int items = o.lines().stream().mapToInt(Line::qty).sum();
            return new OrderSummaryDto(o.id(), masked, items, "Rs " + o.total() / 100);
        }
        static Order fromRequest(String newId, PlaceOrderRequest r, java.util.function.Function<String, Line> catalog) {
            if (r.skus().isEmpty()) throw new IllegalArgumentException("no items");
            return new Order(newId, r.phone(), r.address(), r.skus().stream().map(catalog).toList(), "unscored");
        }
    }

    public static void main(String[] args) {
        var request = new PlaceOrderRequest("9876543210", "12 MG Road", List.of("MILK-1L", "MILK-1L", "BREAD"));
        Order order = OrderAssembler.fromRequest("ORD-77", request,
                sku -> sku.equals("BREAD") ? new Line(sku, "Bread", 1, 4_500, 3_100) : new Line(sku, "Milk 1L", 1, 6_800, 5_900));
        System.out.println("domain has cost and fraud data: " + (order.lines().get(0).costPaise() > 0) + ", " + order.internalFraudScore());
        System.out.println("api returns: " + OrderAssembler.toSummary(order));
    }
}
