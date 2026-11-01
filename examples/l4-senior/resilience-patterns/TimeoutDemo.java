import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Timeout: never wait forever on a remote call. A hung dependency without a timeout holds a thread per request
 * until the pool is exhausted; with a timeout the caller gets control back and can fail or fall back.
 */
public class TimeoutDemo {

    static String hungStockService() {                                                       // (1)!
        try { Thread.sleep(2_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "12 in stock";
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        long start = System.nanoTime();

        CompletableFuture<String> noTimeout = CompletableFuture.supplyAsync(TimeoutDemo::hungStockService, pool);
        System.out.println("without timeout, done after 300 ms? " + waitBriefly(noTimeout));

        List<String> results = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            CompletableFuture<String> call = CompletableFuture.supplyAsync(TimeoutDemo::hungStockService, pool)
                    .orTimeout(200, TimeUnit.MILLISECONDS)                                    // (2)!
                    .exceptionally(e -> e instanceof TimeoutException ? "stock unknown (timed out)" : "error");   // (3)!
            results.add(call.get());
        }
        System.out.println("with 200 ms timeout: " + results);
        System.out.println("pool after timeouts: active=" + pool.getActiveCount() + " queued=" + pool.getQueue().size()
                + " (orTimeout frees the caller, not the thread)");
        System.err.printf("elapsed %d ms%n", (System.nanoTime() - start) / 1_000_000);
        pool.shutdownNow();
    }

    static boolean waitBriefly(CompletableFuture<String> f) {
        try { f.get(300, TimeUnit.MILLISECONDS); return true; } catch (Exception e) { return false; }
    }
}
