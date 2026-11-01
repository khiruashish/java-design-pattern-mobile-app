import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Production-grade dynamic proxy: what Spring AOP does for @PreAuthorize, @Cacheable and @Timed, in ~80 lines.
 * - annotations on the INTERFACE drive behaviour (security check, per-argument cache, metrics)
 * - order is fixed: security -> cache -> metrics -> target (a denied call never fills the cache)
 * - real exceptions are rethrown unwrapped; Object methods (toString/equals/hashCode) pass through sensibly
 * - only interfaces are proxied; the factory refuses classes with a clear message
 */
public class ProductionProxyDemo {

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface RequiresRole { String value(); }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @interface Cached { }

    interface OrderAdmin {
        @Cached String orderSummary(String orderId);
        @RequiresRole("SUPERVISOR") String refund(String orderId, long paise);
        String ping();
    }

    static final class Metrics {
        final Map<String, long[]> calls = new TreeMap<>();                                    // method -> [count, errors]
        synchronized void record(String m, boolean error) { long[] c = calls.computeIfAbsent(m, k -> new long[2]); c[0]++; if (error) c[1]++; }
        public synchronized String toString() { StringBuilder sb = new StringBuilder(); calls.forEach((k, v) -> sb.append(k).append("=").append(v[0]).append(v[1] > 0 ? "(" + v[1] + " err) " : " ")); return sb.toString().trim(); }
    }

    static <T> T enhance(Class<T> iface, T target, Supplier<Set<String>> currentRoles, Metrics metrics) {
        if (!iface.isInterface()) throw new IllegalArgumentException(iface.getSimpleName() + " is not an interface; use composition or a subclass proxy");   // (1)!
        Map<List<Object>, Object> cache = new ConcurrentHashMap<>();
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {                                  // (2)!
                return switch (method.getName()) {
                    case "toString" -> "Proxy[" + iface.getSimpleName() + " -> " + target + "]";
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> method.invoke(target, args);
                };
            }
            RequiresRole role = method.getAnnotation(RequiresRole.class);
            if (role != null && !currentRoles.get().contains(role.value())) {                 // (3)!
                metrics.record(method.getName(), true);
                throw new SecurityException(method.getName() + " requires " + role.value());
            }
            List<Object> key = method.isAnnotationPresent(Cached.class) ? List.of(method.getName(), Arrays.asList(args)) : null;
            if (key != null && cache.containsKey(key)) { metrics.record(method.getName() + ".cacheHit", false); return cache.get(key); }
            try {
                Object result = method.invoke(target, args);
                metrics.record(method.getName(), false);
                if (key != null && result != null) cache.put(key, result);                    // (4)!
                return result;
            } catch (InvocationTargetException e) {
                metrics.record(method.getName(), true);
                throw e.getCause();                                                            // (5)!
            }
        });
        return iface.cast(proxy);
    }

    static final class RealOrderAdmin implements OrderAdmin {
        int summaryLoads;
        public String orderSummary(String id) { summaryLoads++; if (id.isBlank()) throw new IllegalArgumentException("order id is blank"); return id + ": 4 items, Rs 649"; }
        public String refund(String id, long p) { return "refunded Rs " + p / 100 + " on " + id; }
        public String ping() { return "pong"; }
        public String toString() { return "RealOrderAdmin"; }
    }

    public static void main(String[] args) {
        RealOrderAdmin real = new RealOrderAdmin();
        Metrics metrics = new Metrics();
        var roles = new java.util.concurrent.atomic.AtomicReference<Set<String>>(Set.of("AGENT"));
        OrderAdmin admin = enhance(OrderAdmin.class, real, roles::get, metrics);

        System.out.println(admin.orderSummary("FC-4001"));
        System.out.println(admin.orderSummary("FC-4001"));
        System.out.println("real summary loads: " + real.summaryLoads);
        try { admin.refund("FC-4001", 50_000); } catch (SecurityException e) { System.out.println("denied: " + e.getMessage()); }
        roles.set(Set.of("AGENT", "SUPERVISOR"));
        System.out.println(admin.refund("FC-4001", 50_000));
        try { admin.orderSummary(" "); } catch (IllegalArgumentException e) { System.out.println("real exception type kept: " + e.getMessage()); }
        System.out.println(admin + ", equals itself: " + admin.equals(admin));
        System.out.println("metrics: " + metrics);
        try { enhance(RealOrderAdmin.class, real, Set::of, metrics); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
