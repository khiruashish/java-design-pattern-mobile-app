import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 4 solution: a delivery-booking client that retries transient failures with exponential backoff and full
 * jitter, never retries client errors, and sends the same idempotency key on every attempt so the partner books once.
 * A virtual clock and a seeded jitter source keep the output deterministic.
 */
public class RetryingPartnerClientDemo {

    static final class TransientException extends RuntimeException { TransientException(String m) { super(m); } }
    static final class ClientErrorException extends RuntimeException { ClientErrorException(String m) { super(m); } }

    /** Fake partner: fails the first N calls per key with 503, books at most once per idempotency key. */
    static final class FakeQuickDrop {
        final Map<String, String> bookings = new HashMap<>();
        final Map<String, Integer> calls = new HashMap<>();
        final int failuresBeforeSuccess;
        FakeQuickDrop(int failuresBeforeSuccess) { this.failuresBeforeSuccess = failuresBeforeSuccess; }

        String book(String idempotencyKey, String orderId) {
            int n = calls.merge(idempotencyKey, 1, Integer::sum);
            if (orderId.isBlank()) throw new ClientErrorException("400 missing order id");
            if (n <= failuresBeforeSuccess) throw new TransientException("503 busy");
            return bookings.computeIfAbsent(idempotencyKey, k -> "QD-" + orderId);
        }
    }

    static final class RetryingClient {
        final FakeQuickDrop partner;
        final int maxAttempts = 4;
        final long baseMillis = 100, capMillis = 2000;
        long virtualNow = 0;
        long seed = 42;
        final List<String> log = new ArrayList<>();

        RetryingClient(FakeQuickDrop partner) { this.partner = partner; }

        long jitter(long bound) {                       // small deterministic LCG instead of a real random source
            seed = (seed * 6364136223846793005L + 1442695040888963407L);
            return Math.floorMod(seed >>> 33, bound + 1);
        }

        String book(String orderId) {
            String key = "book-" + orderId;              // stable across attempts
            for (int attempt = 1; ; attempt++) {
                try {
                    String id = partner.book(key, orderId);
                    log.add("t=" + virtualNow + "ms attempt " + attempt + " ok " + id);
                    return id;
                } catch (TransientException e) {
                    if (attempt == maxAttempts) { log.add("t=" + virtualNow + "ms attempt " + attempt + " " + e.getMessage() + ", giving up"); throw e; }
                    long ceiling = Math.min(capMillis, baseMillis << (attempt - 1));
                    long sleep = jitter(ceiling);
                    log.add("t=" + virtualNow + "ms attempt " + attempt + " " + e.getMessage() + ", retry in " + sleep + "ms (max " + ceiling + ")");
                    virtualNow += sleep;
                } catch (ClientErrorException e) {
                    log.add("t=" + virtualNow + "ms attempt " + attempt + " " + e.getMessage() + ", not retried");
                    throw e;
                }
            }
        }
    }

    public static void main(String[] args) {
        for (int failures : new int[] {2, 5}) {
            RetryingClient client = new RetryingClient(new FakeQuickDrop(failures));
            System.out.println("partner fails " + failures + " times:");
            try { client.book("FC-1001"); } catch (RuntimeException ignored) { }
            client.log.forEach(l -> System.out.println("  " + l));
            System.out.println("  bookings at partner: " + client.partner.bookings.size());
        }
        RetryingClient client = new RetryingClient(new FakeQuickDrop(0));
        try { client.book(" "); } catch (RuntimeException ignored) { }
        System.out.println("bad request:");
        client.log.forEach(l -> System.out.println("  " + l));
    }
}
