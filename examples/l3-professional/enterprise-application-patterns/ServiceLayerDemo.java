import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A Service Layer defines the application's use cases and owns the transaction around each one. */
public class ServiceLayerDemo {

    static final List<String> log = new ArrayList<>();

    /** Stands in for Spring's @Transactional: begin, then commit or roll back. */
    static <T> T inTransaction(String name, java.util.function.Supplier<T> work) {   // (1)!
        log.add("BEGIN " + name);
        try {
            T result = work.get();
            log.add("COMMIT");
            return result;
        } catch (RuntimeException e) {
            log.add("ROLLBACK (" + e.getMessage() + ")");
            throw e;
        }
    }

    record Order(String id, String customer, long total) {}

    interface OrderRepository { void save(Order o); }
    interface StockGateway { void reserve(String sku, int qty); }
    interface Notifier { void orderPlaced(Order o); }

    /** Thin coordination only. Pricing rules belong to the domain, not here. */
    static final class CheckoutService {                              // (2)!
        private final OrderRepository orders;
        private final StockGateway stock;
        private final Notifier notifier;
        private int nextId = 1;

        CheckoutService(OrderRepository orders, StockGateway stock, Notifier notifier) {
            this.orders = orders; this.stock = stock; this.notifier = notifier;
        }

        String placeOrder(String customer, String sku, int qty, long unitPrice) {
            Order order = inTransaction("placeOrder", () -> {
                stock.reserve(sku, qty);
                Order o = new Order("ORD-" + nextId++, customer, unitPrice * qty);
                orders.save(o);
                return o;
            });
            notifier.orderPlaced(order);                              // (3)!
            return order.id();
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> shelf = new LinkedHashMap<>(Map.of("PANEER-200G", 3));
        Map<String, Order> table = new LinkedHashMap<>();
        CheckoutService service = new CheckoutService(
                o -> { table.put(o.id(), o); log.add("INSERT order " + o.id()); },
                (sku, qty) -> {
                    int left = shelf.getOrDefault(sku, 0);
                    if (left < qty) throw new IllegalStateException("only " + left + " " + sku);
                    shelf.put(sku, left - qty);
                    log.add("UPDATE stock " + sku + " -> " + (left - qty));
                },
                o -> log.add("SMS to " + o.customer() + ": " + o.id() + " placed"));

        System.out.println("placed " + service.placeOrder("asha", "PANEER-200G", 2, 9_500));
        try {
            service.placeOrder("ravi", "PANEER-200G", 5, 9_500);
        } catch (IllegalStateException e) {
            System.out.println("failed: " + e.getMessage());
        }
        log.forEach(line -> System.out.println("  " + line));
        System.out.println("orders saved: " + table.keySet());
    }
}
