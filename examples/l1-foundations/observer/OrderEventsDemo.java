import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Realistic Observer: typed order events (sealed) with several independent listeners. */
public class OrderEventsDemo {

    sealed interface OrderEvent permits Placed, Cancelled, Delivered { String orderId(); }   // (1)!
    record Placed(String orderId, String customerId, long totalCents, List<String> skus) implements OrderEvent { }
    record Cancelled(String orderId, String reason) implements OrderEvent { }
    record Delivered(String orderId, String customerId, int minutesTaken) implements OrderEvent { }

    static final class OrderEvents {
        private final List<Consumer<OrderEvent>> listeners = new ArrayList<>();
        void register(Consumer<OrderEvent> l) { listeners.add(l); }
        void publish(OrderEvent e) { listeners.forEach(l -> l.accept(e)); }
    }

    /** Reserves and releases stock. */
    static void inventory(OrderEvent e) {
        switch (e) {                                                                      // (2)!
            case Placed p -> System.out.println("inventory: reserve " + p.skus());
            case Cancelled c -> System.out.println("inventory: release stock for " + c.orderId());
            case Delivered d -> { }                                                       // nothing to do
        }
    }

    /** Awards loyalty points on delivery; ignores the other events. */
    static void loyalty(OrderEvent e) {
        if (e instanceof Delivered d) System.out.println("loyalty: points for " + d.customerId());   // (3)!
    }

    /** Tracks late deliveries for operations. */
    static void slaMonitor(OrderEvent e) {
        if (e instanceof Delivered d && d.minutesTaken() > 30) System.out.println("sla: " + d.orderId() + " late by " + (d.minutesTaken() - 30) + " min");
    }

    public static void main(String[] args) {
        OrderEvents events = new OrderEvents();
        events.register(OrderEventsDemo::inventory);                                     // (4)!
        events.register(OrderEventsDemo::loyalty);
        events.register(OrderEventsDemo::slaMonitor);

        events.publish(new Placed("FC-1301", "cust-7", 84_000, List.of("RICE-5KG", "OIL-1L")));
        events.publish(new Delivered("FC-1301", "cust-7", 38));
        events.publish(new Cancelled("FC-1302", "customer request"));
    }
}
