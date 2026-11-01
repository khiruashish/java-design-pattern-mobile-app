import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Parallel run: run legacy and new implementations on the same real inputs, serve the legacy result, and compare.
 * Differences are classified: known and accepted (e.g. the new code fixes a legacy rounding bug) vs unexplained.
 */
public class ParallelRunDemo {

    record Order(String id, long subtotalPaise, boolean subscriber) {}

    static final Function<Order, Long> LEGACY_FEE = o -> o.subtotalPaise() >= 19_900 ? 0 : (long) Math.floor(2500 * (o.subscriber() ? 0.65 : 1.0) / 100) * 100;
    static final Function<Order, Long> NEW_FEE = o -> o.subtotalPaise() > 19_900 ? 0 : Math.round(2500 * (o.subscriber() ? 0.65 : 1.0));

    public static void main(String[] args) {
        List<Order> traffic = List.of(new Order("FC-1", 15_000, false), new Order("FC-2", 25_000, false),
                new Order("FC-3", 12_000, true), new Order("FC-4", 19_900, false), new Order("FC-5", 8_000, false));
        Map<String, Integer> categories = new TreeMap<>();
        List<String> unexplained = new ArrayList<>();
        for (Order o : traffic) {
            long served = LEGACY_FEE.apply(o);                                                            // (1)!
            long candidate = NEW_FEE.apply(o);
            String category;
            if (served == candidate) category = "match";
            else if (o.subscriber() && Math.abs(served - candidate) < 100) category = "accepted: legacy truncated to whole rupees";   // (2)!
            else { category = "UNEXPLAINED"; unexplained.add(o.id() + " legacy=" + served + " new=" + candidate); }
            categories.merge(category, 1, Integer::sum);
        }
        System.out.println("comparison: " + categories);
        unexplained.forEach(u -> System.out.println("  investigate " + u));                                // (3)!
    }
}
