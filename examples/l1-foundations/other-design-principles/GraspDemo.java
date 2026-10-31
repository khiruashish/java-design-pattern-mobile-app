import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** GRASP: Information Expert, Creator, Controller, Pure Fabrication in one checkout flow. */
public class GraspDemo {

    record Product(String sku, long priceInCents) { }

    record BasketLine(Product product, int quantity) {
        /** Information Expert: the line has price and quantity, so it computes its subtotal. */
        long subtotal() { return product.priceInCents() * quantity; }                        // (1)!
    }

    static final class Basket {
        private final List<BasketLine> lines = new ArrayList<>();

        /** Creator: the basket contains lines, so it creates them (and validates them). */
        void add(Product product, int quantity) {                                           // (2)!
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            lines.add(new BasketLine(product, quantity));
        }

        /** Information Expert again: the basket has the lines, so it totals them. */
        long total() { return lines.stream().mapToLong(BasketLine::subtotal).sum(); }
        List<BasketLine> lines() { return Collections.unmodifiableList(lines); }
    }

    /** Pure Fabrication: not a domain concept, invented to keep persistence out of Basket. */
    interface BasketRepository { void save(String customerId, Basket basket); }            // (3)!

    /** Controller: receives the system event "checkout" and delegates; holds no business rules. */
    static final class CheckoutController {                                                // (4)!
        private final BasketRepository repository;
        CheckoutController(BasketRepository repository) { this.repository = repository; }

        String checkout(String customerId, Basket basket) {
            if (basket.lines().isEmpty()) return "400 basket is empty";
            repository.save(customerId, basket);
            return "201 order for " + customerId + " total " + basket.total();
        }
    }

    public static void main(String[] args) {
        Basket basket = new Basket();
        basket.add(new Product("MILK-1L", 129), 2);
        basket.add(new Product("BREAD-WHT", 249), 1);

        List<String> saved = new ArrayList<>();
        CheckoutController controller = new CheckoutController((customer, b) -> saved.add(customer + ":" + b.total()));
        System.out.println(controller.checkout("C-42", basket));
        System.out.println(controller.checkout("C-43", new Basket()));
        System.out.println("saved: " + saved);
    }
}
