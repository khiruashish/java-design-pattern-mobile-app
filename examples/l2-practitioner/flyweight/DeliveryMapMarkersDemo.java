import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Realistic Flyweight: the ops map draws 5,000 live orders. Marker styles are shared; positions are passed in at draw time. */
public class DeliveryMapMarkersDemo {

    enum Status { PACKING, ON_THE_WAY, DELAYED }
    enum Vehicle { BIKE, SCOOTER, VAN }

    /** Flyweight: intrinsic, immutable, shared. In a real app it holds a decoded bitmap (tens of KB). */
    record MarkerStyle(Status status, Vehicle vehicle, String colour, String icon) {          // (1)!
        String draw(double lat, double lon, String orderId) {                                  // (2)!
            return String.format("%s %s at (%.3f, %.3f) %s", icon, colour, lat, lon, orderId);
        }
    }

    static final class MarkerStyles {
        private final Map<String, MarkerStyle> cache = new ConcurrentHashMap<>();
        MarkerStyle get(Status s, Vehicle v) {
            return cache.computeIfAbsent(s + "/" + v, k -> new MarkerStyle(s, v,
                    switch (s) { case PACKING -> "grey"; case ON_THE_WAY -> "green"; case DELAYED -> "red"; },
                    v.name().toLowerCase() + ".png"));
        }
        int size() { return cache.size(); }
    }

    record LiveOrder(String id, double lat, double lon, Status status, Vehicle vehicle) { }   // (3)!

    public static void main(String[] args) {
        List<LiveOrder> orders = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            orders.add(new LiveOrder("FC-" + (6000 + i), 12.90 + (i % 100) * 0.001, 77.55 + (i / 100) * 0.001,
                    Status.values()[i % 3], Vehicle.values()[(i / 3) % 3]));
        }
        MarkerStyles styles = new MarkerStyles();
        List<String> frame = new ArrayList<>();
        for (LiveOrder o : orders) frame.add(styles.get(o.status(), o.vehicle()).draw(o.lat(), o.lon(), o.id()));
        System.out.println("drew " + frame.size() + " markers using " + styles.size() + " shared styles");
        frame.subList(0, 3).forEach(System.out::println);
    }
}
