import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Production-grade state machine for orders:
 * - transitions declared as data (from, event, to, guard), validated at startup
 * - guards check business rules (e.g. can't dispatch without a rider)
 * - optimistic versioning: an update based on a stale version is rejected (two workers racing)
 * - idempotent events: re-delivering the same event id is a no-op, not an error
 * - an append-only history for audit and debugging
 */
public class OrderStateMachineDemo {

    enum S { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED }
    enum E { PACK, DISPATCH, DELIVER, CANCEL }

    record Order(String id, S state, long version, String riderId) { }                          // (1)!
    record Event(String eventId, E type, String riderId) { }
    record Transition(S from, E event, S to, Predicate<Event> guard, String guardMessage) { }
    record HistoryEntry(String orderId, S from, S to, String eventId, Instant at) { }

    static final class StateMachine {
        private final Map<S, Map<E, Transition>> table = new EnumMap<>(S.class);
        StateMachine(List<Transition> transitions) {
            for (Transition t : transitions) {
                Transition old = table.computeIfAbsent(t.from(), k -> new EnumMap<>(E.class)).put(t.event(), t);
                if (old != null) throw new IllegalArgumentException("duplicate transition " + t.from() + "+" + t.event());   // (2)!
            }
        }
        Transition find(S from, E event) {
            Transition t = table.getOrDefault(from, Map.of()).get(event);
            if (t == null) throw new IllegalStateException(event + " not allowed in " + from);
            return t;
        }
    }

    static final class OrderStore {                                                            // simulates a table with a version column
        final Map<String, Order> rows = new java.util.HashMap<>();
        final Set<String> processedEvents = new HashSet<>();
        final List<HistoryEntry> history = new ArrayList<>();

        synchronized void save(Order updated, long expectedVersion, HistoryEntry h) {
            Order current = rows.get(updated.id());
            if (current.version() != expectedVersion)                                            // (3)!
                throw new IllegalStateException("stale update for " + updated.id() + ": expected v" + expectedVersion + ", found v" + current.version());
            rows.put(updated.id(), updated);
            history.add(h);
        }
    }

    static final class OrderService {
        private final StateMachine machine; private final OrderStore store; private final java.util.function.Supplier<Instant> clock;
        OrderService(StateMachine m, OrderStore s, java.util.function.Supplier<Instant> clock) { machine = m; store = s; this.clock = clock; }

        Order handle(String orderId, Event event, Order readEarlier) {
            if (store.processedEvents.contains(event.eventId())) return store.rows.get(orderId);  // (4)!
            Transition t = machine.find(readEarlier.state(), event.type());
            if (!t.guard().test(event)) throw new IllegalStateException(t.guardMessage());
            Order next = new Order(orderId, t.to(), readEarlier.version() + 1,
                    event.riderId() != null ? event.riderId() : readEarlier.riderId());
            store.save(next, readEarlier.version(), new HistoryEntry(orderId, t.from(), t.to(), event.eventId(), clock.get()));
            store.processedEvents.add(event.eventId());
            return next;
        }
    }

    static StateMachine orderMachine() {
        Predicate<Event> always = e -> true;
        return new StateMachine(List.of(
                new Transition(S.PLACED, E.PACK, S.PACKED, always, ""),
                new Transition(S.PLACED, E.CANCEL, S.CANCELLED, always, ""),
                new Transition(S.PACKED, E.DISPATCH, S.OUT_FOR_DELIVERY, e -> e.riderId() != null, "dispatch needs a rider"),   // (5)!
                new Transition(S.PACKED, E.CANCEL, S.CANCELLED, always, ""),
                new Transition(S.OUT_FOR_DELIVERY, E.DELIVER, S.DELIVERED, always, "")));
    }

    public static void main(String[] args) {
        OrderStore store = new OrderStore();
        store.rows.put("FC-9101", new Order("FC-9101", S.PLACED, 1, null));
        Instant[] now = {Instant.parse("2026-11-02T10:00:00Z")};
        OrderService svc = new OrderService(orderMachine(), store, () -> now[0] = now[0].plusSeconds(60));

        Order o = svc.handle("FC-9101", new Event("ev-1", E.PACK, null), store.rows.get("FC-9101"));
        System.out.println("after PACK: " + o);
        try { svc.handle("FC-9101", new Event("ev-2", E.DISPATCH, null), o); } catch (IllegalStateException e) { System.out.println("guard: " + e.getMessage()); }

        Order seenByWorkerA = store.rows.get("FC-9101"), seenByWorkerB = store.rows.get("FC-9101");
        svc.handle("FC-9101", new Event("ev-3", E.DISPATCH, "R-42"), seenByWorkerA);
        try { svc.handle("FC-9101", new Event("ev-4", E.CANCEL, null), seenByWorkerB); } catch (IllegalStateException e) { System.out.println("race: " + e.getMessage()); }

        Order again = svc.handle("FC-9101", new Event("ev-3", E.DISPATCH, "R-42"), seenByWorkerA);
        System.out.println("duplicate ev-3 ignored, still " + again.state() + " v" + again.version());
        try { svc.handle("FC-9101", new Event("ev-5", E.PACK, null), again); } catch (IllegalStateException e) { System.out.println("illegal: " + e.getMessage()); }
        svc.handle("FC-9101", new Event("ev-6", E.DELIVER, null), store.rows.get("FC-9101"));
        store.history.forEach(h -> System.out.println("  history: " + h.from() + " -> " + h.to() + " by " + h.eventId() + " at " + h.at()));
    }
}
