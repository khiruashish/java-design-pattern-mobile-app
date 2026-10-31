import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Copy constructors and copy factories, and why immutable records rarely need copying at all. */
public class CopyConstructorDemo {

    // ===================== Mutable class: explicit copy constructor =====================
    static final class DeliveryZone {
        final String name; final List<String> pincodes; final Map<String, Integer> slotCapacity;

        DeliveryZone(String name, List<String> pincodes, Map<String, Integer> slotCapacity) {
            this.name = name; this.pincodes = new ArrayList<>(pincodes); this.slotCapacity = new HashMap<>(slotCapacity);
        }
        DeliveryZone(DeliveryZone other) { this(other.name, other.pincodes, other.slotCapacity); }   // (1)!
        static DeliveryZone copyOf(DeliveryZone other, String newName) { return new DeliveryZone(newName, other.pincodes, other.slotCapacity); }   // (2)!
        public String toString() { return name + " " + pincodes + " " + new java.util.TreeMap<>(slotCapacity); }
    }

    // ===================== Immutable record: share freely, "copy" with changes via withers =====================
    record Slot(String label, int capacity) {
        Slot withCapacity(int c) { return new Slot(label, c); }                            // (3)!
    }

    public static void main(String[] args) {
        DeliveryZone kothrud = new DeliveryZone("kothrud", List.of("411038"), Map.of("18:00", 40));
        DeliveryZone copy = new DeliveryZone(kothrud);
        copy.slotCapacity.put("18:00", 60);
        DeliveryZone baner = DeliveryZone.copyOf(kothrud, "baner");
        baner.pincodes.add("411045");
        System.out.println("original: " + kothrud);
        System.out.println("copy:     " + copy);
        System.out.println("copyOf:   " + baner);

        Slot evening = new Slot("18:00-19:00", 40);
        Slot festive = evening.withCapacity(80);
        System.out.println("records: " + evening + " -> " + festive + ", original unchanged: " + (evening.capacity() == 40));
    }
}
