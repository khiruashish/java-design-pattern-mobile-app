import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Decorators as functions: compose behaviour with andThen/compose and higher-order wrappers. */
public class FunctionalDecoratorDemo {

    static UnaryOperator<Long> percentOff(int p) { return c -> c - c * p / 100; }
    static UnaryOperator<Long> plusTax(int p) { return c -> c + c * p / 100; }
    static UnaryOperator<Long> roundToRupee() { return c -> Math.round(c / 100.0) * 100; }

    /** A generic decorator: wraps ANY supplier with logging of how many calls were made. */
    static <T> Supplier<T> counted(Supplier<T> inner, List<String> log, String name) {   // (1)!
        int[] calls = {0};
        return () -> { calls[0]++; log.add(name + " call #" + calls[0]); return inner.get(); };
    }

    /** A generic memoising decorator. */
    static <T> Supplier<T> memoised(Supplier<T> inner) {                                  // (2)!
        Object[] cache = {null};
        return () -> {
            if (cache[0] == null) cache[0] = inner.get();
            @SuppressWarnings("unchecked") T t = (T) cache[0];
            return t;
        };
    }

    public static void main(String[] args) {
        Function<Long, Long> pipeline = percentOff(10).andThen(plusTax(5)).andThen(roundToRupee());   // (3)!
        System.out.println("37800 -> " + pipeline.apply(37_800L));
        Function<Long, Long> taxFirst = percentOff(10).compose(plusTax(5));               // compose = run the argument first
        System.out.println("tax then discount: " + taxFirst.apply(37_800L));

        List<String> log = new ArrayList<>();
        Supplier<List<String>> categories = memoised(counted(() -> List.of("dairy", "bakery"), log, "loadCategories"));
        categories.get(); categories.get(); categories.get();
        System.out.println("categories " + categories.get() + ", backend calls: " + log);
    }
}
