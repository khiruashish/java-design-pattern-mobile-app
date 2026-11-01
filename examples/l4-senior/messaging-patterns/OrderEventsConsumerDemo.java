import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Production-grade: a consumer group reading a partitioned order-events topic. Keys pin each order to a partition;
 * offsets are committed only after processing; transient failures go to a retry topic, poison messages to a DLQ
 * after three attempts; processing is idempotent so redelivery after a crash is harmless.
 */
public class OrderEventsConsumerDemo {

    record Event(String id, String orderKey, String type, int attempts) {}
    static final class TransientFailure extends RuntimeException { TransientFailure(String m) { super(m); } }

    static final class Broker {
        final List<List<Event>> partitions = new ArrayList<>();
        final int[] committed;
        final List<Event> retryTopic = new ArrayList<>(), deadLetters = new ArrayList<>();
        Broker(int partitionCount) { for (int i = 0; i < partitionCount; i++) partitions.add(new ArrayList<>()); committed = new int[partitionCount]; }
        void publish(Event e) { partitions.get(Math.floorMod(e.orderKey().hashCode(), partitions.size())).add(e); }   // (1)!
    }

    static final class Member {
        final String name; final Set<Integer> assigned = new HashSet<>();
        Member(String name) { this.name = name; }
    }

    static final class OrderEventsConsumer {
        static final int MAX_ATTEMPTS = 3;
        private final Broker broker; private final Consumer<Event> handler;
        final Set<String> processedIds = new HashSet<>();
        final List<String> log = new ArrayList<>();
        OrderEventsConsumer(Broker broker, Consumer<Event> handler) { this.broker = broker; this.handler = handler; }

        void assign(List<Member> members) {                                                                   // (2)!
            members.forEach(m -> m.assigned.clear());
            for (int p = 0; p < broker.partitions.size(); p++) members.get(p % members.size()).assigned.add(p);
            log.add("assignment: " + members.stream().map(m -> m.name + "=" + m.assigned.stream().sorted().toList()).toList());
        }

        /** Processes everything after the committed offset; crashBeforeCommit simulates dying after the work. */
        void poll(Member m, boolean crashBeforeCommit) {
            for (int p : m.assigned.stream().sorted().toList()) {
                List<Event> part = broker.partitions.get(p);
                int offset = broker.committed[p];
                for (; offset < part.size(); offset++) process(m, part.get(offset));
                if (crashBeforeCommit) { log.add(m.name + " crashed before committing p" + p); return; }
                broker.committed[p] = offset;                                                                 // (3)!
            }
        }

        void drainRetryTopic(Member m) {
            List<Event> batch = new ArrayList<>(broker.retryTopic);
            broker.retryTopic.clear();
            batch.forEach(e -> process(m, e));
        }

        private void process(Member m, Event e) {
            if (processedIds.contains(e.id())) { log.add(m.name + " skip duplicate " + e.id()); return; }        // (4)!
            try {
                handler.accept(e);
                processedIds.add(e.id());
                log.add(m.name + " handled " + e.id() + " " + e.orderKey() + " " + e.type());
            } catch (TransientFailure ex) {
                Event next = new Event(e.id(), e.orderKey(), e.type(), e.attempts() + 1);
                if (next.attempts() >= MAX_ATTEMPTS) { broker.deadLetters.add(next); log.add(m.name + " dead-lettered " + e.id() + ": " + ex.getMessage()); }
                else { broker.retryTopic.add(next); log.add(m.name + " sent " + e.id() + " to retry topic (attempt " + next.attempts() + ")"); }   // (5)!
            }
        }
    }

    public static void main(String[] args) {
        Broker broker = new Broker(3);
        int[] loyaltyCalls = {0};
        OrderEventsConsumer group = new OrderEventsConsumer(broker, e -> {
            if (e.type().equals("PAID") && e.orderKey().equals("FC-2") && e.attempts() < 1) throw new TransientFailure("loyalty service 503");
            if (e.type().equals("PAID") && e.orderKey().equals("FC-3")) throw new TransientFailure("loyalty service 503");
            if (e.type().equals("PAID")) loyaltyCalls[0]++;
        });
        int n = 0;
        for (String order : List.of("FC-1", "FC-2", "FC-3"))
            for (String type : List.of("PLACED", "PAID")) broker.publish(new Event("e" + ++n, order, type, 0));

        Member a = new Member("A"), b = new Member("B");
        group.assign(List.of(a, b));
        group.poll(a, true);
        group.assign(List.of(b));
        group.poll(b, false);
        for (int round = 0; round < 3; round++) group.drainRetryTopic(b);
        group.log.forEach(System.out::println);
        System.out.println("loyalty awarded: " + loyaltyCalls[0] + ", DLQ: " + broker.deadLetters.stream().map(Event::id).toList()
                + ", committed offsets: " + java.util.Arrays.toString(broker.committed));
    }
}
