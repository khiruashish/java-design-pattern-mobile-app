import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Production-grade in-process mediator (the idea behind MediatR / Spring's ApplicationEventPublisher):
 * - REQUESTS have exactly one handler (validated at startup: none missing, no duplicates)
 * - NOTIFICATIONS have zero or more handlers; one failing handler doesn't stop the others, failures are reported
 * - pipeline behaviours (logging, validation) wrap every request, so handlers stay focused
 * - callers depend only on the Mediator interface: no handler imports anywhere in controllers
 */
public class InProcessMediatorDemo {

    interface Request<R> { }
    interface Notification { }
    interface RequestHandler<Q extends Request<R>, R> { R handle(Q request); }
    interface Behaviour { Object around(Request<?> request, Function<Request<?>, Object> next); }

    record PlaceOrder(String customer, List<String> skus) implements Request<String> { }
    record GetOrderStatus(String orderId) implements Request<String> { }
    record OrderPlaced(String orderId, String customer) implements Notification { }

    static final class Mediator {
        private final Map<Class<?>, RequestHandler<?, ?>> handlers = new HashMap<>();
        private final Map<Class<?>, List<java.util.function.Consumer<Notification>>> listeners = new HashMap<>();
        private final List<Behaviour> behaviours = new ArrayList<>();
        final List<String> failures = new ArrayList<>();

        <Q extends Request<R>, R> Mediator handle(Class<Q> type, RequestHandler<Q, R> h) {
            if (handlers.putIfAbsent(type, h) != null) throw new IllegalStateException("two handlers for " + type.getSimpleName());   // (1)!
            return this;
        }
        @SuppressWarnings("unchecked")
        <N extends Notification> Mediator on(Class<N> type, java.util.function.Consumer<N> l) {
            listeners.computeIfAbsent(type, t -> new ArrayList<>()).add(n -> l.accept((N) n));
            return this;
        }
        Mediator behaviour(Behaviour b) { behaviours.add(b); return this; }

        Mediator validate(List<Class<? extends Request<?>>> requestTypes) {                        // (2)!
            List<String> missing = requestTypes.stream().filter(t -> !handlers.containsKey(t)).map(Class::getSimpleName).toList();
            if (!missing.isEmpty()) throw new IllegalStateException("no handler for " + missing);
            return this;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        <R> R send(Request<R> request) {
            RequestHandler handler = handlers.get(request.getClass());
            if (handler == null) throw new IllegalStateException("no handler for " + request.getClass().getSimpleName());
            Function<Request<?>, Object> call = q -> handler.handle(q);
            for (int i = behaviours.size() - 1; i >= 0; i--) {                                       // (3)!
                Behaviour b = behaviours.get(i); Function<Request<?>, Object> next = call;
                call = q -> b.around(q, next);
            }
            return (R) call.apply(request);
        }

        void publish(Notification n) {
            for (var l : listeners.getOrDefault(n.getClass(), List.of())) {
                try { l.accept(n); }
                catch (RuntimeException e) { failures.add(n.getClass().getSimpleName() + ": " + e.getMessage()); }   // (4)!
            }
        }
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        Mediator[] holder = new Mediator[1];
        Mediator mediator = new Mediator()
                .behaviour((q, next) -> {
                    String name = q.getClass().getSimpleName();
                    log.add("start " + name);
                    try { Object r = next.apply(q); log.add("end " + name); return r; }
                    catch (RuntimeException e) { log.add("failed " + name); throw e; }
                })
                .behaviour((q, next) -> {
                    if (q instanceof PlaceOrder p && p.skus().isEmpty()) throw new IllegalArgumentException("empty order");
                    return next.apply(q);
                })
                .handle(PlaceOrder.class, q -> {
                    String id = "FC-9701";
                    holder[0].publish(new OrderPlaced(id, q.customer()));                            // (5)!
                    return id;
                })
                .handle(GetOrderStatus.class, q -> q.orderId() + " is being packed")
                .on(OrderPlaced.class, n -> log.add("sms to " + n.customer()))
                .on(OrderPlaced.class, n -> { throw new IllegalStateException("loyalty service down"); })
                .on(OrderPlaced.class, n -> log.add("reserve stock for " + n.orderId()))
                .validate(List.of(PlaceOrder.class, GetOrderStatus.class));
        holder[0] = mediator;

        String id = mediator.send(new PlaceOrder("asha", List.of("MILK-1L")));
        System.out.println("placed " + id + "; " + mediator.send(new GetOrderStatus(id)));
        try { mediator.send(new PlaceOrder("ravi", List.of())); } catch (IllegalArgumentException e) { System.out.println("validation behaviour: " + e.getMessage()); }
        System.out.println("log: " + log);
        System.out.println("notification failures: " + mediator.failures);

        try { new Mediator().validate(List.of(PlaceOrder.class)); } catch (IllegalStateException e) { System.out.println("startup: " + e.getMessage()); }
        try { new Mediator().handle(GetOrderStatus.class, q -> "a").handle(GetOrderStatus.class, q -> "b"); } catch (IllegalStateException e) { System.out.println("startup: " + e.getMessage()); }
    }
}
