import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/** A flyweight factory hands out shared instances by key, creating each one at most once, safely across threads. */
public class FlyweightFactoryDemo {

    record UnitOfMeasure(String code, String label, int decimals) { }                         // (1)!

    static final class Units {
        private static final Map<String, UnitOfMeasure> CACHE = new ConcurrentHashMap<>();
        static final AtomicInteger created = new AtomicInteger();
        static UnitOfMeasure of(String code) {
            return CACHE.computeIfAbsent(code, c -> {                                          // (2)!
                created.incrementAndGet();
                return switch (c) {
                    case "KG" -> new UnitOfMeasure("KG", "kilogram", 3);
                    case "L" -> new UnitOfMeasure("L", "litre", 3);
                    case "PCS" -> new UnitOfMeasure("PCS", "piece", 0);
                    default -> throw new IllegalArgumentException("unknown unit " + c);       // (3)!
                };
            });
        }
    }

    public static void main(String[] args) {
        List<String> feed = List.of("KG", "L", "PCS");
        IntStream.range(0, 300_000).parallel().forEach(i -> Units.of(feed.get(i % 3)));
        System.out.println("300,000 lookups from many threads -> instances created: " + Units.created.get());
        System.out.println("same instance: " + (Units.of("KG") == Units.of("KG")));
        try { Units.of("BOX"); } catch (IllegalArgumentException e) { System.out.println(e.getMessage() + " (nothing cached for it)"); }
    }
}
