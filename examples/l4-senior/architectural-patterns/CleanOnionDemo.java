import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean / onion architecture's dependency rule: source code dependencies point inwards only. Entities know nothing
 * of use cases; use cases know nothing of adapters. This demo checks the rule with reflection, like ArchUnit does.
 */
public class CleanOnionDemo {

    enum Ring { ENTITIES, USE_CASES, ADAPTERS, FRAMEWORKS }                                 // (1)!
    @Retention(RetentionPolicy.RUNTIME) @interface Layer { Ring value(); }

    @Layer(Ring.ENTITIES) record Subscription(String customer, int litresPerDay) {}
    @Layer(Ring.USE_CASES) interface SubscriptionStore { void save(Subscription s); }
    @Layer(Ring.USE_CASES) static final class Subscribe {
        private final SubscriptionStore store;
        Subscribe(SubscriptionStore store) { this.store = store; }
        void run(String customer, int litres) { store.save(new Subscription(customer, litres)); }
    }
    @Layer(Ring.ADAPTERS) static final class JdbcSubscriptionStore implements SubscriptionStore {
        public void save(Subscription s) { }
    }
    @Layer(Ring.ADAPTERS) static final class SubscribeController {
        private final Subscribe useCase;
        SubscribeController(Subscribe useCase) { this.useCase = useCase; }
    }

    /** A rule breaker: a use case that depends on a concrete adapter. */
    @Layer(Ring.USE_CASES) static final class BadRenewal {                                    // (2)!
        private final JdbcSubscriptionStore store;
        BadRenewal(JdbcSubscriptionStore store) { this.store = store; }
    }

    static List<String> violations(Class<?>... classes) {                                     // (3)!
        List<String> out = new ArrayList<>();
        for (Class<?> c : classes) {
            Ring own = c.getAnnotation(Layer.class).value();
            List<Class<?>> deps = new ArrayList<>();
            for (Field f : c.getDeclaredFields()) deps.add(f.getType());
            for (Constructor<?> k : c.getDeclaredConstructors()) deps.addAll(List.of(k.getParameterTypes()));
            for (Class<?> d : deps) {
                Layer l = d.getAnnotation(Layer.class);
                if (l != null && l.value().ordinal() > own.ordinal())
                    out.add(c.getSimpleName() + " (" + own + ") depends on " + d.getSimpleName() + " (" + l.value() + ")");
            }
        }
        return out.stream().distinct().toList();
    }

    public static void main(String[] args) {
        System.out.println("clean design: " + violations(Subscription.class, SubscriptionStore.class, Subscribe.class,
                JdbcSubscriptionStore.class, SubscribeController.class));
        System.out.println("with BadRenewal: " + violations(Subscribe.class, BadRenewal.class));
    }
}
