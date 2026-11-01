import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Classic Observer: an order (subject) notifies interested parties (observers) when its status changes. */
public class SubjectObserverDemo {

    enum Status { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED }

    interface OrderObserver { void onStatusChanged(String orderId, Status from, Status to); }   // (1)!

    static final class Order {
        private final String id;
        private Status status = Status.PLACED;
        private final List<OrderObserver> observers = new CopyOnWriteArrayList<>();      // (2)!

        Order(String id) { this.id = id; }
        void subscribe(OrderObserver o) { observers.add(o); }
        void unsubscribe(OrderObserver o) { observers.remove(o); }

        void moveTo(Status next) {
            Status previous = status;
            status = next;                                                                // (3)!
            observers.forEach(o -> o.onStatusChanged(id, previous, next));
        }
    }

    public static void main(String[] args) {
        Order order = new Order("FC-801");
        OrderObserver sms = (id, from, to) -> System.out.println("  sms: your order " + id + " is " + to.name().toLowerCase().replace('_', ' '));
        OrderObserver analytics = (id, from, to) -> System.out.println("  analytics: " + id + " " + from + "->" + to);
        order.subscribe(sms);
        order.subscribe(analytics);

        System.out.println("PACKED:");
        order.moveTo(Status.PACKED);
        order.unsubscribe(analytics);                                                     // (4)!
        System.out.println("OUT_FOR_DELIVERY:");
        order.moveTo(Status.OUT_FOR_DELIVERY);
    }
}
