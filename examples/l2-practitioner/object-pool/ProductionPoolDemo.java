import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Production-grade Object Pool for expensive clients (e.g. a printer-service or legacy SOAP client):
 * max size with fair waiting and a borrow timeout; validation on borrow; max lifetime eviction;
 * leases that can't be returned twice; leak detection for leases held too long; metrics.
 */
public class ProductionPoolDemo {

    static final class Pool<T> {
        private record Entry<T>(T obj, Instant createdAt) { }

        private final Deque<Entry<T>> idle = new ArrayDeque<>();
        private final List<Lease> active = new ArrayList<>();
        private final Semaphore permits;
        private final Supplier<T> factory; private final Predicate<T> healthy;
        private final Duration maxLifetime, leakThreshold; private final Supplier<Instant> clock;
        final Map<String, Integer> metrics = new TreeMap<>();

        Pool(int maxSize, Supplier<T> factory, Predicate<T> healthy, Duration maxLifetime, Duration leakThreshold, Supplier<Instant> clock) {
            this.permits = new Semaphore(maxSize, true); this.factory = factory; this.healthy = healthy;
            this.maxLifetime = maxLifetime; this.leakThreshold = leakThreshold; this.clock = clock;
        }

        final class Lease implements AutoCloseable {
            private final Entry<T> entry; private final Instant borrowedAt; private final String owner; private boolean closed;
            private Lease(Entry<T> e, String owner) { entry = e; borrowedAt = clock.get(); this.owner = owner; }
            T get() { if (closed) throw new IllegalStateException("lease already returned"); return entry.obj(); }   // (1)!
            @Override public void close() {
                synchronized (Pool.this) {
                    if (closed) return;                                                    // (2)!
                    closed = true;
                    active.remove(this);
                    idle.push(entry);
                }
                permits.release();
                count("returned");
            }
        }

        Lease borrow(String owner, Duration timeout) throws InterruptedException {
            if (!permits.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                count("timeouts");
                throw new IllegalStateException("no object available within " + timeout.toMillis() + "ms (active: " + activeOwners() + ")");   // (3)!
            }
            synchronized (this) {
                while (!idle.isEmpty()) {
                    Entry<T> e = idle.pop();
                    if (clock.get().isAfter(e.createdAt().plus(maxLifetime))) { count("evicted.lifetime"); continue; }   // (4)!
                    if (!healthy.test(e.obj())) { count("evicted.unhealthy"); continue; }
                    return track(new Lease(e, owner));
                }
                count("created");
                return track(new Lease(new Entry<>(factory.get(), clock.get()), owner));
            }
        }

        synchronized List<String> suspectedLeaks() {                                       // (5)!
            Instant now = clock.get();
            return active.stream().filter(l -> Duration.between(l.borrowedAt, now).compareTo(leakThreshold) > 0)
                    .map(l -> l.owner + " held " + Duration.between(l.borrowedAt, now).toSeconds() + "s").toList();
        }

        private Lease track(Lease l) { active.add(l); count("borrowed"); return l; }
        private synchronized List<String> activeOwners() { return active.stream().map(l -> l.owner).toList(); }
        private synchronized void count(String k) { metrics.merge(k, 1, Integer::sum); }
    }

    static final class PrintClient { boolean healthy = true; final int id; PrintClient(int id) { this.id = id; } }

    public static void main(String[] args) throws Exception {
        Instant[] now = {Instant.parse("2026-11-02T09:00:00Z")};
        int[] ids = {0};
        Pool<PrintClient> pool = new Pool<>(2, () -> new PrintClient(++ids[0]), c -> c.healthy,
                Duration.ofMinutes(30), Duration.ofSeconds(60), () -> now[0]);

        try (var lease = pool.borrow("packing-station-1", Duration.ofMillis(10))) { System.out.println("station 1 uses client " + lease.get().id); }

        var forgotten = pool.borrow("returns-desk", Duration.ofMillis(10));               // never closed: a leak
        var second = pool.borrow("packing-station-2", Duration.ofMillis(10));
        try { pool.borrow("packing-station-3", Duration.ofMillis(20)); } catch (IllegalStateException e) { System.out.println("station 3: " + e.getMessage()); }

        now[0] = now[0].plusSeconds(90);
        System.out.println("suspected leaks: " + pool.suspectedLeaks());

        second.get().healthy = false;
        second.close(); second.close();                                                   // double close is harmless
        try { second.get(); } catch (IllegalStateException e) { System.out.println("use after return: " + e.getMessage()); }

        try (var lease = pool.borrow("packing-station-4", Duration.ofMillis(10))) { System.out.println("station 4 uses client " + lease.get().id + " (unhealthy client 2 was discarded)"); }

        forgotten.close();
        now[0] = now[0].plus(Duration.ofMinutes(31));                                     // every idle client is now past max lifetime
        try (var lease = pool.borrow("packing-station-5", Duration.ofMillis(10))) { System.out.println("station 5 uses client " + lease.get().id + " (old clients retired)"); }
        System.out.println("metrics: " + pool.metrics);
    }
}
