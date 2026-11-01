import java.util.ArrayList;
import java.util.List;

/**
 * Dead letter channel: a message that keeps failing is moved aside, with the reason and attempt count, so it
 * doesn't block the queue or get lost. After a fix, dead letters are redriven.
 */
public class DeadLetterChannelDemo {

    record Message(String id, String body, int attempts) {}
    record DeadLetter(Message message, String reason) {}

    static final int MAX_ATTEMPTS = 3;
    static boolean fixedParser = false;

    static void process(Message m) {
        if (m.body().contains("qty=-") && !fixedParser) throw new IllegalArgumentException("negative quantity");
        if (m.body().contains("qty=-")) return;   // fixed parser treats negative quantity as a return
    }

    static void consume(List<Message> queue, List<DeadLetter> dlq, List<String> log) {
        while (!queue.isEmpty()) {
            Message m = queue.remove(0);
            try {
                process(m);
                log.add("processed " + m.id() + " after " + (m.attempts() + 1) + " attempt(s)");
            } catch (RuntimeException e) {
                Message retried = new Message(m.id(), m.body(), m.attempts() + 1);
                if (retried.attempts() < MAX_ATTEMPTS) queue.add(retried);                          // (1)!
                else { dlq.add(new DeadLetter(retried, e.getMessage())); log.add("dead-lettered " + m.id() + ": " + e.getMessage()); }   // (2)!
            }
        }
    }

    public static void main(String[] args) {
        List<Message> queue = new ArrayList<>(List.of(
                new Message("m1", "sku=MILK-1L qty=2", 0), new Message("m2", "sku=CURD-500G qty=-1", 0), new Message("m3", "sku=EGGS-12 qty=1", 0)));
        List<DeadLetter> dlq = new ArrayList<>();
        List<String> log = new ArrayList<>();
        consume(queue, dlq, log);
        log.forEach(System.out::println);
        dlq.forEach(d -> System.out.println("DLQ: " + d.message().id() + " attempts=" + d.message().attempts() + " reason=" + d.reason()));

        fixedParser = true;                                                                      // (3)!
        log.clear();
        dlq.forEach(d -> queue.add(new Message(d.message().id(), d.message().body(), 0)));
        dlq.clear();
        consume(queue, dlq, log);
        System.out.println("after fix and redrive: " + log + ", DLQ size " + dlq.size());
    }
}
