import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** DDD factories create whole, valid aggregates; repositories store and load whole aggregates by root ID only. */
public class DddFactoryRepositoryDemo {

    record Plan(String code, int litresPerDay, long pricePerLitrePaise, List<String> allowedDays) {}
    record Subscription(String id, String customerId, Plan plan, List<String> days, String status) {}

    static final class SubscriptionFactory {                                               // (1)!
        private final Map<String, Plan> catalog;
        private int seq = 1;
        SubscriptionFactory(Map<String, Plan> catalog) { this.catalog = catalog; }
        Subscription create(String customerId, String planCode, List<String> days) {
            Plan plan = Optional.ofNullable(catalog.get(planCode)).orElseThrow(() -> new IllegalArgumentException("unknown plan " + planCode));
            if (days.isEmpty() || !plan.allowedDays().containsAll(days)) throw new IllegalArgumentException(planCode + " delivers only on " + plan.allowedDays());
            return new Subscription("SUB-" + seq++, customerId, plan, List.copyOf(days), "ACTIVE");
        }
    }

    interface SubscriptionRepository {                                                       // (2)!
        void save(Subscription s);
        Optional<Subscription> byId(String id);
        List<Subscription> activeFor(String customerId);
    }
    static final class InMemorySubscriptions implements SubscriptionRepository {
        private final Map<String, Subscription> rows = new TreeMap<>();
        public void save(Subscription s) { rows.put(s.id(), s); }
        public Optional<Subscription> byId(String id) { return Optional.ofNullable(rows.get(id)); }
        public List<Subscription> activeFor(String c) { return rows.values().stream().filter(s -> s.customerId().equals(c) && s.status().equals("ACTIVE")).toList(); }
        // (3)!
    }

    public static void main(String[] args) {
        var weekdays = List.of("MON", "TUE", "WED", "THU", "FRI");
        var factory = new SubscriptionFactory(Map.of(
                "MILK-1L-WEEKDAY", new Plan("MILK-1L-WEEKDAY", 1, 6_800, weekdays),
                "MILK-2L-DAILY", new Plan("MILK-2L-DAILY", 2, 6_500, List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"))));
        SubscriptionRepository repo = new InMemorySubscriptions();

        repo.save(factory.create("C-17", "MILK-1L-WEEKDAY", List.of("MON", "WED", "FRI")));
        repo.save(factory.create("C-17", "MILK-2L-DAILY", List.of("SAT", "SUN")));
        try {
            factory.create("C-17", "MILK-1L-WEEKDAY", List.of("SUN"));
        } catch (IllegalArgumentException e) {
            System.out.println("factory refused: " + e.getMessage());
        }
        repo.activeFor("C-17").forEach(s -> System.out.println(s.id() + " " + s.plan().code() + " " + s.days()));
        System.out.println("SUB-2 loaded whole: " + repo.byId("SUB-2").map(s -> s.plan().litresPerDay() + "L on " + s.days()).orElse("none"));
    }
}
