import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderEventsConsumerTest {

    static OrderEventsConsumerDemo.Event event(String id, String key, String type) { return new OrderEventsConsumerDemo.Event(id, key, type, 0); }

    @Test
    void sameKeyAlwaysLandsInTheSamePartition() {
        var broker = new OrderEventsConsumerDemo.Broker(4);
        for (int i = 0; i < 5; i++) broker.publish(event("e" + i, "FC-7", "T" + i));
        assertEquals(1, broker.partitions.stream().filter(p -> !p.isEmpty()).count());
    }

    @Test
    void eventsForOneOrderAreHandledInPublishOrder() {
        var broker = new OrderEventsConsumerDemo.Broker(3);
        List<String> seen = new ArrayList<>();
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> seen.add(e.orderKey() + ":" + e.type()));
        for (String t : List.of("PLACED", "PACKED", "DELIVERED")) broker.publish(event("x-" + t, "FC-1", t));
        var m = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(m));
        group.poll(m, false);
        assertEquals(List.of("FC-1:PLACED", "FC-1:PACKED", "FC-1:DELIVERED"), seen);
    }

    @Test
    void redeliveryAfterCrashDoesNotRepeatSideEffects() {
        var broker = new OrderEventsConsumerDemo.Broker(1);
        int[] effects = {0};
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> effects[0]++);
        broker.publish(event("e1", "FC-1", "PAID"));
        var a = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(a));
        group.poll(a, true);
        assertEquals(0, broker.committed[0]);
        group.poll(a, false);
        assertEquals(1, effects[0]);
        assertEquals(1, broker.committed[0]);
    }

    @Test
    void transientFailureIsRetriedThenSucceeds() {
        var broker = new OrderEventsConsumerDemo.Broker(1);
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> {
            if (e.attempts() == 0) throw new OrderEventsConsumerDemo.TransientFailure("503");
        });
        broker.publish(event("e1", "FC-1", "PAID"));
        var a = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(a));
        group.poll(a, false);
        assertEquals(1, broker.retryTopic.size());
        group.drainRetryTopic(a);
        assertTrue(group.processedIds.contains("e1"));
        assertTrue(broker.deadLetters.isEmpty());
    }

    @Test
    void persistentFailureEndsInDeadLetterAfterMaxAttempts() {
        var broker = new OrderEventsConsumerDemo.Broker(1);
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> { throw new OrderEventsConsumerDemo.TransientFailure("down"); });
        broker.publish(event("e1", "FC-1", "PAID"));
        var a = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(a));
        group.poll(a, false);
        for (int i = 0; i < 5; i++) group.drainRetryTopic(a);
        assertEquals(1, broker.deadLetters.size());
        assertEquals(OrderEventsConsumerDemo.OrderEventsConsumer.MAX_ATTEMPTS, broker.deadLetters.get(0).attempts());
        assertEquals(1, broker.committed[0], "a failing message must not block the partition");
    }

    @Test
    void everyPartitionIsAssignedToExactlyOneMember() {
        var broker = new OrderEventsConsumerDemo.Broker(5);
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> {});
        var a = new OrderEventsConsumerDemo.Member("A");
        var b = new OrderEventsConsumerDemo.Member("B");
        group.assign(List.of(a, b));
        assertEquals(5, a.assigned.size() + b.assigned.size());
        a.assigned.forEach(p -> assertFalse(b.assigned.contains(p)));
    }
}
