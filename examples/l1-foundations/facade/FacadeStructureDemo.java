import java.util.List;

/** Facade: one simple entry point over several subsystems the client would otherwise have to coordinate. */
public class FacadeStructureDemo {

    // ===================== Subsystems (each fine on its own) =====================
    static final class Inventory { boolean reserve(List<String> skus) { return !skus.contains("SOLD-OUT"); } }
    static final class Pricing { long total(List<String> skus) { return skus.size() * 9_900L; } }
    static final class Payments { String charge(String customer, long cents) { return "pay_" + customer + "_" + cents; } }
    static final class Notifications { String confirm(String customer, String paymentId) { return "sms to " + customer + " (" + paymentId + ")"; } }

    // ===================== BEFORE: the client knows every subsystem and the order of calls =====================
    static String checkoutWithoutFacade(String customer, List<String> skus) {
        Inventory inv = new Inventory(); Pricing pricing = new Pricing(); Payments pay = new Payments(); Notifications n = new Notifications();   // (1)!
        if (!inv.reserve(skus)) return "out of stock";
        long total = pricing.total(skus);
        String paymentId = pay.charge(customer, total);
        return n.confirm(customer, paymentId);
    }

    // ===================== AFTER: a facade owns the coordination =====================
    static final class CheckoutFacade {                                                   // (2)!
        private final Inventory inventory; private final Pricing pricing; private final Payments payments; private final Notifications notifications;
        CheckoutFacade(Inventory i, Pricing p, Payments pay, Notifications n) { inventory = i; pricing = p; payments = pay; notifications = n; }

        String placeOrder(String customer, List<String> skus) {                           // (3)!
            if (!inventory.reserve(skus)) return "out of stock";
            String paymentId = payments.charge(customer, pricing.total(skus));
            return notifications.confirm(customer, paymentId);
        }
    }

    public static void main(String[] args) {
        System.out.println("without facade: " + checkoutWithoutFacade("asha", List.of("MILK", "EGGS")));
        CheckoutFacade checkout = new CheckoutFacade(new Inventory(), new Pricing(), new Payments(), new Notifications());
        System.out.println("with facade:    " + checkout.placeOrder("asha", List.of("MILK", "EGGS")));
        System.out.println("with facade:    " + checkout.placeOrder("ravi", List.of("SOLD-OUT")));
        System.out.println("subsystems still usable directly: total = " + new Pricing().total(List.of("MILK")));   // (4)!
    }
}
