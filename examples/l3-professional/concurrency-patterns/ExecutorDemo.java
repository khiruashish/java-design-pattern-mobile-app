import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Thread pool: a fixed set of workers and a queue, instead of a new Thread per task. Bounded queues need a rejection policy. */
public class ExecutorDemo {

    static long invoicePaise(int orderNo) { return 10_000L + orderNo * 125; }

    public static void main(String[] args) throws Exception {
        // ---------- before: one thread per task; 10,000 orders would start 10,000 platform threads ----------
        List<Thread> threads = new ArrayList<>();
        AtomicInteger done = new AtomicInteger();
        for (int i = 0; i < 20; i++) { Thread t = new Thread(() -> done.incrementAndGet()); threads.add(t); t.start(); }   // (1)!
        for (Thread t : threads) t.join();
        System.out.println("before: started " + threads.size() + " threads for " + done.get() + " tiny tasks");

        // ---------- after: a bounded pool with an explicit rejection policy ----------
        ThreadPoolExecutor pool = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());                // (2)!
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int i = 1; i <= 200; i++) { int n = i; tasks.add(() -> invoicePaise(n)); }
            List<Future<Long>> results = pool.invokeAll(tasks);                                         // (3)!
            long total = 0;
            for (Future<Long> f : results) total += f.get();
            System.out.println("after: 200 invoices on " + pool.getMaximumPoolSize() + " threads, total Rs " + total / 100
                    + ", largest pool size " + pool.getLargestPoolSize());
        } finally {
            pool.shutdown();
            System.out.println("terminated cleanly: " + pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
