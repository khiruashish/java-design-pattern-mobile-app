import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Streams as declarative pipelines: filter -> map -> group -> collect, compared with the equivalent loop. */
public class StreamsPipelineDemo {

    record Order(String id, String city, String status, long totalPaise) { }

    static final List<Order> ORDERS = List.of(
            new Order("O1", "Pune", "DELIVERED", 64_900), new Order("O2", "Pune", "CANCELLED", 12_000),
            new Order("O3", "Mumbai", "DELIVERED", 31_000), new Order("O4", "Pune", "DELIVERED", 22_000),
            new Order("O5", "Mumbai", "DELIVERED", 87_500), new Order("O6", "Nashik", "DELIVERED", 9_900));

    // ---------- before: a loop with a mutable map and the logic spread across lines ----------
    static Map<String, Long> revenueByCityBefore() {                                                   // (1)!
        Map<String, Long> out = new HashMap<>();
        for (Order o : ORDERS) {
            if (!o.status().equals("DELIVERED")) continue;
            out.put(o.city(), out.getOrDefault(o.city(), 0L) + o.totalPaise());
        }
        return new TreeMap<>(out);
    }

    // ---------- after: the pipeline reads as the requirement ----------
    static Map<String, Long> revenueByCity() {
        return ORDERS.stream()
                .filter(o -> o.status().equals("DELIVERED"))
                .collect(Collectors.groupingBy(Order::city, TreeMap::new, Collectors.summingLong(Order::totalPaise)));   // (2)!
    }

    public static void main(String[] args) {
        System.out.println("loop:   " + revenueByCityBefore());
        System.out.println("stream: " + revenueByCity());

        var stats = ORDERS.stream().filter(o -> o.status().equals("DELIVERED"))
                .collect(Collectors.teeing(Collectors.counting(), Collectors.averagingLong(Order::totalPaise),
                        (n, avg) -> n + " delivered, average Rs " + Math.round(avg / 100)));
        System.out.println(stats);
        System.out.println("top 2: " + ORDERS.stream().sorted(Comparator.comparingLong(Order::totalPaise).reversed()).limit(2).map(Order::id).toList());

        List<String> ids = ORDERS.parallelStream().map(Order::id).toList();                              // (3)!
        System.out.println("parallel, collected (not forEach-added) in encounter order: " + ids);
    }
}
