import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A thread pool is an object pool of threads: fixed workers, a BOUNDED queue, and a policy for when both are full. */
public class ThreadPoolDemo {

    public static void main(String[] args) throws Exception {
        CountDownLatch release = new CountDownLatch(1), started = new CountDownLatch(2);
        AtomicInteger rejected = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.SECONDS,                                                // (1)!
                new ArrayBlockingQueue<>(3),                                              // (2)!
                (task, executor) -> rejected.incrementAndGet());                           // (3)!

        for (int i = 1; i <= 8; i++) {
            pool.execute(() -> { started.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        }
        started.await();
        System.out.println("active=" + pool.getActiveCount() + " queued=" + pool.getQueue().size() + " rejected=" + rejected.get());
        release.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("completed=" + pool.getCompletedTaskCount() + ", threads ever created=" + pool.getLargestPoolSize());   // (4)!
        System.out.println("built-in policies: " + List.of("AbortPolicy (throw)", "CallerRunsPolicy (back-pressure)", "DiscardPolicy", "DiscardOldestPolicy"));
    }
}
