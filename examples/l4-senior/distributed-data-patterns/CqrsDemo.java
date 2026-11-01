import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * CQRS: commands go to a write model that enforces rules; queries read from a separate model shaped for the
 * screen, updated from events. The read side can lag behind the write side.
 */
public class CqrsDemo {

    record OrderPlaced(String orderId, String customerId, long totalPaise) {}
    record OrderCancelled(String orderId) {}

    // ----- write side: rules, normalised -----
    static final class OrderCommands {                                                            // (1)!
        private final Map<String, String> status = new LinkedHashMap<>();
        final List<Object> events = new ArrayList<>();
        void place(String orderId, String customerId, long totalPaise) {
            if (totalPaise < 9900) throw new IllegalArgumentException("minimum order is Rs 99");
            status.put(orderId, "PLACED"); events.add(new OrderPlaced(orderId, customerId, totalPaise));
        }
        void cancel(String orderId) {
            if (!"PLACED".equals(status.get(orderId))) throw new IllegalStateException(orderId + " can't be cancelled");
            status.put(orderId, "CANCELLED"); events.add(new OrderCancelled(orderId));
        }
    }

    // ----- read side: denormalised for "My orders" -----
    record CustomerSummary(int activeOrders, long activeSpendPaise) {}
    static final class CustomerSummaryProjection {                                                // (2)!
        final Map<String, CustomerSummary> byCustomer = new TreeMap<>();
        private final Map<String, OrderPlaced> placed = new LinkedHashMap<>();
        int applied;
        void apply(Object e) {
            applied++;
            if (e instanceof OrderPlaced p) { placed.put(p.orderId(), p); adjust(p.customerId(), 1, p.totalPaise()); }
            if (e instanceof OrderCancelled c) { OrderPlaced p = placed.get(c.orderId()); adjust(p.customerId(), -1, -p.totalPaise()); }
        }
        private void adjust(String customer, int orders, long paise) {
            CustomerSummary s = byCustomer.getOrDefault(customer, new CustomerSummary(0, 0));
            byCustomer.put(customer, new CustomerSummary(s.activeOrders() + orders, s.activeSpendPaise() + paise));
        }
    }

    public static void main(String[] args) {
        OrderCommands commands = new OrderCommands();
        CustomerSummaryProjection view = new CustomerSummaryProjection();
        commands.place("FC-1", "C-1", 24_000);
        commands.place("FC-2", "C-1", 13_600);
        commands.place("FC-3", "C-2", 9_900);
        try { commands.place("FC-4", "C-2", 5_000); } catch (IllegalArgumentException e) { System.out.println("command rejected: " + e.getMessage()); }
        commands.cancel("FC-2");

        for (int i = 0; i < 3; i++) view.apply(commands.events.get(i));                           // (3)!
        System.out.println("read model after 3 of " + commands.events.size() + " events: " + view.byCustomer);
        for (int i = 3; i < commands.events.size(); i++) view.apply(commands.events.get(i));
        System.out.println("read model caught up:           " + view.byCustomer);
    }
}
