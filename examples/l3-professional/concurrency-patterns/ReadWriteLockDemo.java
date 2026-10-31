import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/** Read-write lock: many readers at once, one writer exclusively. StampedLock adds lock-free optimistic reads. */
public class ReadWriteLockDemo {

    static final class CatalogCache {
        private final Map<String, Long> prices = new HashMap<>();
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final AtomicInteger maxConcurrentReaders = new AtomicInteger();

        Long price(String sku) {
            lock.readLock().lock();                                                                     // (1)!
            try {
                maxConcurrentReaders.accumulateAndGet(lock.getReadLockCount(), Math::max);
                Thread.sleep(5);
                return prices.get(sku);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
            finally { lock.readLock().unlock(); }
        }
        void reprice(Map<String, Long> update) {
            lock.writeLock().lock();                                                                    // (2)!
            try { prices.putAll(update); } finally { lock.writeLock().unlock(); }
        }
    }

    static final class RiderLocation {
        private final StampedLock lock = new StampedLock();
        private double lat, lon;
        void move(double la, double lo) { long s = lock.writeLock(); try { lat = la; lon = lo; } finally { lock.unlockWrite(s); } }
        double[] read() {
            long stamp = lock.tryOptimisticRead();                                                       // (3)!
            double la = lat, lo = lon;
            if (!lock.validate(stamp)) {
                stamp = lock.readLock();
                try { la = lat; lo = lon; } finally { lock.unlockRead(stamp); }
            }
            return new double[] {la, lo};
        }
    }

    public static void main(String[] args) {
        CatalogCache cache = new CatalogCache();
        cache.reprice(Map.of("MILK", 6_400L, "BREAD", 4_500L));
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 40; i++) pool.submit(() -> cache.price("MILK"));
        }
        System.out.println("readers overlapped: " + (cache.maxConcurrentReaders.get() > 1) + ", MILK = " + cache.price("MILK"));
        cache.reprice(Map.of("MILK", 6_800L));
        System.out.println("after a write: MILK = " + cache.price("MILK"));

        RiderLocation rider = new RiderLocation();
        rider.move(18.5204, 73.8567);
        double[] at = rider.read();
        System.out.println("rider at " + at[0] + ", " + at[1] + " (optimistic read, no lock taken when uncontended)");
    }
}
