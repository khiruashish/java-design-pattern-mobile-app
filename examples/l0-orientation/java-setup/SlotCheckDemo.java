import java.time.LocalTime;
import java.util.List;

/** Checks which FreshCart delivery slots are still open at a given time. */
public class SlotCheckDemo {

    record Slot(LocalTime start, LocalTime end) {
        Slot {
            if (!end.isAfter(start)) throw new IllegalArgumentException("Slot must end after it starts: " + start + "-" + end);
        }
        @Override public String toString() { return start + "-" + end; }
    }

    static final List<Slot> SLOTS = List.of(
            new Slot(LocalTime.of(8, 0), LocalTime.of(10, 0)),
            new Slot(LocalTime.of(12, 0), LocalTime.of(14, 0)),
            new Slot(LocalTime.of(18, 0), LocalTime.of(20, 0)));

    public static void main(String[] args) {
        // Program arguments: java SlotCheckDemo.java 11:30
        LocalTime now = args.length > 0 ? LocalTime.parse(args[0]) : LocalTime.of(9, 15);       // (1)!
        // System property: java -Dfreshcart.cutoffMinutes=60 SlotCheckDemo.java
        int cutoffMinutes = Integer.getInteger("freshcart.cutoffMinutes", 30);                 // (2)!

        System.out.println("Time " + now + ", ordering closes " + cutoffMinutes + " min before a slot starts");
        for (Slot slot : SLOTS) {
            boolean open = now.plusMinutes(cutoffMinutes).isBefore(slot.start());
            System.out.println("  " + slot + (open ? "  open" : "  closed"));
        }
    }
}
