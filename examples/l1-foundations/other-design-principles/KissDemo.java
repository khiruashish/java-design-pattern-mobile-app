import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** KISS: the same delivery-slot rule, written clever and written simple. */
public class KissDemo {

    record Slot(String id, LocalTime start, int capacity, int booked) { }

    // ===================== Clever: a generic rule engine for one rule =====================
    static List<String> openSlotsClever(List<Slot> slots, LocalTime now) {                // (1)!
        Map<String, Predicate<Slot>> rules = Map.of(
                "capacity", s -> s.booked() < s.capacity(),
                "cutoff", s -> now.plusMinutes(30).isBefore(s.start()));
        return slots.stream()
                .filter(rules.values().stream().reduce(x -> true, Predicate::and))
                .map(Slot::id).sorted().collect(Collectors.toList());
    }

    // ===================== Simple: says exactly what the business rule says =====================
    static final int CUTOFF_MINUTES = 30;

    static boolean isBookable(Slot slot, LocalTime now) {                                  // (2)!
        boolean hasRoom = slot.booked() < slot.capacity();
        boolean beforeCutoff = now.plusMinutes(CUTOFF_MINUTES).isBefore(slot.start());
        return hasRoom && beforeCutoff;
    }

    static List<String> openSlots(List<Slot> slots, LocalTime now) {
        return slots.stream().filter(s -> isBookable(s, now)).map(Slot::id).sorted().toList();
    }

    public static void main(String[] args) {
        List<Slot> slots = List.of(
                new Slot("S1-08:00", LocalTime.of(8, 0), 20, 5),
                new Slot("S2-12:00", LocalTime.of(12, 0), 20, 20),
                new Slot("S3-18:00", LocalTime.of(18, 0), 20, 19));
        LocalTime now = LocalTime.of(9, 0);
        System.out.println("clever: " + openSlotsClever(slots, now));
        System.out.println("simple: " + openSlots(slots, now));
        System.out.println("why S1 closed: bookable=" + isBookable(slots.get(0), now) + " (past cutoff)");     // (3)!
    }
}
