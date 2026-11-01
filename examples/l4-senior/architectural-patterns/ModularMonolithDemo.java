import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Modular monolith: one deployable, several modules with explicit public APIs and private internals, talking through
 * those APIs or in-process events. Boundaries are as strict as microservices without the network.
 */
public class ModularMonolithDemo {

    // ---------- In-process event bus shared by modules ----------
    static final class Events {
        private final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();
        <E> void on(Class<E> type, Consumer<E> h) { handlers.computeIfAbsent(type, t -> new ArrayList<>()).add(o -> h.accept(type.cast(o))); }
        void publish(Object e) { handlers.getOrDefault(e.getClass(), List.of()).forEach(h -> h.accept(e)); }
    }

    // ---------- Inventory module ----------
    interface InventoryApi { boolean reserve(String sku, int qty); int available(String sku); }   // (1)!
    static final class InventoryModule {
        private final Map<String, Integer> stock = new HashMap<>(Map.of("MILK-1L", 5));           // internal state
        final InventoryApi api = new InventoryApi() {
            public boolean reserve(String sku, int qty) {
                int left = stock.getOrDefault(sku, 0);
                if (left < qty) return false;
                stock.put(sku, left - qty);
                return true;
            }
            public int available(String sku) { return stock.getOrDefault(sku, 0); }
        };
    }

    // ---------- Orders module ----------
    record OrderPlaced(String orderId, String customer) {}                                         // (2)!
    static final class OrdersModule {
        private final InventoryApi inventory; private final Events events; private int seq = 1;
        OrdersModule(InventoryApi inventory, Events events) { this.inventory = inventory; this.events = events; }
        String place(String customer, String sku, int qty) {
            if (!inventory.reserve(sku, qty)) return "REJECTED (stock)";
            String id = "FC-" + seq++;
            events.publish(new OrderPlaced(id, customer));
            return id;
        }
    }

    // ---------- Notifications module: depends on nothing but the event ----------
    static final class NotificationsModule {                                                       // (3)!
        final List<String> sent = new ArrayList<>();
        NotificationsModule(Events events) { events.on(OrderPlaced.class, e -> sent.add("SMS to " + e.customer() + ": " + e.orderId() + " placed")); }
    }

    public static void main(String[] args) {
        Events events = new Events();
        InventoryModule inventory = new InventoryModule();
        OrdersModule orders = new OrdersModule(inventory.api, events);
        NotificationsModule notifications = new NotificationsModule(events);

        System.out.println(orders.place("asha", "MILK-1L", 3));
        System.out.println(orders.place("ravi", "MILK-1L", 3));
        System.out.println("milk left: " + inventory.api.available("MILK-1L"));
        System.out.println(notifications.sent);
    }
}
