import java.util.List;
import java.util.function.Predicate;

/** java.util.function.Predicate already has and/or/negate: often all the "Specification" you need in memory. */
public class PredicateAsSpecDemo {

    record Slot(String id, int hour, int capacityLeft, boolean express) { }

    static final Predicate<Slot> HAS_ROOM = s -> s.capacityLeft() > 0;                              // (1)!
    static final Predicate<Slot> EVENING = s -> s.hour() >= 17;
    static final Predicate<Slot> EXPRESS = Slot::express;

    public static void main(String[] args) {
        List<Slot> slots = List.of(new Slot("S-09", 9, 4, false), new Slot("S-18", 18, 0, false),
                                   new Slot("S-19", 19, 2, true), new Slot("S-20", 20, 6, false));

        Predicate<Slot> eveningStandard = HAS_ROOM.and(EVENING).and(Predicate.not(EXPRESS));           // (2)!
        System.out.println("evening, standard, with room: " + slots.stream().filter(eveningStandard).map(Slot::id).toList());
        System.out.println("express or morning: " + slots.stream().filter(EXPRESS.or(EVENING.negate())).map(Slot::id).toList());

        System.out.println("can it explain itself? toString() is a lambda class name: " + eveningStandard.toString().contains("$$Lambda"));   // (3)!
    }
}
