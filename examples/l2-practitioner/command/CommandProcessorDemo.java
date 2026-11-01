import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-grade Command processing (the shape behind job queues and outboxes):
 * - commands are immutable data (sealed records) with an idempotency key, so they can be stored and replayed
 * - one handler per command type, chosen by an exhaustive switch
 * - retries with exponential backoff for transient errors; permanent errors go straight to a dead-letter list
 * - duplicates (same key) are executed once; every attempt is written to an audit log
 */
public class CommandProcessorDemo {

    sealed interface Cmd permits RefundPayment, SendSms, RestockSku {                        // (1)!
        String key();
    }
    record RefundPayment(String key, String orderId, long paise) implements Cmd { }
    record SendSms(String key, String phone, String text) implements Cmd { }
    record RestockSku(String key, String sku, int qty) implements Cmd { }

    static final class TransientException extends RuntimeException { TransientException(String m) { super(m); } }

    static final class Processor {
        final Map<String, String> completed = new LinkedHashMap<>();                            // key -> result (idempotency store)
        final List<String> audit = new ArrayList<>(), deadLetters = new ArrayList<>();
        final List<Duration> sleeps = new ArrayList<>();                                        // recorded instead of sleeping
        private final int maxAttempts;
        int payFailuresLeft;                                                                    // simulates a flaky payment provider

        Processor(int maxAttempts, int payFailures) { this.maxAttempts = maxAttempts; this.payFailuresLeft = payFailures; }

        String submit(Cmd cmd) {
            if (completed.containsKey(cmd.key())) { audit.add(cmd.key() + " duplicate"); return completed.get(cmd.key()); }   // (2)!
            for (int attempt = 1; ; attempt++) {
                try {
                    String result = handle(cmd);
                    completed.put(cmd.key(), result);
                    audit.add(cmd.key() + " ok on attempt " + attempt);
                    return result;
                } catch (TransientException e) {
                    audit.add(cmd.key() + " attempt " + attempt + " failed: " + e.getMessage());
                    if (attempt == maxAttempts) { deadLetters.add(cmd.key()); return "dead-lettered"; }
                    sleeps.add(Duration.ofMillis(100L << (attempt - 1)));                       // (3)!
                } catch (IllegalArgumentException e) {
                    audit.add(cmd.key() + " rejected: " + e.getMessage());
                    deadLetters.add(cmd.key());                                                 // (4)!
                    return "rejected";
                }
            }
        }

        private String handle(Cmd cmd) {
            return switch (cmd) {                                                               // (5)!
                case RefundPayment r -> {
                    if (r.paise() <= 0) throw new IllegalArgumentException("refund must be positive");
                    if (payFailuresLeft-- > 0) throw new TransientException("PayLink timeout");
                    yield "refunded " + r.paise() / 100 + " on " + r.orderId();
                }
                case SendSms s -> "sms to " + s.phone();
                case RestockSku r -> "restocked " + r.qty() + " x " + r.sku();
            };
        }
    }

    public static void main(String[] args) {
        Processor p = new Processor(4, 2);
        System.out.println(p.submit(new RefundPayment("refund-FC-7201", "FC-7201", 64_900)));
        System.out.println(p.submit(new RefundPayment("refund-FC-7201", "FC-7201", 64_900)));
        System.out.println(p.submit(new SendSms("sms-FC-7201", "+919800000001", "Refund done")));
        System.out.println(p.submit(new RefundPayment("refund-FC-7202", "FC-7202", 0)));
        System.out.println("backoff waits: " + p.sleeps);
        System.out.println("dead letters: " + p.deadLetters);
        p.audit.forEach(a -> System.out.println("  audit: " + a));

        Processor down = new Processor(3, 99);
        System.out.println("provider down: " + down.submit(new RefundPayment("refund-FC-7203", "FC-7203", 1_000)) + ", waits " + down.sleeps);
    }
}
