import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** FreshCart order placement after applying SRP, OCP and DIP. */
public class OrderServiceDemo {

    record Order(String id, String customerEmail, long basketInCents, String promoCode) { }

    // ---- OCP: new pricing rules are new classes; the service never changes ----
    interface PricingRule { long adjust(Order order, long runningTotal); }                 // (1)!

    record DeliveryFee(long feeInCents, long freeOverInCents) implements PricingRule {
        public long adjust(Order o, long total) { return o.basketInCents() >= freeOverInCents ? total : total + feeInCents; }
    }

    record PromoCode(String code, int percentOff) implements PricingRule {
        public long adjust(Order o, long total) {
            return code.equals(o.promoCode()) ? total - o.basketInCents() * percentOff / 100 : total;
        }
    }

    // ---- DIP: the service owns these abstractions; details implement them ----
    interface OrderRepository { void save(Order order, long totalInCents); Optional<Long> totalOf(String orderId); }  // (2)!
    interface Notifier { void orderConfirmed(String email, String orderId, long totalInCents); }

    // ---- SRP: this class only coordinates placing an order ----
    static final class OrderService {                                                       // (3)!
        private final List<PricingRule> rules;
        private final OrderRepository repository;
        private final Notifier notifier;

        OrderService(List<PricingRule> rules, OrderRepository repository, Notifier notifier) {
            this.rules = List.copyOf(rules);
            this.repository = repository;
            this.notifier = notifier;
        }

        long place(Order order) {
            long total = order.basketInCents();
            for (PricingRule rule : rules) total = rule.adjust(order, total);
            repository.save(order, total);
            notifier.orderConfirmed(order.customerEmail(), order.id(), total);
            return total;
        }
    }

    // ---- Details, chosen only in main (the composition root) ----
    static final class InMemoryOrders implements OrderRepository {
        private final Map<String, Long> totals = new HashMap<>();
        public void save(Order order, long total) { totals.put(order.id(), total); }
        public Optional<Long> totalOf(String id) { return Optional.ofNullable(totals.get(id)); }
    }

    static final class ConsoleEmail implements Notifier {
        public void orderConfirmed(String email, String orderId, long total) {
            System.out.printf("email to %s: order %s confirmed, total $%d.%02d%n", email, orderId, total / 100, total % 100);
        }
    }

    public static void main(String[] args) {
        List<PricingRule> rules = new ArrayList<>(List.of(new DeliveryFee(499, 5_000), new PromoCode("WELCOME10", 10)));
        InMemoryOrders repo = new InMemoryOrders();
        OrderService service = new OrderService(rules, repo, new ConsoleEmail());                // (4)!

        service.place(new Order("FC-5001", "asha@example.com", 3_000, null));
        service.place(new Order("FC-5002", "ravi@example.com", 6_000, "WELCOME10"));
        System.out.println("stored FC-5002 total: " + repo.totalOf("FC-5002").orElseThrow());
    }
}
