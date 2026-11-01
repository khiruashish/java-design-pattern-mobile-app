import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-reply over messaging: the requester sends a correlation ID and a reply-to address; the replier copies the
 * correlation ID into its reply. Replies can arrive in any order, late, or not at all.
 */
public class RequestReplyDemo {

    record Request(String correlationId, String replyTo, String body) {}
    record Reply(String correlationId, String body) {}

    static final class Requester {
        private final Map<String, Long> pending = new LinkedHashMap<>();                          // (1)!
        private int seq;
        final List<String> log = new ArrayList<>();
        Request send(String body, long now) {
            String id = "corr-" + ++seq;
            pending.put(id, now);
            return new Request(id, "replies.checkout-7", body);                                   // (2)!
        }
        void onReply(Reply r) {
            if (pending.remove(r.correlationId()) == null) log.add("unknown or late reply " + r.correlationId() + " dropped");
            else log.add(r.correlationId() + " -> " + r.body());                                  // (3)!
        }
        void expire(long now, long timeoutMs) {
            pending.entrySet().removeIf(e -> {
                boolean old = now - e.getValue() > timeoutMs;
                if (old) log.add(e.getKey() + " timed out");
                return old;
            });
        }
    }

    public static void main(String[] args) {
        Requester checkout = new Requester();
        Request a = checkout.send("quote delivery fee BLR 560001", 0);
        Request b = checkout.send("quote delivery fee BLR 560102", 0);
        Request c = checkout.send("quote delivery fee PUN 411001", 0);
        System.out.println("sent " + List.of(a.correlationId(), b.correlationId(), c.correlationId()) + " with replyTo=" + a.replyTo());
        checkout.onReply(new Reply(b.correlationId(), "Rs 25"));
        checkout.onReply(new Reply(a.correlationId(), "Rs 0 (free above Rs 199)"));
        checkout.expire(5_000, 3_000);
        checkout.onReply(new Reply(c.correlationId(), "Rs 30"));
        checkout.log.forEach(System.out::println);
    }
}
