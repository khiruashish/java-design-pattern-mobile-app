import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Virtual threads (Java 21): cheap threads for blocking I/O, so "one thread per request" works at scale again.
 * Structured concurrency (StructuredTaskScope) is still a preview API in Java 21-24; the try-with-resources
 * executor below gives the same "subtasks can't outlive their parent" shape with final APIs.
 */
public class VirtualThreadsDemo {

    static String callPartner(int i) throws InterruptedException { Thread.sleep(Duration.ofMillis(200)); return "ok-" + i; }

    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        List<Future<String>> results = new ArrayList<>();
        try (ExecutorService perTask = Executors.newVirtualThreadPerTaskExecutor()) {                  // (1)!
            for (int i = 0; i < 10_000; i++) { int n = i; results.add(perTask.submit(() -> callPartner(n))); }
        }                                                                                              // waits for all tasks
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("10,000 blocking calls of 200 ms on virtual threads: all done = "
                + results.stream().allMatch(Future::isDone) + ", under 3 s = " + (ms < 3_000));

        System.out.println("is virtual: " + Thread.ofVirtual().unstarted(() -> { }).isVirtual()
                + ", platform default: " + Thread.ofPlatform().unstarted(() -> { }).isVirtual());

        try (ExecutorService scope = Executors.newVirtualThreadPerTaskExecutor()) {                   // (2)!
            Future<String> stock = scope.submit(() -> callPartner(1));
            Future<String> eta = scope.submit(() -> { throw new IllegalStateException("eta service down"); });
            try {
                String etaResult = eta.get();                                                                // fails fast
                System.out.println("eta " + etaResult + ", stock " + stock.get());
            } catch (ExecutionException e) {
                stock.cancel(true);                                                                     // (3)!
                System.out.println("one subtask failed (" + e.getCause().getMessage() + "), the other was cancelled: " + stock.isCancelled());
            }
        }
    }
}
