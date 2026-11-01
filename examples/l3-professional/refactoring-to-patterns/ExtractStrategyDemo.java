import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Extract Strategy: pull a varying algorithm out of a service into an interface with one class per variant. */
public class ExtractStrategyDemo {

    record Rider(String name, double km, int activeOrders, double rating) {}

    // ---- Before: the algorithm and the city switch are buried in the service. ----
    static String assignBefore(String city, List<Rider> riders) {                  // (1)!
        Rider best = null;
        for (Rider r : riders) {
            if (city.equals("BLR")) {
                if (best == null || r.km() < best.km()) best = r;
            } else if (city.equals("PUN")) {
                if (r.activeOrders() < 2 && (best == null || r.rating() > best.rating())) best = r;
            } else {
                if (best == null || r.activeOrders() < best.activeOrders()) best = r;
            }
        }
        return best == null ? "none" : best.name();
    }

    // ---- After ----
    interface AssignmentStrategy { String pick(List<Rider> riders); }              // (2)!

    static final AssignmentStrategy NEAREST = rs -> rs.stream().min(Comparator.comparingDouble(Rider::km)).map(Rider::name).orElse("none");
    static final AssignmentStrategy BEST_RATED_NOT_BUSY = rs -> rs.stream().filter(r -> r.activeOrders() < 2)
            .max(Comparator.comparingDouble(Rider::rating)).map(Rider::name).orElse("none");
    static final AssignmentStrategy LEAST_BUSY = rs -> rs.stream().min(Comparator.comparingInt(Rider::activeOrders)).map(Rider::name).orElse("none");

    record Dispatcher(Map<String, AssignmentStrategy> byCity, AssignmentStrategy fallback) {   // (3)!
        String assign(String city, List<Rider> riders) { return byCity.getOrDefault(city, fallback).pick(riders); }
    }

    public static void main(String[] args) {
        List<Rider> riders = List.of(new Rider("Arjun", 1.2, 2, 4.9), new Rider("Bina", 2.5, 0, 4.7), new Rider("Chetan", 0.8, 3, 4.2));
        Dispatcher dispatcher = new Dispatcher(Map.of("BLR", NEAREST, "PUN", BEST_RATED_NOT_BUSY), LEAST_BUSY);
        for (String city : List.of("BLR", "PUN", "MYS"))
            System.out.println(city + ": before " + assignBefore(city, riders) + ", after " + dispatcher.assign(city, riders));
    }
}
