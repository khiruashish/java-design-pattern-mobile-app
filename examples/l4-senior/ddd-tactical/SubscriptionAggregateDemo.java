import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * DairyDay's milk subscription as a production-style aggregate: value objects, invariants (cutoff, pause limits),
 * domain events, optimistic concurrency via a version, a factory, a repository that stores whole aggregates, and a
 * domain service for billing that needs both the subscription and the calendar.
 */
public class SubscriptionAggregateDemo {

    // ---------- Value objects ----------
    record SubscriptionId(String value) {}
    record Litres(int value) { Litres { if (value < 1 || value > 5) throw new IllegalArgumentException("1..5 litres"); } }
    record Money(long paise) { Money times(long n) { return new Money(paise * n); } }

    // ---------- Events ----------
    sealed interface Event permits Started, Skipped, Paused, Resumed {}
    record Started(SubscriptionId id, int litres) implements Event {}
    record Skipped(SubscriptionId id, LocalDate day) implements Event {}
    record Paused(SubscriptionId id, LocalDate from, LocalDate to) implements Event {}
    record Resumed(SubscriptionId id) implements Event {}

    // ---------- Aggregate root ----------
    static final class Subscription {
        static final int MAX_PAUSE_DAYS = 30;
        private final SubscriptionId id; private final String customerId;
        private Litres litres; private final Money pricePerLitre;
        private final TreeSet<LocalDate> skipped = new TreeSet<>();
        private LocalDate pausedFrom, pausedTo;
        private long version;                                                              // (1)!
        private final List<Event> events = new ArrayList<>();

        private Subscription(SubscriptionId id, String customerId, Litres litres, Money price, long version) {
            this.id = id; this.customerId = customerId; this.litres = litres; this.pricePerLitre = price; this.version = version;
        }

        void skip(LocalDate day, LocalDate today, int hourNow) {                            // (2)!
            if (!day.isAfter(today) || (day.equals(today.plusDays(1)) && hourNow >= 22))
                throw new IllegalStateException("too late to skip " + day + " (cutoff 22:00 the day before)");
            if (isPaused(day)) throw new IllegalStateException(day + " is already paused");
            if (skipped.add(day)) events.add(new Skipped(id, day));
        }
        void pause(LocalDate from, LocalDate to, LocalDate today) {
            if (!from.isAfter(today)) throw new IllegalStateException("pause must start in the future");
            if (from.datesUntil(to.plusDays(1)).count() > MAX_PAUSE_DAYS) throw new IllegalStateException("pause at most " + MAX_PAUSE_DAYS + " days");
            pausedFrom = from; pausedTo = to;
            skipped.removeIf(d -> isPaused(d));
            events.add(new Paused(id, from, to));
        }
        void resume() { if (pausedFrom == null) return; pausedFrom = null; pausedTo = null; events.add(new Resumed(id)); }

        boolean deliversOn(LocalDate day) { return !skipped.contains(day) && !isPaused(day) && day.getDayOfWeek() != DayOfWeek.SUNDAY; }
        private boolean isPaused(LocalDate d) { return pausedFrom != null && !d.isBefore(pausedFrom) && !d.isAfter(pausedTo); }
        List<Event> pullEvents() { var out = List.copyOf(events); events.clear(); return out; }
        SubscriptionId id() { return id; } long version() { return version; } Litres litres() { return litres; } Money pricePerLitre() { return pricePerLitre; }
    }

    // ---------- Factory ----------
    static final class SubscriptionFactory {                                               // (3)!
        private int seq = 1;
        Subscription start(String customerId, int litres, Money price) {
            var s = new Subscription(new SubscriptionId("SUB-" + seq++), customerId, new Litres(litres), price, 0);
            s.events.add(new Started(s.id, litres));
            return s;
        }
    }

    // ---------- Repository with optimistic concurrency ----------
    static final class ConcurrentModification extends RuntimeException {
        ConcurrentModification(SubscriptionId id) { super(id.value() + " was changed by someone else; reload and retry"); }
    }
    static final class SubscriptionRepository {                                            // (4)!
        private record Stored(String customerId, int litres, long price, TreeSet<LocalDate> skipped, LocalDate pf, LocalDate pt, long version) {}
        private final Map<String, Stored> table = new TreeMap<>();
        final List<Event> published = new ArrayList<>();

        void save(Subscription s) {
            Stored current = table.get(s.id.value());
            long expected = current == null ? 0 : current.version();
            if (s.version != expected) throw new ConcurrentModification(s.id);
            table.put(s.id.value(), new Stored(s.customerId, s.litres.value(), s.pricePerLitre.paise(), new TreeSet<>(s.skipped), s.pausedFrom, s.pausedTo, expected + 1));
            s.version = expected + 1;
            published.addAll(s.pullEvents());                                             // dispatched only after a successful save
        }
        Optional<Subscription> load(SubscriptionId id) {
            return Optional.ofNullable(table.get(id.value())).map(st -> {
                var s = new Subscription(id, st.customerId(), new Litres(st.litres()), new Money(st.price()), st.version());
                s.skipped.addAll(st.skipped()); s.pausedFrom = st.pf(); s.pausedTo = st.pt();
                return s;
            });
        }
    }

    // ---------- Domain service ----------
    static final class MonthlyBilling {                                                    // (5)!
        Money billFor(Subscription s, LocalDate monthStart) {
            long days = monthStart.datesUntil(monthStart.plusMonths(1)).filter(s::deliversOn).count();
            return s.pricePerLitre().times(s.litres().value() * days);
        }
    }

    public static void main(String[] args) {
        var repo = new SubscriptionRepository();
        var factory = new SubscriptionFactory();
        LocalDate today = LocalDate.of(2026, 11, 1);

        Subscription sub = factory.start("C-17", 2, new Money(6_800));
        repo.save(sub);

        Subscription app = repo.load(sub.id()).orElseThrow();
        app.skip(LocalDate.of(2026, 11, 3), today, 20);
        app.pause(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 24), today);
        repo.save(app);

        Subscription supportDesk = repo.load(sub.id()).orElseThrow();
        Subscription staleTab = repo.load(sub.id()).orElseThrow();
        supportDesk.resume();
        repo.save(supportDesk);
        staleTab.skip(LocalDate.of(2026, 11, 5), today, 9);
        try { repo.save(staleTab); } catch (ConcurrentModification e) { System.out.println("stale tab: " + e.getMessage()); }

        for (Runnable r : List.<Runnable>of(
                () -> app.skip(LocalDate.of(2026, 11, 2), today, 23),
                () -> app.pause(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 15), today))) {
            try { r.run(); } catch (IllegalStateException e) { System.out.println("refused: " + e.getMessage()); }
        }

        Subscription current = repo.load(sub.id()).orElseThrow();
        System.out.println("version " + current.version() + ", November bill: Rs " + new MonthlyBilling().billFor(current, LocalDate.of(2026, 11, 1)).paise() / 100);
        repo.published.forEach(e -> System.out.println("event: " + e));
    }
}
