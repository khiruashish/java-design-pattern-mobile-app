import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A reference implementation (golden path) is a real, running service that shows the approved way to combine
 * patterns. Each capability has a version; services are compared with the reference to measure drift, and the most
 * drifted services get help first. The reference teaches by example; the drift report shows where examples didn't land.
 */
public class ReferenceImplementationDemo {

    record Capability(String name, int version) {}

    static final Set<Capability> REFERENCE = Set.of(                                               // (1)!
            new Capability("health-and-readiness", 1),
            new Capability("structured-logging", 2),
            new Capability("timeouts-and-retries", 2),
            new Capability("transactional-outbox", 1),
            new Capability("contract-tests", 1),
            new Capability("feature-flags", 1));

    record Service(String name, Set<Capability> has) {
        int driftPercent() {
            long missing = REFERENCE.stream().filter(c -> !has.contains(c)).count();               // (2)!
            return (int) (missing * 100 / REFERENCE.size());
        }
        Set<String> gaps() {
            Set<String> gaps = new TreeSet<>();
            for (Capability c : REFERENCE) {
                if (has.contains(c)) continue;
                boolean older = has.stream().anyMatch(h -> h.name().equals(c.name()));
                gaps.add(c.name() + (older ? " (old version)" : ""));
            }
            return gaps;
        }
    }

    public static void main(String[] args) {
        List<Service> services = List.of(
                new Service("checkout", Set.of(new Capability("health-and-readiness", 1), new Capability("structured-logging", 2),
                        new Capability("timeouts-and-retries", 2), new Capability("transactional-outbox", 1),
                        new Capability("contract-tests", 1), new Capability("feature-flags", 1))),
                new Service("catalogue", Set.of(new Capability("health-and-readiness", 1), new Capability("structured-logging", 1),
                        new Capability("timeouts-and-retries", 2), new Capability("feature-flags", 1))),
                new Service("loyalty", Set.of(new Capability("health-and-readiness", 1), new Capability("timeouts-and-retries", 1))));

        Map<String, Integer> drift = new LinkedHashMap<>();
        services.stream().sorted(Comparator.comparingInt(Service::driftPercent).reversed().thenComparing(Service::name)) // (3)!
                .forEach(s -> {
                    drift.put(s.name(), s.driftPercent());
                    System.out.println(s.name() + ": drift " + s.driftPercent() + "%" + (s.gaps().isEmpty() ? "" : ", gaps " + s.gaps()));
                });
        System.out.println("help first: " + drift.keySet().iterator().next());
    }
}
