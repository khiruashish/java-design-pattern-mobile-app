import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/** Minimal: six everyday JDK calls, each one a named pattern, checked by behaviour rather than by name. */
public class JdkPatternSpottingDemo {

    public static void main(String[] args) {
        String receipt = new StringBuilder().append("FC-1").append(" | ").append("Rs 649").toString();   // (1)!
        System.out.println("Builder:        StringBuilder -> " + receipt);
        System.out.println("Factory method: LocalDate.of -> " + LocalDate.of(2026, 10, 31) + ", List.of -> " + List.of("MILK"));
        System.out.println("Flyweight:      Integer.valueOf(42) shared -> " + (Integer.valueOf(42) == Integer.valueOf(42)));
        System.out.println("Proxy:          unmodifiableList blocks writes -> " + blocksWrites(Collections.unmodifiableList(new java.util.ArrayList<>(List.of("A")))));
        System.out.println("Singleton:      Runtime.getRuntime() -> " + (Runtime.getRuntime() == Runtime.getRuntime()));
        try (var pool = Executors.newFixedThreadPool(2)) {                                                // (2)!
            System.out.println("Command:        Callable submitted to an executor -> " + pool.submit(() -> "ran").get());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static boolean blocksWrites(List<String> list) {
        try { list.add("B"); return false; } catch (UnsupportedOperationException e) { return true; }
    }
}
