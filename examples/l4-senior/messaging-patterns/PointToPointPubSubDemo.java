import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Point-to-point: a queue delivers each message to exactly one receiver (work distribution).
 * Publish-subscribe: a topic delivers a copy of each message to every subscriber (event broadcast).
 */
public class PointToPointPubSubDemo {

    static final class Queue {                                                                 // (1)!
        private final List<Consumer<String>> receivers = new ArrayList<>(); private int next;
        void addReceiver(Consumer<String> r) { receivers.add(r); }
        void send(String m) { receivers.get(next++ % receivers.size()).accept(m); }
    }

    static final class Topic {                                                                 // (2)!
        private final Map<String, Consumer<String>> subscribers = new LinkedHashMap<>();
        void subscribe(String name, Consumer<String> s) { subscribers.put(name, s); }
        void publish(String m) { subscribers.values().forEach(s -> s.accept(m)); }
    }

    public static void main(String[] args) {
        Queue pickTasks = new Queue();
        pickTasks.addReceiver(m -> System.out.println("  picker Ravi got " + m));
        pickTasks.addReceiver(m -> System.out.println("  picker Meena got " + m));
        System.out.println("queue: pick tasks");
        for (String o : List.of("pick FC-1", "pick FC-2", "pick FC-3")) pickTasks.send(o);

        Topic orderPlaced = new Topic();
        orderPlaced.subscribe("loyalty", m -> System.out.println("  loyalty: award points for " + m));
        orderPlaced.subscribe("analytics", m -> System.out.println("  analytics: count " + m));
        orderPlaced.subscribe("notifications", m -> System.out.println("  notifications: confirm " + m));   // (3)!
        System.out.println("topic: OrderPlaced");
        orderPlaced.publish("FC-1");
    }
}
