/** Object adapter: wrap a third-party client and expose OUR interface. Composition, not inheritance. */
public class ObjectAdapterDemo {

    // ===================== Our interface (the "target") =====================
    record Location(double lat, double lng, int etaMinutes) { }
    interface RiderTracker { Location locate(String orderId); }                             // (1)!

    // ===================== Third-party client we can't change (the "adaptee") =====================
    static final class ParcelGoClient {
        /** Returns "lat;lng;etaSeconds" or "NOT_FOUND". */
        String fetchPosition(String shipmentRef) {
            return shipmentRef.equals("PG-77") ? "18.5204;73.8567;540" : "NOT_FOUND";
        }
    }

    // ===================== The adapter =====================
    static final class ParcelGoTracker implements RiderTracker {
        private final ParcelGoClient client;                                              // (2)!
        private final java.util.Map<String, String> shipmentRefs;
        ParcelGoTracker(ParcelGoClient client, java.util.Map<String, String> refs) { this.client = client; this.shipmentRefs = refs; }

        public Location locate(String orderId) {
            String ref = shipmentRefs.get(orderId);                                       // (3)!
            if (ref == null) throw new IllegalArgumentException("no ParcelGo shipment for " + orderId);
            String raw = client.fetchPosition(ref);
            if (raw.equals("NOT_FOUND")) throw new IllegalStateException("ParcelGo lost track of " + ref);
            String[] p = raw.split(";");
            return new Location(Double.parseDouble(p[0]), Double.parseDouble(p[1]), (Integer.parseInt(p[2]) + 59) / 60);   // (4)!
        }
    }

    public static void main(String[] args) {
        RiderTracker tracker = new ParcelGoTracker(new ParcelGoClient(), java.util.Map.of("FC-1701", "PG-77", "FC-1702", "PG-99"));
        System.out.println("FC-1701 -> " + tracker.locate("FC-1701"));
        for (String id : new String[]{"FC-1702", "FC-1703"}) {
            try { tracker.locate(id); } catch (RuntimeException e) { System.out.println(id + " -> " + e.getMessage()); }
        }
    }
}
