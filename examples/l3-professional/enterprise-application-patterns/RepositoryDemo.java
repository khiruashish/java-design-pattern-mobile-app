import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A Repository looks like an in-memory collection of aggregates; callers never see SQL or tables. */
public class RepositoryDemo {

    record Rider(String id, String name, String zone, boolean available, double rating) {}

    interface RiderRepository {                                        // (1)!
        Optional<Rider> findById(String id);
        List<Rider> findAvailableInZone(String zone);
        void save(Rider rider);
    }

    /** Fast, deterministic implementation for tests and this demo. A JDBC or JPA one has the same interface. */
    static final class InMemoryRiderRepository implements RiderRepository {   // (2)!
        private final Map<String, Rider> rows = new TreeMap<>();
        public Optional<Rider> findById(String id) { return Optional.ofNullable(rows.get(id)); }
        public List<Rider> findAvailableInZone(String zone) {
            return rows.values().stream()
                    .filter(r -> r.available() && r.zone().equals(zone))
                    .sorted(Comparator.comparingDouble(Rider::rating).reversed())
                    .toList();
        }
        public void save(Rider rider) { rows.put(rider.id(), rider); }
    }

    /** Domain-facing code depends only on the interface. */
    static Optional<Rider> bestRiderFor(RiderRepository riders, String zone) {   // (3)!
        return riders.findAvailableInZone(zone).stream().findFirst();
    }

    public static void main(String[] args) {
        RiderRepository riders = new InMemoryRiderRepository();
        riders.save(new Rider("R1", "Arjun", "KORAMANGALA", true, 4.6));
        riders.save(new Rider("R2", "Bina", "KORAMANGALA", true, 4.9));
        riders.save(new Rider("R3", "Chetan", "INDIRANAGAR", true, 4.8));
        riders.save(new Rider("R4", "Divya", "KORAMANGALA", false, 5.0));

        System.out.println("available in Koramangala: "
                + riders.findAvailableInZone("KORAMANGALA").stream().map(Rider::name).toList());
        System.out.println("best for Koramangala: " + bestRiderFor(riders, "KORAMANGALA").map(Rider::name).orElse("none"));
        System.out.println("best for HSR: " + bestRiderFor(riders, "HSR").map(Rider::name).orElse("none"));
        System.out.println("R9 exists: " + riders.findById("R9").isPresent());
    }
}
