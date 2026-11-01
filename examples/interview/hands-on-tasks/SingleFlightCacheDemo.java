import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Task 5 solution: a thread-safe price cache with single-flight loading. Concurrent misses for the same key share one
 * in-flight load (a CompletableFuture stored with computeIfAbsent); failed loads are removed so the next caller retries.
 */
public class SingleFlightCacheDemo {

    static final class SingleFlightCache<K, V> {
        private final ConcurrentHashMap<K, CompletableFuture<V>> entries = new ConcurrentHashMap<>();
        private final Function<K, V> loader;
        SingleFlightCache(Function<K, V> loader) { this.loader = loader; }

        V get(K key) {
            CompletableFuture<V> created = new CompletableFuture<>();
            CompletableFuture<V> existing = entries.putIfAbsent(key, created);
            if (existing != null) return existing.join();          // someone else is loading or has loaded
            try {
                V value = loader.apply(key);                         // load outside any map lock
                created.complete(value);
                return value;
            } catch (RuntimeException e) {
                entries.remove(key, created);                        // let the next caller retry
                created.completeExceptionally(e);
                throw e;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        SingleFlightCache<String, Long> cache = new SingleFlightCache<>(sku -> {
            loads.incrementAndGet();
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return 6800L;
        });

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 50; i++) results.add(pool.submit(() -> { start.await(); return cache.get("MILK-1L"); }));
            start.countDown();
            Set<Long> values = new java.util.TreeSet<>();
            for (Future<Long> f : results) values.add(f.get());
            System.out.println("50 concurrent requests for MILK-1L: loader called " + loads.get() + " time(s), values " + values);
        }

        AtomicInteger attempts = new AtomicInteger();
        SingleFlightCache<String, Long> flaky = new SingleFlightCache<>(sku -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("pricing DB timeout");
            return 4500L;
        });
        try { flaky.get("CURD-500G"); } catch (IllegalStateException e) { System.out.println("first load failed: " + e.getMessage()); }
        System.out.println("second call retries and gets " + flaky.get("CURD-500G") + " paise (loader attempts " + attempts.get() + ")");
    }
}
