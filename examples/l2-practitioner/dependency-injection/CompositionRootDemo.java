import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** The composition root: the ONE place where the object graph is built. Everything else just receives dependencies. */
public class CompositionRootDemo {

    interface OrderRepository { void save(String orderId); List<String> all(); }
    interface Notifier { void send(String msg); }

    static final class InMemoryOrders implements OrderRepository {
        private final List<String> orders = new ArrayList<>();
        public void save(String id) { orders.add(id); }
        public List<String> all() { return orders; }
    }
    static final class ConsoleNotifier implements Notifier { public void send(String m) { System.out.println("  notify: " + m); } }

    static final class OrderService {                                                     // (1)!
        private final OrderRepository orders; private final Notifier notifier; private final Clock clock;
        OrderService(OrderRepository orders, Notifier notifier, Clock clock) { this.orders = orders; this.notifier = notifier; this.clock = clock; }
        void place(String id) { orders.save(id); notifier.send(id + " placed at " + Instant.now(clock)); }
    }

    static final class ReportService {
        private final OrderRepository orders;
        ReportService(OrderRepository orders) { this.orders = orders; }
        String daily() { return orders.all().size() + " orders"; }
    }

    /** Composition root: knows every concrete class; nothing else does. */
    record App(OrderService orderService, ReportService reports) {
        static App wire() {                                                               // (2)!
            OrderRepository orders = new InMemoryOrders();                                // (3)!
            Clock clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
            return new App(new OrderService(orders, new ConsoleNotifier(), clock), new ReportService(orders));
        }
    }

    public static void main(String[] args) {
        App app = App.wire();
        app.orderService().place("FC-2801");
        app.orderService().place("FC-2802");
        System.out.println("report: " + app.reports().daily() + " (both services share one repository)");
    }
}
