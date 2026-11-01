import java.util.ArrayList;
import java.util.List;

/** Domain events record that something meaningful happened in an aggregate; they're dispatched after it's saved. */
public class DomainEventsDemo {

    sealed interface DomainEvent permits OrderPlaced, OrderCancelled {}                   // (1)!
    record OrderPlaced(String orderId, String customerId, long totalPaise) implements DomainEvent {}
    record OrderCancelled(String orderId, String reason) implements DomainEvent {}

    static final class Order {
        private final String id; private final String customerId; private String status = "DRAFT"; private final long total;
        private final List<DomainEvent> events = new ArrayList<>();
        Order(String id, String customerId, long total) { this.id = id; this.customerId = customerId; this.total = total; }

        void place() {
            if (!status.equals("DRAFT")) throw new IllegalStateException(status);
            status = "PLACED";
            events.add(new OrderPlaced(id, customerId, total));                           // (2)!
        }
        void cancel(String reason) {
            if (!status.equals("PLACED")) throw new IllegalStateException("only placed orders can be cancelled");
            status = "CANCELLED";
            events.add(new OrderCancelled(id, reason));
        }
        List<DomainEvent> pullEvents() { List<DomainEvent> out = List.copyOf(events); events.clear(); return out; }
    }

    static final List<String> handled = new ArrayList<>();
    static void dispatch(DomainEvent e) {
        switch (e) {
            case OrderPlaced p -> { handled.add("loyalty: +" + p.totalPaise() / 10_000 + " points for " + p.customerId()); handled.add("warehouse: pick " + p.orderId()); }
            case OrderCancelled c -> handled.add("payments: refund " + c.orderId() + " (" + c.reason() + ")");
        }
    }

    public static void main(String[] args) {
        Order order = new Order("FC-31", "C-17", 51_800);
        order.place();
        order.cancel("customer changed mind");
        System.out.println("saved order FC-31");                                         // (3)!
        order.pullEvents().forEach(DomainEventsDemo::dispatch);
        handled.forEach(h -> System.out.println("  " + h));
        System.out.println("events left on the aggregate: " + order.pullEvents().size());
    }
}
