import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Lazy Load defers loading related data until it is used, and shows why that can cause N+1 queries. */
public class LazyLoadDemo {

    static final List<String> SQL = new ArrayList<>();
    static final Map<String, List<String>> ORDERS = new LinkedHashMap<>();
    static {
        ORDERS.put("asha", List.of("ORD-1", "ORD-4"));
        ORDERS.put("ravi", List.of("ORD-2"));
        ORDERS.put("meera", List.of("ORD-3", "ORD-5", "ORD-6"));
    }

    /** A value holder: loads once, on first get(). */
    static final class Lazy<T> implements Supplier<T> {                   // (1)!
        private Supplier<T> loader; private T value;
        Lazy(Supplier<T> loader) { this.loader = loader; }
        public T get() {
            if (loader != null) { value = loader.get(); loader = null; }
            return value;
        }
    }

    record Customer(String name, Lazy<List<String>> orders) {}

    static List<Customer> loadCustomers() {
        SQL.add("SELECT * FROM customer");
        return ORDERS.keySet().stream()
                .map(n -> new Customer(n, new Lazy<>(() -> {
                    SQL.add("SELECT * FROM orders WHERE customer='" + n + "'");
                    return ORDERS.get(n);
                })))
                .toList();
    }

    public static void main(String[] args) {
        List<Customer> customers = loadCustomers();
        System.out.println("names only: " + customers.stream().map(Customer::name).toList() + ", queries=" + SQL.size());   // (2)!

        SQL.clear();
        customers = loadCustomers();
        int total = customers.stream().mapToInt(c -> c.orders().get().size()).sum();
        System.out.println("N+1: " + total + " orders, queries=" + SQL.size());

        SQL.clear();                                                      // (3)!
        SQL.add("SELECT * FROM customer");
        SQL.add("SELECT * FROM orders WHERE customer IN ('asha','ravi','meera')");
        int batched = ORDERS.values().stream().mapToInt(List::size).sum();
        System.out.println("batch fetch: " + batched + " orders, queries=" + SQL.size());
    }
}
