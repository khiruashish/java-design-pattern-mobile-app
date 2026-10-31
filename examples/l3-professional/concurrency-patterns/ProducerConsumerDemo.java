import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Producer-consumer: a bounded BlockingQueue decouples producers from consumers and applies back-pressure. */
public class ProducerConsumerDemo {

    record PackJob(String orderId, int items) { }
    static final PackJob POISON = new PackJob("STOP", 0);                                               // (1)!

    public static void main(String[] args) throws Exception {
        BlockingQueue<PackJob> queue = new ArrayBlockingQueue<>(5);                                       // (2)!
        AtomicInteger packed = new AtomicInteger();
        AtomicLong items = new AtomicLong();
        int consumers = 3;

        try (ExecutorService pool = Executors.newFixedThreadPool(consumers + 1)) {
            for (int c = 0; c < consumers; c++) pool.submit(() -> {
                while (true) {
                    PackJob job = queue.take();                                                         // waits when empty
                    if (job == POISON) return null;
                    TimeUnit.MILLISECONDS.sleep(2);
                    packed.incrementAndGet(); items.addAndGet(job.items());
                }
            });
            pool.submit(() -> {
                for (int i = 1; i <= 40; i++) queue.put(new PackJob("FC-" + i, 1 + i % 4));           // (3)!
                for (int c = 0; c < consumers; c++) queue.put(POISON);
                return null;
            });
        }
        System.out.println("packed " + packed.get() + " orders, " + items.get() + " items; queue never held more than 5 jobs");
        System.out.println("queue empty at the end: " + queue.isEmpty());
    }
}
