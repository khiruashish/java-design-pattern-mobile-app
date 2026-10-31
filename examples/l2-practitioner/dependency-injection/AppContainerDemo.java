import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Production-grade composition root in plain Java (what a DI container gives you, made explicit):
 * - bindings are factories (no reflection), registered by type, with SINGLETON or PER_REQUEST scope
 * - the whole graph is VALIDATED at startup: all missing bindings reported at once, cycles detected
 * - singletons are created eagerly in dependency order and closed in reverse order at shutdown
 * - tests override individual bindings without touching production wiring
 */
public class AppContainerDemo {

    enum Scope { SINGLETON, PER_REQUEST }

    interface Resolver { <T> T get(Class<T> type); }

    static final class Container implements AutoCloseable {
        private record Binding(Function<Resolver, ?> factory, Scope scope, List<Class<?>> deps) { }
        private final Map<Class<?>, Binding> bindings = new LinkedHashMap<>();
        private final Map<Class<?>, Object> singletons = new LinkedHashMap<>();           // insertion = creation order
        final List<String> log = new ArrayList<>();

        <T> Container bind(Class<T> type, Scope scope, List<Class<?>> deps, Function<Resolver, T> factory) {   // (1)!
            bindings.put(type, new Binding(factory, scope, deps));
            return this;
        }

        Container start() {
            List<String> problems = new ArrayList<>();
            bindings.forEach((type, b) -> b.deps().forEach(d -> {
                if (!bindings.containsKey(d)) problems.add(type.getSimpleName() + " needs " + d.getSimpleName() + " but nothing provides it");
            }));
            bindings.keySet().forEach(t -> findCycle(t, new ArrayList<>(), problems));    // (2)!
            if (!problems.isEmpty()) throw new IllegalStateException("container invalid:\n  - " + String.join("\n  - ", new TreeSet<>(problems)));
            bindings.forEach((type, b) -> { if (b.scope() == Scope.SINGLETON) resolve(type); });   // (3)!
            return this;
        }

        private void findCycle(Class<?> t, List<Class<?>> path, List<String> problems) {
            if (path.contains(t)) { problems.add("cycle: " + String.join(" -> ", path.subList(path.indexOf(t), path.size()).stream().map(Class::getSimpleName).toList()) + " -> " + t.getSimpleName()); return; }
            Binding b = bindings.get(t);
            if (b == null) return;
            path.add(t);
            b.deps().forEach(d -> findCycle(d, path, problems));
            path.remove(path.size() - 1);
        }

        <T> T resolve(Class<T> type) {
            Binding b = bindings.get(type);
            if (b.scope() == Scope.SINGLETON && singletons.containsKey(type)) return type.cast(singletons.get(type));
            Resolver r = new Resolver() { public <D> D get(Class<D> d) { return resolve(d); } };
            T instance = type.cast(b.factory().apply(r));
            if (b.scope() == Scope.SINGLETON) { singletons.put(type, instance); log.add("created " + type.getSimpleName()); }
            return instance;
        }

        @Override public void close() {                                                    // (4)!
            List<Class<?>> order = new ArrayList<>(singletons.keySet());
            for (int i = order.size() - 1; i >= 0; i--) {
                if (singletons.get(order.get(i)) instanceof AutoCloseable c) {
                    try { c.close(); log.add("closed " + order.get(i).getSimpleName()); } catch (Exception e) { log.add("close failed " + order.get(i).getSimpleName()); }
                }
            }
        }
    }

    // ===================== Application classes: plain constructors, no container imports =====================
    static final class DbPool implements AutoCloseable { public void close() { } }
    static final class OrderRepository { final DbPool db; OrderRepository(DbPool db) { this.db = db; } }
    interface Payments { String charge(long c); }
    static final class LivePayments implements Payments, AutoCloseable { public String charge(long c) { return "LIVE " + c; } public void close() { } }
    static final class RequestInfo { static int seq; final int id = ++seq; }
    static final class CheckoutService {
        final OrderRepository orders; final Payments payments;
        CheckoutService(OrderRepository o, Payments p) { orders = o; payments = p; }
    }
    static final class CheckoutHandler {
        final CheckoutService service; final RequestInfo request;
        CheckoutHandler(CheckoutService s, RequestInfo r) { service = s; request = r; }
        String handle(long c) { return "request #" + request.id + ": " + service.payments.charge(c); }
    }

    static Container production() {                                                       // (5)!
        return new Container()
                .bind(DbPool.class, Scope.SINGLETON, List.of(), r -> new DbPool())
                .bind(OrderRepository.class, Scope.SINGLETON, List.of(DbPool.class), r -> new OrderRepository(r.get(DbPool.class)))
                .bind(Payments.class, Scope.SINGLETON, List.of(), r -> new LivePayments())
                .bind(CheckoutService.class, Scope.SINGLETON, List.of(OrderRepository.class, Payments.class),
                        r -> new CheckoutService(r.get(OrderRepository.class), r.get(Payments.class)))
                .bind(RequestInfo.class, Scope.PER_REQUEST, List.of(), r -> new RequestInfo())
                .bind(CheckoutHandler.class, Scope.PER_REQUEST, List.of(CheckoutService.class, RequestInfo.class),
                        r -> new CheckoutHandler(r.get(CheckoutService.class), r.get(RequestInfo.class)));
    }

    public static void main(String[] args) {
        Container app = production().start();
        try (app) {
            System.out.println(app.resolve(CheckoutHandler.class).handle(49_950));
            System.out.println(app.resolve(CheckoutHandler.class).handle(12_000));
            System.out.println("one CheckoutService for all requests: " + (app.resolve(CheckoutHandler.class).service == app.resolve(CheckoutHandler.class).service));
        }
        System.out.println(app.log);

        try (Container test = production().bind(Payments.class, Scope.SINGLETON, List.of(), r -> (Payments) c -> "FAKE " + c).start()) {
            System.out.println("test override: " + test.resolve(CheckoutHandler.class).handle(100));
        }

        try {
            new Container()
                    .bind(CheckoutService.class, Scope.SINGLETON, List.of(OrderRepository.class, Payments.class), r -> null)
                    .bind(OrderRepository.class, Scope.SINGLETON, List.of(CheckoutService.class), r -> null)
                    .start();
        } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
    }
}
