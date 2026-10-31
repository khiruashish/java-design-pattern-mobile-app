import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** One-method patterns collapse into lambdas: Strategy, Command and Template Method. */
public class LambdasReplacePatternsDemo {

    record Slot(String id, int hour, long feePaise, int ridersFree) { }

    // Strategy: a class per algorithm before; now a function value (or a Comparator)
    static final Comparator<Slot> CHEAPEST = Comparator.comparingLong(Slot::feePaise);                    // (1)!
    static final Comparator<Slot> EARLIEST = Comparator.comparingInt(Slot::hour);
    static final Comparator<Slot> MOST_RIDERS_THEN_CHEAPEST = Comparator.comparingInt(Slot::ridersFree).reversed().thenComparing(CHEAPEST);

    // Command: an object with execute() before; now a Runnable in a queue
    static final Deque<Runnable> jobs = new ArrayDeque<>();                                              // (2)!

    // Template Method: an abstract class with a hook before; now the hook is a parameter
    static String exportSlots(List<Slot> slots, Function<Slot, String> formatRow) {                      // (3)!
        StringBuilder sb = new StringBuilder("--- slots ---\n");
        slots.forEach(s -> sb.append(formatRow.apply(s)).append('\n'));
        return sb.append("--- ").append(slots.size()).append(" rows ---").toString();
    }

    public static void main(String[] args) {
        List<Slot> slots = List.of(new Slot("S-08", 8, 2_500, 2), new Slot("S-18", 18, 1_500, 5), new Slot("S-20", 20, 1_500, 1));
        System.out.println("cheapest: " + slots.stream().min(CHEAPEST).orElseThrow().id()
                + ", earliest: " + slots.stream().min(EARLIEST).orElseThrow().id()
                + ", most riders: " + slots.stream().min(MOST_RIDERS_THEN_CHEAPEST).orElseThrow().id());

        jobs.add(() -> System.out.println("job: release unpaid slot holds"));
        jobs.add(() -> System.out.println("job: send slot reminders"));
        while (!jobs.isEmpty()) jobs.poll().run();

        System.out.println(exportSlots(slots, s -> s.id() + "," + s.hour() + "," + s.feePaise()));
        ToLongFunction<Slot> revenueIfFull = s -> s.feePaise() * s.ridersFree();
        System.out.println("revenue if full: " + slots.stream().mapToLong(revenueIfFull).sum());
    }
}
