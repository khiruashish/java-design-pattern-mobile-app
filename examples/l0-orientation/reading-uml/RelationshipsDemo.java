import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Every UML relationship, in FreshCart terms. */
public class RelationshipsDemo {

    // Inheritance (generalisation):  Customer <|-- PremiumCustomer
    static class Customer {
        final String name;
        Customer(String name) { this.name = name; }
        int freeDeliveriesPerMonth() { return 0; }
    }
    static final class PremiumCustomer extends Customer {
        PremiumCustomer(String name) { super(name); }
        @Override int freeDeliveriesPerMonth() { return 8; }
    }

    // Realisation:  Notifier <|.. SmsNotifier
    interface Notifier { String notify(Customer c, String text); }
    static final class SmsNotifier implements Notifier {
        public String notify(Customer c, String text) { return "SMS to " + c.name + ": " + text; }
    }

    record Product(String sku) { }
    record OrderLine(Product product, int quantity) { }

    static final class Order {
        private final Customer customer;                                  // (1)!
        private final List<OrderLine> lines = new ArrayList<>();          // (2)!
        private final List<Product> wishlistSuggestions;                  // (3)!

        Order(Customer customer, List<Product> sharedCatalogueSuggestions) {
            this.customer = customer;
            this.wishlistSuggestions = sharedCatalogueSuggestions;
        }

        void add(Product product, int quantity) { lines.add(new OrderLine(product, quantity)); }

        String confirm(Notifier notifier, LocalDate date) {               // (4)!
            return notifier.notify(customer, lines.size() + " lines confirmed for " + date
                    + ", " + customer.freeDeliveriesPerMonth() + " free deliveries this month, "
                    + wishlistSuggestions.size() + " suggestions");
        }
    }

    public static void main(String[] args) {
        List<Product> suggestions = List.of(new Product("HONEY-500G"));
        Order order = new Order(new PremiumCustomer("Asha"), suggestions);
        order.add(new Product("MILK-1L"), 2);
        order.add(new Product("BREAD-WHT"), 1);
        System.out.println(order.confirm(new SmsNotifier(), LocalDate.of(2026, 11, 2)));
    }
}
