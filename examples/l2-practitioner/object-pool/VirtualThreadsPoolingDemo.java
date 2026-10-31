import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** With virtual threads, don't pool THREADS (they're cheap); limit access to the scarce RESOURCE instead. */
public class VirtualThreadsPoolingDemo {

    static final Semaphore DB_CONNECTIONS = new Semaphore(10);                            // (1)!
    static final AtomicInteger inUse = new AtomicInteger(), maxSeen = new AtomicInteger();

    static int fetchStock(int i) throws InterruptedException {
        DB_CONNECTIONS.acquire();                                                         // (2)!
        try {
            int now = inUse.incrementAndGet();
            maxSeen.accumulateAndGet(now, Math::max);
            Thread.sleep(5);                                                               // simulated query
            return i % 7;
        } finally {
            inUse.decrementAndGet();
            DB_CONNECTIONS.release();
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger total = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {               // (3)!
            for (int i = 0; i < 2_000; i++) {
                int n = i;
                executor.submit(() -> { total.addAndGet(fetchStock(n)); return null; });
            }
        }                                                                                  // waits for all tasks
        System.out.println("2000 requests on 2000 virtual threads, total stock " + total.get());
        System.out.println("max concurrent DB calls within the limit of 10: " + (maxSeen.get() <= 10));
    }
}
