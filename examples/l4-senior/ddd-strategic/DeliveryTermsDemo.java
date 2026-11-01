import java.util.List;

/** QuickDrop: "delivery" means one thing to customers and another to riders, so each context gets its own model. */
public class DeliveryTermsDemo {

    // Customer context: a delivery is "when my order arrives".
    record CustomerDelivery(String orderId, String window, String status) {}                       // (1)!

    // Rider context: a delivery is "one stop on my trip", and a trip has many.
    record Stop(String orderId, String address, int sequence) {}
    record Trip(String riderId, List<Stop> stops) {                                                // (2)!
        int remaining(int completed) { return stops.size() - completed; }
    }

    public static void main(String[] args) {
        var trip = new Trip("R7", List.of(new Stop("QD-1", "12 MG Road", 1), new Stop("QD-2", "4 Brigade Rd", 2), new Stop("QD-3", "9 Church St", 3)));
        System.out.println("rider sees: " + trip.stops().size() + " stops, " + trip.remaining(1) + " remaining");
        System.out.println("customer sees: " + new CustomerDelivery("QD-2", "18:00-20:00", "next stop"));
    }
}
