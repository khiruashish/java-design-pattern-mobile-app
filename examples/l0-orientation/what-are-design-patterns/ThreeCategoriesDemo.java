import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ThreeCategoriesDemo {

    // ===== Creational: Factory Method decides WHICH object to create =====
    interface Delivery { String describe(); }
    record StandardDelivery(String slot) implements Delivery {
        public String describe() { return "standard delivery in slot " + slot; }
    }
    record ExpressDelivery(int minutes) implements Delivery {
        public String describe() { return "express delivery within " + minutes + " min"; }
    }

    static Delivery createDelivery(boolean express, String slot) {          // (1)!
        return express ? new ExpressDelivery(30) : new StandardDelivery(slot);
    }

    // ===== Structural: Adapter makes a third-party API fit OUR interface =====
    /** Pretend this class comes from a rider-partner SDK we can't change. */
    static final class PartnerRiderApi {
        String requestCourier(double lat, double lon, String ref) {
            return "partner courier booked for " + ref + " at " + lat + "," + lon;
        }
    }

    interface RiderDispatch { String dispatch(String orderId, String address); }

    static final class PartnerRiderAdapter implements RiderDispatch {       // (2)!
        private final PartnerRiderApi api = new PartnerRiderApi();
        public String dispatch(String orderId, String address) {
            // Real code would geocode the address; this example uses a fixed point.
            return api.requestCourier(12.97, 77.59, orderId);
        }
    }

    // ===== Behavioural: Observer tells interested parties about changes =====
    static final class OrderStatus {
        private final List<Consumer<String>> listeners = new ArrayList<>();
        void subscribe(Consumer<String> listener) { listeners.add(listener); }
        void change(String orderId, String status) {                        // (3)!
            listeners.forEach(l -> l.accept(orderId + " is now " + status));
        }
    }

    public static void main(String[] args) {
        System.out.println("Creational:  " + createDelivery(false, "18:00-19:00").describe());
        System.out.println("Creational:  " + createDelivery(true, "ignored").describe());
        System.out.println("Structural:  " + new PartnerRiderAdapter().dispatch("FC-1001", "12 MG Road"));

        OrderStatus status = new OrderStatus();
        status.subscribe(msg -> System.out.println("Behavioural: app push   -> " + msg));
        status.subscribe(msg -> System.out.println("Behavioural: store desk -> " + msg));
        status.change("FC-1001", "PACKED");
    }
}
