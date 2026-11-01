import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Idempotent consumer: brokers deliver at least once, so the same message can arrive twice. Record processed
 * message IDs in the same transaction as the side effect, and skip duplicates.
 */
public class IdempotentConsumerDemo {

    record Message(String id, String customerId, int points) {}

    static final class NaiveConsumer {
        int points;
        void handle(Message m) { points += m.points(); }
    }

    static final class IdempotentConsumer {
        int points; final Set<String> processed = new HashSet<>();                              // (1)!
        String handle(Message m) {
            if (!processed.add(m.id())) return "skip duplicate " + m.id();                       // (2)!
            points += m.points();                                                                // (3)!
            return "credited " + m.points() + " for " + m.id();
        }
    }

    public static void main(String[] args) {
        Message earn = new Message("msg-81", "C-1", 24);
        Message earn2 = new Message("msg-82", "C-1", 13);
        List<Message> delivered = List.of(earn, earn2, earn);   // msg-81 redelivered after a consumer restart

        NaiveConsumer naive = new NaiveConsumer();
        delivered.forEach(naive::handle);
        System.out.println("naive points: " + naive.points + " (should be 37)");

        IdempotentConsumer safe = new IdempotentConsumer();
        delivered.forEach(m -> System.out.println("  " + safe.handle(m)));
        System.out.println("idempotent points: " + safe.points);
    }
}
