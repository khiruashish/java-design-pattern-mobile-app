import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Higher-order functions take or return functions, so cross-cutting behaviour (retry, timing, caching) is written once. */
public class HigherOrderFunctionsDemo {

    static <T> Supplier<T> withRetry(Supplier<T> call, int attempts) {                                 // (1)!
        return () -> {
            RuntimeException last = null;
            for (int i = 1; i <= attempts; i++) {
                try { return call.get(); } catch (RuntimeException e) { last = e; System.out.println("  attempt " + i + " failed: " + e.getMessage()); }
            }
            throw last;
        };
    }

    static <A, B> Function<A, B> memoize(Function<A, B> f) {                                          // (2)!
        Map<A, B> cache = new HashMap<>();
        return a -> cache.computeIfAbsent(a, f);
    }

    static <A, B> Function<A, B> logged(String name, Function<A, B> f) {                               // (3)!
        return a -> { B b = f.apply(a); System.out.println("  " + name + "(" + a + ") = " + b); return b; };
    }

    public static void main(String[] args) {
        int[] calls = {0};
        Supplier<String> flakyStock = () -> { if (++calls[0] < 3) throw new IllegalStateException("timeout"); return "MILK: 42 left"; };
        System.out.println("stock: " + withRetry(flakyStock, 3).get());

        int[] lookups = {0};
        Function<String, Integer> pincodeToZone = memoize(logged("zoneLookup", pin -> { lookups[0]++; return pin.startsWith("411") ? 1 : 2; }));
        for (String pin : new String[] {"411001", "560001", "411001", "411001"}) pincodeToZone.apply(pin);
        System.out.println("4 calls, " + lookups[0] + " real lookups");
    }
}
