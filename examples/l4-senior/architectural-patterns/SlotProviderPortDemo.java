import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * FreshCart is moving delivery slots from its own scheduler to ParcelGo. The slot logic was buried in a layered
 * service; introducing a port lets both providers plug in, and one contract test runs against every adapter.
 */
public class SlotProviderPortDemo {

    record Slot(String id, String window, int capacityLeft) {}

    interface SlotProvider {                                                                // (1)!
        List<Slot> available(String pin, String date);
        boolean hold(String slotId, String orderId);
    }

    /** Adapter 1: FreshCart's in-house scheduler (in-memory here). */
    static final class InHouseScheduler implements SlotProvider {
        private final Map<String, Integer> capacity = new TreeMap<>(Map.of("S-07", 0, "S-09", 3, "S-18", 10));
        public List<Slot> available(String pin, String date) {
            List<Slot> out = new ArrayList<>();
            capacity.forEach((id, left) -> { if (left > 0) out.add(new Slot(id, id.substring(2) + ":00", left)); });
            return out;
        }
        public boolean hold(String slotId, String orderId) {
            Integer left = capacity.get(slotId);
            if (left == null || left == 0) return false;
            capacity.put(slotId, left - 1);
            return true;
        }
    }

    /** Adapter 2: translates ParcelGo's API model (minutes since midnight, "remaining" counts) into ours. */
    static final class ParcelGoAdapter implements SlotProvider {                               // (2)!
        record ParcelGoWindow(String ref, int startMinute, int remaining) {}
        private final Map<String, ParcelGoWindow> remote = new TreeMap<>(Map.of(
                "PG-A", new ParcelGoWindow("PG-A", 540, 2), "PG-B", new ParcelGoWindow("PG-B", 1080, 0)));
        public List<Slot> available(String pin, String date) {
            return remote.values().stream().filter(w -> w.remaining() > 0)
                    .map(w -> new Slot(w.ref(), String.format("%02d:%02d", w.startMinute() / 60, w.startMinute() % 60), w.remaining())).toList();
        }
        public boolean hold(String slotId, String orderId) {
            ParcelGoWindow w = remote.get(slotId);
            if (w == null || w.remaining() == 0) return false;
            remote.put(slotId, new ParcelGoWindow(w.ref(), w.startMinute(), w.remaining() - 1));
            return true;
        }
    }

    /** One contract every adapter must satisfy. */
    static List<String> contract(SlotProvider p) {                                              // (3)!
        List<String> failures = new ArrayList<>();
        List<Slot> slots = p.available("560034", "2026-11-02");
        if (slots.isEmpty()) failures.add("no slots offered");
        if (slots.stream().anyMatch(s -> s.capacityLeft() <= 0)) failures.add("offered a full slot");
        Slot first = slots.get(0);
        for (int i = 0; i < first.capacityLeft(); i++) if (!p.hold(first.id(), "O" + i)) failures.add("hold " + i + " refused below capacity");
        if (p.hold(first.id(), "one-too-many")) failures.add("overbooked " + first.id());
        if (p.hold("NO-SUCH-SLOT", "x")) failures.add("held an unknown slot");
        return failures;
    }

    public static void main(String[] args) {
        for (SlotProvider p : List.of(new InHouseScheduler(), new ParcelGoAdapter())) {
            System.out.println(p.getClass().getSimpleName() + " offers " + p.available("560034", "2026-11-02"));
        }
        for (SlotProvider p : List.of(new InHouseScheduler(), new ParcelGoAdapter())) {
            List<String> failures = contract(p);
            System.out.println("contract " + p.getClass().getSimpleName() + ": " + (failures.isEmpty() ? "pass" : failures));
        }
    }
}
