import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Facade for "place order":
 * - one call for the app, coordinating inventory, coupons, payments, orders and notifications
 * - compensation: if a later step fails, earlier steps are undone in reverse order
 * - idempotency: a retried request with the same key returns the first result, never charges twice
 * - a non-critical step (notification) failing doesn't fail the order
 * - returns a clear result type; subsystem exceptions never reach the app
 */
public class PlaceOrderFacadeDemo {

    // ===================== Subsystems =====================
    static final class Inventory {
        final Set<String> reserved = new HashSet<>();
        String reserve(String orderId, List<String> skus) {
            if (skus.contains("SOLD-OUT")) throw new IllegalStateException("SOLD-OUT unavailable");
            reserved.add(orderId); return "res-" + orderId;
        }
        void release(String orderId) { reserved.remove(orderId); }
    }
    static final class Coupons {
        final Set<String> used = new HashSet<>();
        long apply(String code, long total) {
            if (code == null) return total;
            if (!used.add(code)) throw new IllegalStateException("coupon " + code + " already used");
            return total * 80 / 100;
        }
        void restore(String code) { if (code != null) used.remove(code); }
    }
    static final class Payments {
        int charges;
        String charge(String customer, long cents) {
            if (customer.equals("declined")) throw new IllegalStateException("card declined");
            charges++; return "pay-" + charges;
        }
    }
    static final class Orders { final List<String> saved = new ArrayList<>(); void save(String orderId) { saved.add(orderId); } }
    static final class Notifier { void confirm(String customer, String orderId) { if (customer.equals("no-sms")) throw new IllegalStateException("sms gateway down"); } }

    // ===================== Facade API =====================
    record PlaceOrder(String idempotencyKey, String orderId, String customer, List<String> skus, long totalCents, String coupon) { }
    sealed interface Outcome permits Placed, Rejected { }
    record Placed(String orderId, String paymentId, long chargedCents, List<String> warnings) implements Outcome { }
    record Rejected(String orderId, String reason, List<String> undone) implements Outcome { }

    static final class PlaceOrderFacade {
        private final Inventory inventory; private final Coupons coupons; private final Payments payments; private final Orders orders; private final Notifier notifier;
        private final Map<String, Outcome> byKey = new ConcurrentHashMap<>();

        PlaceOrderFacade(Inventory i, Coupons c, Payments p, Orders o, Notifier n) { inventory = i; coupons = c; payments = p; orders = o; notifier = n; }

        Outcome place(PlaceOrder cmd) {
            return byKey.computeIfAbsent(cmd.idempotencyKey(), k -> doPlace(cmd));        // (1)!
        }

        private Outcome doPlace(PlaceOrder cmd) {
            List<Runnable> compensations = new ArrayList<>();                             // (2)!
            List<String> undone = new ArrayList<>();
            try {
                inventory.reserve(cmd.orderId(), cmd.skus());
                compensations.add(() -> { inventory.release(cmd.orderId()); undone.add("stock released"); });

                long payable = coupons.apply(cmd.coupon(), cmd.totalCents());
                compensations.add(() -> { coupons.restore(cmd.coupon()); if (cmd.coupon() != null) undone.add("coupon restored"); });

                String paymentId = payments.charge(cmd.customer(), payable);              // (3)!
                orders.save(cmd.orderId());

                List<String> warnings = new ArrayList<>();
                try { notifier.confirm(cmd.customer(), cmd.orderId()); }
                catch (RuntimeException e) { warnings.add("confirmation not sent: " + e.getMessage()); }   // (4)!
                return new Placed(cmd.orderId(), paymentId, payable, warnings);
            } catch (RuntimeException e) {
                for (int i = compensations.size() - 1; i >= 0; i--) compensations.get(i).run();   // (5)!
                return new Rejected(cmd.orderId(), e.getMessage(), undone);
            }
        }
    }

    public static void main(String[] args) {
        Inventory inventory = new Inventory(); Coupons coupons = new Coupons(); Payments payments = new Payments(); Orders orders = new Orders();
        PlaceOrderFacade facade = new PlaceOrderFacade(inventory, coupons, payments, orders, new Notifier());

        PlaceOrder ok = new PlaceOrder("k1", "FC-2301", "asha", List.of("MILK"), 50_000, "WELCOME20");
        System.out.println(facade.place(ok));
        System.out.println("retry same key: " + facade.place(ok) + ", total charges " + payments.charges);
        System.out.println(facade.place(new PlaceOrder("k2", "FC-2302", "declined", List.of("RICE"), 30_000, "FESTIVE")));
        System.out.println(facade.place(new PlaceOrder("k3", "FC-2303", "ravi", List.of("SOLD-OUT"), 10_000, null)));
        System.out.println(facade.place(new PlaceOrder("k4", "FC-2304", "no-sms", List.of("EGGS"), 12_000, null)));
        System.out.println(facade.place(new PlaceOrder("k5", "FC-2305", "vikram", List.of("OIL"), 20_000, "WELCOME20")));
        System.out.println("reserved: " + new java.util.TreeSet<>(inventory.reserved) + ", coupons used: " + new java.util.TreeSet<>(coupons.used) + ", saved orders: " + orders.saved);
    }
}
