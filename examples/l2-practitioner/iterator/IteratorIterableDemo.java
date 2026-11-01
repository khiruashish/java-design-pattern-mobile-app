import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Iterator gives sequential access to a collection without exposing how it's stored. Iterable makes it work with for-each. */
public class IteratorIterableDemo {

    record Stop(String orderId, String address) { }

    // ===================== Before: the route hands out its internal list =====================
    static final class RouteBefore {
        private final List<Stop> stops = new ArrayList<>();
        void add(Stop s) { stops.add(s); }
        List<Stop> getStops() { return stops; }                                               // (1)!
    }

    // ===================== After: the route is Iterable; storage is private (an array here) =====================
    static final class DeliveryRoute implements Iterable<Stop> {
        private Stop[] stops = new Stop[2]; private int size;
        void add(Stop s) {
            if (size == stops.length) stops = java.util.Arrays.copyOf(stops, size * 2);
            stops[size++] = s;
        }
        @Override public Iterator<Stop> iterator() {                                          // (2)!
            return new Iterator<>() {
                private int next = 0;
                public boolean hasNext() { return next < size; }
                public Stop next() {
                    if (!hasNext()) throw new NoSuchElementException();                          // (3)!
                    return stops[next++];
                }
            };
        }
    }

    public static void main(String[] args) {
        RouteBefore before = new RouteBefore();
        before.add(new Stop("FC-8001", "12 MG Road"));
        before.getStops().clear();                                                            // any caller can wreck the route
        System.out.println("before: a caller cleared the route, stops left = " + before.getStops().size());

        DeliveryRoute route = new DeliveryRoute();
        route.add(new Stop("FC-8001", "12 MG Road"));
        route.add(new Stop("FC-8002", "4 Church St"));
        route.add(new Stop("FC-8003", "88 Residency Rd"));
        for (Stop s : route) System.out.println("after: deliver " + s.orderId() + " to " + s.address());
        Iterator<Stop> it = route.iterator();
        try { it.remove(); } catch (UnsupportedOperationException e) { System.out.println("after: remove() not supported, the route stays intact"); }
    }
}
