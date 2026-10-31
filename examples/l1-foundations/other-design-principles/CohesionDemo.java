import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Cohesion and coupling: group by feature, and let features talk through a narrow event. */
public class CohesionDemo {

    // ===================== LOW cohesion: a grab-bag of unrelated helpers =====================
    static final class OrderUtils {                                                       // (1)!
        static String formatPrice(long cents) { return "$%d.%02d".formatted(cents / 100, cents % 100); }
        static boolean isValidPincode(String pin) { return pin.matches("[1-9][0-9]{5}"); }
        static int riderPayoutCents(double km) { return (int) Math.max(300, 150 + Math.round(km * 45)); }
    }

    // ===================== HIGH cohesion: each class has one feature's rules =====================
    record OrderPlaced(String orderId, String pincode, long totalInCents) { }               // (2)!

    /** Feature: checkout. Knows nothing about delivery internals. */
    static final class Checkout {
        private final List<Consumer<OrderPlaced>> listeners = new ArrayList<>();
        void onOrderPlaced(Consumer<OrderPlaced> l) { listeners.add(l); }
        void place(String orderId, String pincode, long total) {
            if (!Pincode.isValid(pincode)) throw new IllegalArgumentException("invalid pincode " + pincode);
            OrderPlaced event = new OrderPlaced(orderId, pincode, total);
            listeners.forEach(l -> l.accept(event));                                         // (3)!
        }
    }

    /** Feature: addresses. All pincode knowledge in one place. */
    static final class Pincode {
        static boolean isValid(String pin) { return pin != null && pin.matches("[1-9][0-9]{5}"); }
    }

    /** Feature: delivery. Depends only on the event, not on Checkout's classes. */
    static final class DeliveryPlanner {
        final List<String> planned = new ArrayList<>();
        void handle(OrderPlaced e) { planned.add(e.orderId() + "->hub-" + e.pincode().substring(0, 3)); }
    }

    public static void main(String[] args) {
        System.out.println("utils grab-bag: " + OrderUtils.formatPrice(1299) + ", " + OrderUtils.isValidPincode("411001") + ", " + OrderUtils.riderPayoutCents(2));

        Checkout checkout = new Checkout();
        DeliveryPlanner planner = new DeliveryPlanner();
        checkout.onOrderPlaced(planner::handle);
        checkout.place("FC-9001", "411001", 4_250);
        checkout.place("FC-9002", "560034", 1_999);
        try { checkout.place("FC-9003", "012345", 999); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        System.out.println("delivery planned: " + planner.planned);
    }
}
