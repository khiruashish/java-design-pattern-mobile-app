import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Database per service: each service owns its data. Others get what they need through APIs or events and keep
 * their own copies, so a schema change in one service can't break another.
 */
public class DatabasePerServiceDemo {

    // ---------- Customer service: owns customers ----------
    record CustomerChanged(String customerId, String displayName) {}                       // (1)!
    static final class CustomerService {
        private final Map<String, Map<String, String>> customerTable = new HashMap<>();
        private final List<Consumer<CustomerChanged>> subscribers = new java.util.ArrayList<>();
        void subscribe(Consumer<CustomerChanged> s) { subscribers.add(s); }
        void register(String id, String first, String last) {
            customerTable.put(id, new HashMap<>(Map.of("first_name", first, "last_name", last)));
            subscribers.forEach(s -> s.accept(new CustomerChanged(id, first + " " + last.charAt(0) + ".")));
        }
        void migrateSchemaToFullName() {                                                    // (2)!
            customerTable.values().forEach(row -> row.put("full_name", row.remove("first_name") + " " + row.remove("last_name")));
        }
    }

    // ---------- Order service: owns orders and a local copy of what it needs about customers ----------
    static final class OrderService {
        private final Map<String, String> customerNames = new HashMap<>();                 // (3)!
        private final Map<String, String> orders = new HashMap<>();
        OrderService(CustomerService customers) { customers.subscribe(e -> customerNames.put(e.customerId(), e.displayName())); }
        void place(String orderId, String customerId) { orders.put(orderId, customerId); }
        String packingSlip(String orderId) { return orderId + " for " + customerNames.getOrDefault(orders.get(orderId), "(unknown)"); }
    }

    public static void main(String[] args) {
        CustomerService customers = new CustomerService();
        OrderService orders = new OrderService(customers);
        customers.register("C-1", "Asha", "Rao");
        orders.place("FC-1", "C-1");
        System.out.println("before customer schema change: " + orders.packingSlip("FC-1"));
        customers.migrateSchemaToFullName();
        System.out.println("after customer schema change:  " + orders.packingSlip("FC-1"));

        // Shared-database alternative: orders reading customers' table directly would now break.
        Map<String, String> row = customers.customerTable.get("C-1");
        System.out.println("a cross-service SQL join on first_name would now see: " + row.get("first_name"));
    }
}
