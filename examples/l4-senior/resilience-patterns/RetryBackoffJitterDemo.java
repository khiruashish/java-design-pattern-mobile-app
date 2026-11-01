import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Retry with exponential backoff and jitter. Without jitter, every client that failed together retries together
 * (a thundering herd). Full jitter spreads retries out. Only retry transient failures of idempotent operations.
 */
public class RetryBackoffJitterDemo {

    static long backoffMs(int attempt, long baseMs, long capMs) {                                // (1)!
        return Math.min(capMs, baseMs * (1L << (attempt - 1)));
    }

    static long fullJitterMs(int attempt, long baseMs, long capMs, Random random) {               // (2)!
        return (long) (random.nextDouble() * backoffMs(attempt, baseMs, capMs));
    }

    static final Set<String> RETRYABLE = Set.of("503", "timeout", "connection-reset");
    static boolean shouldRetry(String failure, boolean idempotent, int attempt, int maxAttempts) { // (3)!
        return idempotent && RETRYABLE.contains(failure) && attempt < maxAttempts;
    }

    public static void main(String[] args) {
        System.out.println("backoff schedule (base 100 ms, cap 2 s): "
                + List.of(1, 2, 3, 4, 5, 6).stream().map(a -> backoffMs(a, 100, 2000)).toList());

        int clients = 1000;
        Random random = new Random(42);
        TreeMap<Long, Integer> plain = new TreeMap<>(), jittered = new TreeMap<>();
        for (int c = 0; c < clients; c++) {
            plain.merge(backoffMs(3, 100, 2000) / 50, 1, Integer::sum);
            jittered.merge(fullJitterMs(3, 100, 2000, random) / 50, 1, Integer::sum);
        }
        System.out.println("third retry, busiest 50 ms window: no jitter " + plain.values().stream().max(Integer::compare).get()
                + " clients, full jitter " + jittered.values().stream().max(Integer::compare).get() + " clients");

        System.out.println("GET /stock 503, attempt 1 -> retry? " + shouldRetry("503", true, 1, 3));
        System.out.println("POST /payments timeout -> retry? " + shouldRetry("timeout", false, 1, 3));
        System.out.println("GET /stock 400 -> retry? " + shouldRetry("400", true, 1, 3));
        System.out.println("GET /stock 503, attempt 3 of 3 -> retry? " + shouldRetry("503", true, 3, 3));
    }
}
