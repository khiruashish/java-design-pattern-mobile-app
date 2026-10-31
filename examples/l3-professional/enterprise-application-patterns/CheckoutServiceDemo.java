import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** FreshCart checkout: a rich domain model, repositories, a service layer with a transaction, and DTOs at the edge. */
public class CheckoutServiceDemo {

    // ---- Domain model ----
    record Product(String sku, String name, long pricePaise, int stock) {
        Product take(int qty) {
            if (qty > stock) throw new OutOfStock(sku, stock);
            return new Product(sku, name, pricePaise, stock - qty);
        }
    }
    static final class OutOfStock extends RuntimeException {
        OutOfStock(String sku, int left) { super(sku + " has only " + left + " left"); }
    }
    record Line(String sku, int qty, long pricePaise) { long amount() { return qty * pricePaise; } }
    record Order(String id, String customer, List<Line> lines) {
        Order { if (lines.isEmpty()) throw new IllegalArgumentException("empty cart"); lines = List.copyOf(lines); }
        long subtotal() { return lines.stream().mapToLong(Line::amount).sum(); }
        long deliveryFee() { return subtotal() >= 49_900 ? 0 : 2_500; }
        long total() { return subtotal() + deliveryFee(); }
    }

    // ---- Repositories and a fake transactional store ----
    static final class Store {                                          // (1)!
        Map<String, Product> products = new TreeMap<>();
        Map<String, Order> orders = new TreeMap<>();
        <T> T transaction(java.util.function.Supplier<T> work) {
            var savedProducts = new TreeMap<>(products);
            var savedOrders = new TreeMap<>(orders);
            try { return work.get(); }
            catch (RuntimeException e) { products = savedProducts; orders = savedOrders; throw e; }
        }
    }
    record ProductRepository(Store s) {
        Optional<Product> find(String sku) { return Optional.ofNullable(s.products.get(sku)); }
        void save(Product p) { s.products.put(p.sku(), p); }
    }
    record OrderRepository(Store s) {
        void save(Order o) { s.orders.put(o.id(), o); }
        int count() { return s.orders.size(); }
    }

    // ---- DTOs ----
    record CartItem(String sku, int qty) {}
    record CheckoutRequest(String customer, List<CartItem> items) {}
    sealed interface CheckoutResponse permits Confirmed, Rejected {}
    record Confirmed(String orderId, String total, String delivery) implements CheckoutResponse {}
    record Rejected(String reason) implements CheckoutResponse {}

    // ---- Service layer ----
    static final class CheckoutService {                                // (2)!
        private final Store store; private final ProductRepository products; private final OrderRepository orders;
        private int seq = 100;
        CheckoutService(Store store) { this.store = store; products = new ProductRepository(store); orders = new OrderRepository(store); }

        CheckoutResponse checkout(CheckoutRequest req) {
            try {
                Order order = store.transaction(() -> {
                    List<Line> lines = new ArrayList<>();
                    for (CartItem item : req.items()) {
                        Product p = products.find(item.sku()).orElseThrow(() -> new IllegalArgumentException("unknown " + item.sku()));
                        products.save(p.take(item.qty()));
                        lines.add(new Line(p.sku(), item.qty(), p.pricePaise()));
                    }
                    Order o = new Order("FC-" + seq++, req.customer(), lines);
                    orders.save(o);
                    return o;
                });
                return new Confirmed(order.id(), rupees(order.total()), order.deliveryFee() == 0 ? "free" : rupees(order.deliveryFee()));   // (3)!
            } catch (OutOfStock | IllegalArgumentException e) {
                return new Rejected(e.getMessage());
            }
        }
        static String rupees(long p) { return "Rs " + p / 100 + "." + String.format("%02d", p % 100); }
    }

    public static void main(String[] args) {
        Store store = new Store();
        var products = new ProductRepository(store);
        products.save(new Product("MILK-1L", "Milk 1L", 6_800, 10));
        products.save(new Product("MANGO-1KG", "Alphonso 1kg", 45_000, 2));
        CheckoutService service = new CheckoutService(store);

        System.out.println(service.checkout(new CheckoutRequest("asha", List.of(new CartItem("MILK-1L", 2)))));
        System.out.println(service.checkout(new CheckoutRequest("ravi", List.of(new CartItem("MILK-1L", 1), new CartItem("MANGO-1KG", 1)))));
        System.out.println(service.checkout(new CheckoutRequest("meera", List.of(new CartItem("MILK-1L", 3), new CartItem("MANGO-1KG", 5)))));
        System.out.println(service.checkout(new CheckoutRequest("dev", List.of())));
        System.out.println("milk left: " + products.find("MILK-1L").orElseThrow().stock()
                + ", mango left: " + products.find("MANGO-1KG").orElseThrow().stock()
                + ", orders: " + new OrderRepository(store).count());
    }
}
