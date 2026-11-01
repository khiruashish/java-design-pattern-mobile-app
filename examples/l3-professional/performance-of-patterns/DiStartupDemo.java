import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Startup cost of a DI container: a reflective container discovers constructors and resolves dependencies at
 * runtime; hand wiring (or compile-time DI like Dagger, Micronaut, Spring AOT) does that work at build time.
 */
public class DiStartupDemo {

    public static final class Clock { }
    public static final class PriceRepo { public PriceRepo(Clock c) { } }
    public static final class StockRepo { public StockRepo(Clock c) { } }
    public static final class Pricing { public Pricing(PriceRepo r) { } }
    public static final class Stock { public Stock(StockRepo r) { } }
    public static final class Checkout { public Checkout(Pricing p, Stock s, Clock c) { } }

    /** A tiny reflective container: picks each class's only constructor and resolves parameters recursively. */
    static final class ReflectiveContainer {                                               // (1)!
        private final Map<Class<?>, Object> singletons = new HashMap<>();
        final List<String> created = new ArrayList<>();
        <T> T get(Class<T> type) {
            Object existing = singletons.get(type);
            if (existing != null) return type.cast(existing);
            try {
                Constructor<?> ctor = type.getConstructors()[0];
                Object[] deps = new Object[ctor.getParameterCount()];
                for (int i = 0; i < deps.length; i++) deps[i] = get(ctor.getParameterTypes()[i]);
                Object instance = ctor.newInstance(deps);
                singletons.put(type, instance);
                created.add(type.getSimpleName());
                return type.cast(instance);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot create " + type.getSimpleName(), e);
            }
        }
    }

    static Checkout wireByHand() {                                                         // (2)!
        Clock clock = new Clock();
        return new Checkout(new Pricing(new PriceRepo(clock)), new Stock(new StockRepo(clock)), clock);
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        ReflectiveContainer container = new ReflectiveContainer();
        Checkout viaContainer = container.get(Checkout.class);
        long t1 = System.nanoTime();
        Checkout byHand = wireByHand();
        long t2 = System.nanoTime();
        System.err.printf("reflective container: %d us, hand wiring: %d us (first call, cold JVM)%n", (t1 - t0) / 1000, (t2 - t1) / 1000);

        System.out.println("container created, in dependency order: " + container.created);   // (3)!
        System.out.println("Clock shared as a singleton: " + (container.get(Clock.class) == container.get(Clock.class)));
        System.out.println("both produced a Checkout: " + (viaContainer != null && byHand != null));
    }
}
