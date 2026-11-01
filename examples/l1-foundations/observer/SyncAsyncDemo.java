import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Synchronous notification shares the publisher's thread and fate; asynchronous isolates it. */
public class SyncAsyncDemo {

    record OrderPlaced(String orderId) { }

    static void slowEmail(OrderPlaced e) { sleep(300); }
    static void brokenLoyalty(OrderPlaced e) { throw new IllegalStateException("loyalty service down"); }

    // ===================== Synchronous, naive =====================
    static String placeSyncNaive(List<Consumer<OrderPlaced>> observers) {
        long start = System.nanoTime();
        try {
            observers.forEach(o -> o.accept(new OrderPlaced("FC-901")));                 // (1)!
            return "placed";
        } catch (RuntimeException ex) {
            return "checkout FAILED because an observer threw: " + ex.getMessage();
        } finally {
            System.out.println("  sync naive took >=300ms: " + (elapsedMs(start) >= 300));
        }
    }

    // ===================== Synchronous, isolated =====================
    static String placeSyncIsolated(List<Consumer<OrderPlaced>> observers, ConcurrentLinkedQueue<String> errors) {
        for (Consumer<OrderPlaced> o : observers) {
            try { o.accept(new OrderPlaced("FC-902")); }
            catch (RuntimeException ex) { errors.add("FC-902: " + ex.getMessage()); }     // (2)!
        }
        return "placed";
    }

    // ===================== Asynchronous =====================
    static String placeAsync(List<Consumer<OrderPlaced>> observers, ExecutorService pool, ConcurrentLinkedQueue<String> errors) {
        long start = System.nanoTime();
        for (Consumer<OrderPlaced> o : observers) {
            pool.submit(() -> {                                                            // (3)!
                try { o.accept(new OrderPlaced("FC-903")); }
                catch (RuntimeException ex) { errors.add("FC-903: " + ex.getMessage()); }
            });
        }
        System.out.println("  async returned in <100ms: " + (elapsedMs(start) < 100));
        return "placed";
    }

    public static void main(String[] args) throws Exception {
        List<Consumer<OrderPlaced>> observers = List.of(SyncAsyncDemo::slowEmail, SyncAsyncDemo::brokenLoyalty);
        System.out.println("sync naive -> " + placeSyncNaive(observers));
        var errors = new ConcurrentLinkedQueue<String>();
        System.out.println("sync isolated -> " + placeSyncIsolated(observers, errors));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        System.out.println("async -> " + placeAsync(observers, pool, errors));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);                                       // (4)!
        System.out.println("errors recorded: " + errors.stream().sorted().toList());
    }

    static long elapsedMs(long start) { return (System.nanoTime() - start) / 1_000_000; }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
