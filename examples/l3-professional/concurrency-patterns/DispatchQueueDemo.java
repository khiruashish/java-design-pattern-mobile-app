import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Realistic: FreshCart's packing station. Checkout threads hand orders to a bounded queue; a pool of packers drains it.
 * When the queue is full for too long, checkout gets a clear "busy" answer instead of piling up threads.
 * Shutdown lets packers finish everything already queued.
 */
public class DispatchQueueDemo {

    record Order(String id, String zone, int items) { }
    sealed interface Msg permits Work, Stop { }
    record Work(Order order) implements Msg { }
    record Stop() implements Msg { }

    static final class PackingStation implements AutoCloseable {
        private final BlockingQueue<Msg> queue;
        private final ExecutorService packers;
        private final int packerCount;
        final Map<String, AtomicInteger> packedByZone = new ConcurrentHashMap<>();                     // (1)!
        final AtomicInteger rejected = new AtomicInteger();

        PackingStation(int capacity, int packerCount, long msPerOrder) {
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.packerCount = packerCount;
            this.packers = Executors.newFixedThreadPool(packerCount);
            for (int i = 0; i < packerCount; i++) packers.submit(() -> {
                while (true) {
                    Msg m = queue.take();
                    if (m instanceof Work(Order o)) {
                        TimeUnit.MILLISECONDS.sleep(msPerOrder);
                        packedByZone.computeIfAbsent(o.zone(), z -> new AtomicInteger()).incrementAndGet();
                    } else return null;
                }
            });
        }

        boolean submit(Order o) throws InterruptedException {                                            // (2)!
            boolean accepted = queue.offer(new Work(o), 1, TimeUnit.MILLISECONDS);
            if (!accepted) rejected.incrementAndGet();
            return accepted;
        }

        @Override public void close() throws Exception {                                               // (3)!
            for (int i = 0; i < packerCount; i++) queue.put(new Stop());
            packers.shutdown();
            if (!packers.awaitTermination(10, TimeUnit.SECONDS)) throw new IllegalStateException("packers did not finish");
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> zones = List.of("Aundh", "Baner", "Kothrud");
        int accepted = 0;
        PackingStation station = new PackingStation(5, 2, 30);
        try (station) {
            for (int i = 1; i <= 60; i++) if (station.submit(new Order("FC-" + i, zones.get(i % 3), 1 + i % 5))) accepted++;
        }
        Map<String, Integer> packed = new TreeMap<>();
        station.packedByZone.forEach((z, n) -> packed.put(z, n.get()));
        System.out.println("60 orders offered: accepted + rejected = " + (accepted + station.rejected.get())
                + ", some turned away while the queue was full: " + (station.rejected.get() > 0));
        System.out.println("every accepted order was packed before shutdown: " + (accepted == packed.values().stream().mapToInt(Integer::intValue).sum()));
    }
}
