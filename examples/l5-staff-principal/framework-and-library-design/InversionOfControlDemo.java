import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Inversion of control in your own framework: user code declares what it needs (constructor parameters) and the
 * framework decides when and how objects are built and called. This tiny container resolves constructor dependencies
 * recursively, caches singletons and reports dependency cycles with the full path.
 */
public class InversionOfControlDemo {

    static final class Container {
        private final Map<Class<?>, Object> singletons = new HashMap<>();
        private final Deque<Class<?>> resolving = new ArrayDeque<>();

        <T> T get(Class<T> type) {
            Object existing = singletons.get(type);
            if (existing != null) return type.cast(existing);
            if (resolving.contains(type)) {                                                        // (1)!
                StringBuilder path = new StringBuilder();
                resolving.descendingIterator().forEachRemaining(c -> path.append(c.getSimpleName()).append(" -> "));
                throw new IllegalStateException("dependency cycle: " + path + type.getSimpleName());
            }
            resolving.push(type);
            try {
                Constructor<?> ctor = type.getDeclaredConstructors()[0];
                Object[] deps = new Object[ctor.getParameterCount()];
                for (int i = 0; i < deps.length; i++) deps[i] = get(ctor.getParameterTypes()[i]);   // (2)!
                Object created = ctor.newInstance(deps);
                System.out.println("created " + type.getSimpleName());
                singletons.put(type, created);                                                     // (3)!
                return type.cast(created);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            } finally {
                resolving.pop();
            }
        }
    }

    record PriceRepository() {}
    record TaxRules() {}
    record PricingService(PriceRepository prices, TaxRules tax) {}
    record CheckoutService(PricingService pricing, PriceRepository prices) {}

    record AService(BService b) {}
    record BService(AService a) {}

    public static void main(String[] args) {
        Container container = new Container();
        CheckoutService checkout = container.get(CheckoutService.class);
        System.out.println("same PriceRepository shared: " + (checkout.prices() == checkout.pricing().prices()));
        try {
            container.get(AService.class);
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }
}
