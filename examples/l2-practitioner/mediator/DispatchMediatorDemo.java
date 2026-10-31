import java.util.ArrayDeque;
import java.util.Deque;

/** Realistic Mediator: the dispatcher coordinates packed orders and free riders; neither side knows about the other. */
public class DispatchMediatorDemo {

    interface Dispatcher { void orderPacked(String orderId); void riderFree(String riderId); }   // (1)!

    static final class StoreDispatcher implements Dispatcher {
        private final Deque<String> waitingOrders = new ArrayDeque<>(), freeRiders = new ArrayDeque<>();
        public void orderPacked(String orderId) {
            if (freeRiders.isEmpty()) { waitingOrders.add(orderId); System.out.println("  " + orderId + " waits (no free rider)"); }
            else assign(orderId, freeRiders.poll());
        }
        public void riderFree(String riderId) {
            if (waitingOrders.isEmpty()) { freeRiders.add(riderId); System.out.println("  " + riderId + " waits (no packed order)"); }
            else assign(waitingOrders.poll(), riderId);                                              // (2)!
        }
        private void assign(String order, String rider) { System.out.println("  assign " + order + " -> " + rider); }   // (3)!
    }

    // Colleagues: they only report what happened to them
    record PackingStation(Dispatcher d) { void finished(String orderId) { d.orderPacked(orderId); } }
    record RiderApp(Dispatcher d, String riderId) { void tapAvailable() { d.riderFree(riderId); } }

    public static void main(String[] args) {
        Dispatcher d = new StoreDispatcher();
        PackingStation station = new PackingStation(d);
        RiderApp arjun = new RiderApp(d, "R-42"), meena = new RiderApp(d, "R-43");
        station.finished("FC-9601");
        station.finished("FC-9602");
        arjun.tapAvailable();
        meena.tapAvailable();
        meena.tapAvailable();
        station.finished("FC-9603");
    }
}
