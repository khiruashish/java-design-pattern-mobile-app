import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The classic answer to "add operations over a fixed set of event types" is the Visitor pattern
 * (an interface with one visit method per type plus accept() on every type). With Java 21,
 * sealed interfaces and pattern matching for switch do the same job in a fraction of the code.
 */
public class DeliveryEventsDemo {

    sealed interface DeliveryEvent permits Assigned, PickedUp, Delivered, Failed { // (1)!
        String orderId();
    }
    record Assigned(String orderId, String rider) implements DeliveryEvent { }
    record PickedUp(String orderId, int etaMinutes) implements DeliveryEvent { }
    record Delivered(String orderId) implements DeliveryEvent { }
    record Failed(String orderId, String reason) implements DeliveryEvent {
        Failed {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Failed event needs a reason");
        }
    }

    enum Kind { ASSIGNED, PICKED_UP, DELIVERED, FAILED }

    /** Operation 1: the customer-facing push text. */
    static String customerMessage(DeliveryEvent event) {
        return switch (event) {                                                  // (2)!
            case Assigned a -> a.rider() + " is your rider for " + a.orderId();
            case PickedUp p when p.etaMinutes() <= 10 -> "Almost there! " + p.orderId() + " arrives in " + p.etaMinutes() + " min";
            case PickedUp p -> p.orderId() + " is on its way, ETA " + p.etaMinutes() + " min";
            case Delivered d -> d.orderId() + " delivered. Enjoy!";
            case Failed f -> "Sorry, " + f.orderId() + " could not be delivered: " + f.reason();
        };
    }

    /** Operation 2: the metric bucket. Adding a new event type makes BOTH switches fail to compile. */
    static Kind kind(DeliveryEvent event) {                                      // (3)!
        return switch (event) {
            case Assigned a -> Kind.ASSIGNED;
            case PickedUp p -> Kind.PICKED_UP;
            case Delivered d -> Kind.DELIVERED;
            case Failed f -> Kind.FAILED;
        };
    }

    public static void main(String[] args) {
        List<DeliveryEvent> events = List.of(
                new Assigned("FC-3001", "Imran"),
                new PickedUp("FC-3001", 25),
                new PickedUp("FC-3001", 8),
                new Delivered("FC-3001"),
                new Failed("FC-3002", "customer not reachable"));

        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (DeliveryEvent event : events) {
            System.out.println("push: " + customerMessage(event));
            counts.merge(kind(event), 1, Integer::sum);
        }
        System.out.println("metrics: " + counts);

        try {
            new Failed("FC-3003", " ");
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());                  // (4)!
        }
    }
}
