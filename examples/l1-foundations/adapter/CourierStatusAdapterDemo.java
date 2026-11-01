import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Realistic Adapter: two couriers report tracking in different formats; adapters translate both into one model. */
public class CourierStatusAdapterDemo {

    enum Status { PICKED_UP, IN_TRANSIT, DELIVERED, FAILED, UNKNOWN }
    record TrackingUpdate(String orderId, Status status, Instant at, String courier) { }

    interface TrackingSource { TrackingUpdate translate(String rawPayload); }             // (1)!

    /** QuickDrop: "orderId|statusCode|epochSeconds", codes 10/20/30/90. */
    static final class QuickDropAdapter implements TrackingSource {
        private static final Map<Integer, Status> CODES = Map.of(10, Status.PICKED_UP, 20, Status.IN_TRANSIT, 30, Status.DELIVERED, 90, Status.FAILED);
        public TrackingUpdate translate(String raw) {
            String[] f = raw.split("\\|");
            Status s = CODES.getOrDefault(Integer.parseInt(f[1]), Status.UNKNOWN);       // (2)!
            return new TrackingUpdate(f[0], s, Instant.ofEpochSecond(Long.parseLong(f[2])), "QuickDrop");
        }
    }

    /** ParcelGo: "ref=..;state=..;time=ISO", states as words, refs prefixed "PG-". */
    static final class ParcelGoAdapter implements TrackingSource {
        public TrackingUpdate translate(String raw) {
            Map<String, String> f = new java.util.HashMap<>();
            for (String kv : raw.split(";")) { String[] p = kv.split("=", 2); f.put(p[0], p[1]); }
            Status s = switch (f.get("state")) {
                case "COLLECTED" -> Status.PICKED_UP;
                case "ON_ROUTE", "NEAR_YOU" -> Status.IN_TRANSIT;                         // (3)!
                case "HANDED_OVER" -> Status.DELIVERED;
                case "RETURNED", "CUSTOMER_UNREACHABLE" -> Status.FAILED;
                default -> Status.UNKNOWN;
            };
            return new TrackingUpdate(f.get("ref").substring(3), s, Instant.parse(f.get("time")), "ParcelGo");   // (4)!
        }
    }

    public static void main(String[] args) {
        TrackingSource qd = new QuickDropAdapter(), pg = new ParcelGoAdapter();
        List<TrackingUpdate> updates = List.of(
                qd.translate("FC-1901|20|1793606400"),
                pg.translate("ref=PG-FC-1902;state=NEAR_YOU;time=2026-11-02T10:05:00Z"),
                qd.translate("FC-1903|55|1793606700"),
                pg.translate("ref=PG-FC-1904;state=CUSTOMER_UNREACHABLE;time=2026-11-02T10:12:00Z"));
        updates.forEach(System.out::println);
        System.out.println("needs review (unknown codes): " + updates.stream().filter(u -> u.status() == Status.UNKNOWN).map(TrackingUpdate::orderId).toList());
    }
}
