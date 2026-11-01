import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Content-based router: inspect each message and send it to the right channel. The rules are data, so a new route
 * doesn't change the producers or the other consumers.
 */
public class ContentBasedRouterDemo {

    record Order(String id, String payment, boolean express, int items, String city) {}
    record Route(String channel, Predicate<Order> when) {}

    static final class Router {
        private final List<Route> routes = new ArrayList<>();                                     // (1)!
        private final String fallbackChannel;
        final Map<String, List<String>> channels = new TreeMap<>();
        Router(String fallbackChannel) { this.fallbackChannel = fallbackChannel; }
        Router when(String channel, Predicate<Order> rule) { routes.add(new Route(channel, rule)); return this; }
        void route(Order o) {
            String channel = routes.stream().filter(r -> r.when().test(o)).map(Route::channel)
                    .findFirst().orElse(fallbackChannel);                                         // (2)!
            channels.computeIfAbsent(channel, k -> new ArrayList<>()).add(o.id());
        }
    }

    public static void main(String[] args) {
        Router router = new Router("standard-fulfilment")
                .when("unsupported-city", o -> !List.of("BLR", "PUN", "DEL").contains(o.city()))   // (3)!
                .when("bulk-orders", o -> o.items() > 40)
                .when("express-fulfilment", Order::express)
                .when("cod-verification", o -> o.payment().equals("COD"));
        List.of(new Order("FC-1", "UPI", true, 5, "BLR"), new Order("FC-2", "COD", false, 3, "PUN"),
                new Order("FC-3", "CARD", false, 60, "BLR"), new Order("FC-4", "UPI", false, 2, "GOA"),
                new Order("FC-5", "COD", true, 4, "DEL"), new Order("FC-6", "UPI", false, 7, "DEL")).forEach(router::route);
        router.channels.forEach((channel, ids) -> System.out.println(channel + ": " + ids));
    }
}
