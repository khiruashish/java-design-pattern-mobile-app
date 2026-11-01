import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Three patterns working together in one small, production-shaped checkout. */
public class CheckoutDemo {

    // ---------- Builder (creational): a valid, immutable Order or nothing ----------
    record Order(String id, String customer, long basketInCents, boolean express) {
        static Builder builder() { return new Builder(); }

        static final class Builder {                                        // (1)!
            private String id;
            private String customer;
            private long basketInCents = -1;
            private boolean express;

            Builder id(String id) { this.id = id; return this; }
            Builder customer(String customer) { this.customer = customer; return this; }
            Builder basketInCents(long cents) { this.basketInCents = cents; return this; }
            Builder express(boolean express) { this.express = express; return this; }

            Order build() {
                List<String> problems = new ArrayList<>();
                if (id == null || id.isBlank()) problems.add("id is required");
                if (customer == null || customer.isBlank()) problems.add("customer is required");
                if (basketInCents <= 0) problems.add("basket must be positive");
                if (!problems.isEmpty()) throw new IllegalStateException("Invalid order: " + String.join(", ", problems));
                return new Order(id, customer, basketInCents, express);
            }
        }
    }

    // ---------- Strategy (behavioural): how delivery is priced ----------
    interface FeeStrategy { long feeInCents(Order order); }                 // (2)!

    static final FeeStrategy STANDARD = order -> order.basketInCents() >= 5_000 ? 0 : 499;
    static final FeeStrategy EXPRESS = order -> STANDARD.feeInCents(order) + 300;

    // ---------- Observer (behavioural): who hears about a placed order ----------
    interface CheckoutListener { void orderPlaced(Order order, long totalInCents); }

    static final class Checkout {
        private final List<CheckoutListener> listeners = new ArrayList<>();

        void addListener(CheckoutListener listener) { listeners.add(Objects.requireNonNull(listener)); }

        long place(Order order) {
            FeeStrategy strategy = order.express() ? EXPRESS : STANDARD;
            long total = order.basketInCents() + strategy.feeInCents(order);
            for (CheckoutListener listener : listeners) {
                try {
                    listener.orderPlaced(order, total);
                } catch (RuntimeException e) {                              // (3)!
                    System.out.println("listener_failed order=" + order.id() + " error=\"" + e.getMessage() + "\"");
                }
            }
            return total;
        }
    }

    static String money(long cents) { return "$%d.%02d".formatted(cents / 100, cents % 100); }

    public static void main(String[] args) {
        Checkout checkout = new Checkout();
        checkout.addListener((o, total) -> System.out.println("receipt order=" + o.id() + " total=" + money(total)));
        checkout.addListener((o, total) -> { throw new RuntimeException("loyalty service unavailable"); });
        checkout.addListener((o, total) -> System.out.println("metric orders_placed{express=" + o.express() + "} +1"));

        checkout.place(Order.builder().id("FC-2001").customer("Asha").basketInCents(3_250).build());
        checkout.place(Order.builder().id("FC-2002").customer("Ravi").basketInCents(6_100).express(true).build());

        try {
            Order.builder().id(" ").basketInCents(0).build();
        } catch (IllegalStateException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}
