import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * DairyDay's modular monolith. Modules (subscriptions, billing, delivery) expose an API and keep internals private;
 * cross-module reactions use events published only after the business transaction commits; an architecture check
 * (a fitness function, like Spring Modulith's verify()) rejects undeclared dependencies, access to internals and cycles.
 */
public class ModularMonolithFitnessDemo {

    // ---------- Module metadata and the fitness function ----------
    record Module(String name, Set<String> allowedDependencies) {}
    record Access(String fromModule, String toModule, String toType) {}                   // what static analysis would extract

    static List<String> verify(List<Module> modules, List<Access> accesses) {             // (1)!
        Map<String, Module> byName = new LinkedHashMap<>();
        modules.forEach(m -> byName.put(m.name(), m));
        List<String> problems = new ArrayList<>();
        for (Access a : accesses) {
            if (a.fromModule().equals(a.toModule())) continue;
            if (!byName.get(a.fromModule()).allowedDependencies().contains(a.toModule()))
                problems.add(a.fromModule() + " -> " + a.toModule() + " is not a declared dependency");
            if (a.toType().contains(".internal."))
                problems.add(a.fromModule() + " uses internal type " + a.toType());
        }
        problems.addAll(cycles(modules));
        return problems;
    }

    static List<String> cycles(List<Module> moduleList) {                                  // (2)!
        Map<String, Module> modules = new LinkedHashMap<>();
        moduleList.forEach(m -> modules.put(m.name(), m));
        List<String> found = new ArrayList<>();
        for (String start : modules.keySet()) {
            Deque<List<String>> paths = new ArrayDeque<>();
            paths.push(List.of(start));
            while (!paths.isEmpty()) {
                List<String> path = paths.pop();
                for (String next : modules.get(path.get(path.size() - 1)).allowedDependencies()) {
                    if (next.equals(start)) {
                        List<String> cycle = new ArrayList<>(path); cycle.add(start);
                        String canonical = String.join(" -> ", cycle);
                        if (start.equals(path.stream().min(String::compareTo).orElseThrow())) found.add("cycle: " + canonical);
                    } else if (!path.contains(next) && modules.containsKey(next)) {
                        List<String> longer = new ArrayList<>(path); longer.add(next); paths.push(longer);
                    }
                }
            }
        }
        return found.stream().distinct().toList();
    }

    // ---------- Runtime: transactional event publication ----------
    static final class UnitOfWork {                                                         // (3)!
        private final List<Object> pending = new ArrayList<>();
        private final EventBus bus;
        UnitOfWork(EventBus bus) { this.bus = bus; }
        void register(Object event) { pending.add(event); }
        void commit() { List<Object> toSend = List.copyOf(pending); pending.clear(); toSend.forEach(bus::publish); }
        void rollback() { pending.clear(); }
    }
    static final class EventBus {
        private final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();
        <E> void on(Class<E> t, Consumer<E> h) { handlers.computeIfAbsent(t, k -> new ArrayList<>()).add(o -> h.accept(t.cast(o))); }
        void publish(Object e) { handlers.getOrDefault(e.getClass(), List.of()).forEach(h -> h.accept(e)); }
    }

    // ---------- Modules ----------
    record SubscriptionStarted(String customer, int litresPerDay, long pricePerLitrePaise) {}   // published API of "subscriptions"

    static final class Subscriptions {
        private final Map<String, Integer> active = new TreeMap<>();
        private final EventBus bus;
        Subscriptions(EventBus bus) { this.bus = bus; }
        String start(String customer, int litres, boolean failAfterSave) {
            UnitOfWork uow = new UnitOfWork(bus);
            try {
                if (litres < 1 || litres > 5) throw new IllegalArgumentException("1..5 litres per day");
                active.put(customer, litres);
                uow.register(new SubscriptionStarted(customer, litres, 6_800));                  // (4)!
                if (failAfterSave) throw new IllegalStateException("database write failed");
                uow.commit();
                return "started " + customer;
            } catch (RuntimeException e) {
                active.remove(customer);
                uow.rollback();
                return "failed " + customer + ": " + e.getMessage();
            }
        }
    }
    static final class Billing {
        final Map<String, Long> monthlyPaise = new TreeMap<>();
        Billing(EventBus bus) { bus.on(SubscriptionStarted.class, e -> monthlyPaise.put(e.customer(), e.litresPerDay() * e.pricePerLitrePaise() * 30)); }
    }
    static final class Delivery {
        final Set<String> route = new LinkedHashSet<>();
        Delivery(EventBus bus) { bus.on(SubscriptionStarted.class, e -> route.add(e.customer())); }       // (5)!
    }

    public static void main(String[] args) {
        // 1. Architecture check, run in CI.
        List<Module> declared = List.of(
                new Module("subscriptions", Set.of()),
                new Module("billing", Set.of("subscriptions")),
                new Module("delivery", Set.of("subscriptions")));
        List<Access> clean = List.of(new Access("billing", "subscriptions", "dairyday.subscriptions.SubscriptionStarted"),
                new Access("delivery", "subscriptions", "dairyday.subscriptions.SubscriptionStarted"));
        System.out.println("check, clean build: " + verify(declared, clean));

        List<Access> sneaky = new ArrayList<>(clean);
        sneaky.add(new Access("delivery", "subscriptions", "dairyday.subscriptions.internal.SubscriptionRow"));
        sneaky.add(new Access("delivery", "billing", "dairyday.billing.Invoice"));
        System.out.println("check, sneaky build: " + verify(declared, sneaky));

        List<Module> cyclic = List.of(new Module("subscriptions", Set.of("billing")), new Module("billing", Set.of("subscriptions")),
                new Module("delivery", Set.of("subscriptions")));
        System.out.println("check, cyclic modules: " + cycles(cyclic));

        // 2. Runtime behaviour.
        EventBus bus = new EventBus();
        Subscriptions subscriptions = new Subscriptions(bus);
        Billing billing = new Billing(bus);
        Delivery delivery = new Delivery(bus);
        System.out.println(subscriptions.start("asha", 2, false));
        System.out.println(subscriptions.start("ravi", 1, true));
        System.out.println(subscriptions.start("meera", 9, false));
        System.out.println("billing: " + billing.monthlyPaise + ", delivery route: " + delivery.route);
    }
}
