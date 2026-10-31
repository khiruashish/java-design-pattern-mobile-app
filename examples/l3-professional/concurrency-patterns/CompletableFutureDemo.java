import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Future and CompletableFuture: start independent calls together, combine results, and handle timeouts and failures as values. */
public class CompletableFutureDemo {

    static long priceService(String sku) { sleep(150); return 6_400; }
    static int stockService(String sku) { sleep(150); return 42; }
    static int etaService(String pincode) { sleep(800); return 25; }                                       // the slow one
    static String reviewsService(String sku) { throw new IllegalStateException("reviews service down"); }

    static void sleep(long ms) { try { TimeUnit.MILLISECONDS.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    record ProductPage(long pricePaise, int stock, int etaMinutes, String reviews) { }

    public static void main(String[] args) {
        try (ExecutorService io = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.nanoTime();
            CompletableFuture<Long> price = CompletableFuture.supplyAsync(() -> priceService("MILK"), io);   // (1)!
            CompletableFuture<Integer> stock = CompletableFuture.supplyAsync(() -> stockService("MILK"), io);
            CompletableFuture<Integer> eta = CompletableFuture.supplyAsync(() -> etaService("411001"), io)
                    .completeOnTimeout(45, 300, TimeUnit.MILLISECONDS);                                   // (2)!
            CompletableFuture<String> reviews = CompletableFuture.supplyAsync(() -> reviewsService("MILK"), io)
                    .exceptionally(e -> "reviews unavailable");                                           // (3)!

            ProductPage page = price.thenCombine(stock, (p, s) -> new long[] {p, s})
                    .thenCombine(eta, (ps, e) -> new ProductPage(ps[0], (int) ps[1], e, null))
                    .thenCombine(reviews, (pg, r) -> new ProductPage(pg.pricePaise(), pg.stock(), pg.etaMinutes(), r))
                    .join();
            long ms = (System.nanoTime() - start) / 1_000_000;
            System.out.println(page);
            System.out.println("took under 600 ms (sequential would be 1,100+): " + (ms < 600));
        }
    }
}
