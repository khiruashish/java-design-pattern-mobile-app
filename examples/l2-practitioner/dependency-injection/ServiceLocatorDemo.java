import java.util.HashMap;
import java.util.Map;

/** Service Locator: classes FETCH their dependencies from a global registry. It works, but hides dependencies and couples tests. */
public class ServiceLocatorDemo {

    interface Payments { String charge(long cents); }

    static final class ServiceLocator {                                                   // (1)!
        private static final Map<Class<?>, Object> SERVICES = new HashMap<>();
        static <T> void register(Class<T> type, T impl) { SERVICES.put(type, impl); }
        static <T> T get(Class<T> type) {
            Object s = SERVICES.get(type);
            if (s == null) throw new IllegalStateException("no service registered for " + type.getSimpleName());
            return type.cast(s);
        }
        static void clear() { SERVICES.clear(); }
    }

    // ===================== With a locator: the constructor tells you nothing =====================
    static final class CheckoutWithLocator {
        String pay(long c) { return ServiceLocator.get(Payments.class).charge(c); }       // (2)!
    }

    // ===================== With DI: the constructor IS the documentation =====================
    record CheckoutWithDi(Payments payments) {                                            // (3)!
        String pay(long c) { return payments.charge(c); }
    }

    public static void main(String[] args) {
        try { new CheckoutWithLocator().pay(100); } catch (IllegalStateException e) { System.out.println("locator, nothing registered: " + e.getMessage() + " (found at runtime)"); }
        ServiceLocator.register(Payments.class, c -> "live charge " + c);
        System.out.println("locator: " + new CheckoutWithLocator().pay(100));
        ServiceLocator.register(Payments.class, c -> "test charge " + c);                  // a test overrides global state...
        System.out.println("locator after a test ran: " + new CheckoutWithLocator().pay(100) + "  <- leaked into other code");
        ServiceLocator.clear();

        System.out.println("DI: " + new CheckoutWithDi(c -> "test charge " + c).pay(100) + " (no global state)");
    }
}
