import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** What Spring/Guice do at their core: find a constructor, resolve its parameters by type, cache singletons, detect cycles. */
public class MiniContainerDemo {

    static final class Container {
        private final Map<Class<?>, Class<?>> bindings = new HashMap<>();
        private final Map<Class<?>, Object> singletons = new HashMap<>();
        private final Deque<Class<?>> resolving = new ArrayDeque<>();

        <T> void bind(Class<T> type, Class<? extends T> impl) { bindings.put(type, impl); }   // (1)!

        <T> T get(Class<T> type) {
            if (singletons.containsKey(type)) return type.cast(singletons.get(type));
            if (resolving.contains(type)) throw new IllegalStateException("dependency cycle: " + String.join(" -> ", resolving.reversed().stream().map(Class::getSimpleName).toList()) + " -> " + type.getSimpleName());   // (2)!
            resolving.push(type);
            try {
                Class<?> impl = bindings.getOrDefault(type, type);
                Constructor<?> ctor = impl.getDeclaredConstructors()[0];                     // (3)!
                Object[] params = new Object[ctor.getParameterCount()];
                for (int i = 0; i < params.length; i++) params[i] = get(ctor.getParameterTypes()[i]);   // (4)!
                ctor.setAccessible(true);
                Object instance = ctor.newInstance(params);
                singletons.put(type, instance);
                return type.cast(instance);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot create " + type.getSimpleName(), e);
            } finally {
                resolving.pop();
            }
        }
    }

    interface Payments { String charge(long c); }
    static final class FakePayments implements Payments { public String charge(long c) { return "charged " + c; } }
    static final class OrderRepository { int saved; void save() { saved++; } }
    static final class CheckoutService {
        final Payments payments; final OrderRepository orders;
        CheckoutService(Payments payments, OrderRepository orders) { this.payments = payments; this.orders = orders; }
        String checkout(long c) { orders.save(); return payments.charge(c); }
    }
    static final class ReportService { final OrderRepository orders; ReportService(OrderRepository orders) { this.orders = orders; } }

    static final class A { A(B b) { } }
    static final class B { B(A a) { } }

    public static void main(String[] args) {
        Container c = new Container();
        c.bind(Payments.class, FakePayments.class);
        CheckoutService checkout = c.get(CheckoutService.class);
        System.out.println(checkout.checkout(4_999));
        System.out.println("same repository in both services: " + (checkout.orders == c.get(ReportService.class).orders));
        try { c.get(A.class); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
    }
}
