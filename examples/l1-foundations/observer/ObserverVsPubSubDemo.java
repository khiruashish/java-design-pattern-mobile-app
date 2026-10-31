import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Observer: subscribers know the subject. Pub/Sub: publishers and subscribers only know a broker and a topic. */
public class ObserverVsPubSubDemo {

    // ===================== Observer: direct reference to the subject =====================
    static final class Basket {
        private final List<Consumer<Integer>> observers = new ArrayList<>();
        void observe(Consumer<Integer> o) { observers.add(o); }                          // (1)!
        void add(int itemCount) { observers.forEach(o -> o.accept(itemCount)); }
    }

    // ===================== Pub/Sub: a broker in the middle =====================
    static final class Broker {                                                           // (2)!
        private final Map<String, List<Consumer<String>>> topics = new HashMap<>();
        void subscribe(String topic, Consumer<String> s) { topics.computeIfAbsent(topic, t -> new ArrayList<>()).add(s); }
        int publish(String topic, String message) {
            List<Consumer<String>> subs = topics.getOrDefault(topic, List.of());
            subs.forEach(s -> s.accept(message));
            return subs.size();
        }
    }

    public static void main(String[] args) {
        Basket basket = new Basket();
        basket.observe(n -> System.out.println("observer: basket badge shows " + n));
        basket.add(3);

        Broker broker = new Broker();
        broker.subscribe("orders.placed", m -> System.out.println("pubsub inventory got " + m));
        broker.subscribe("orders.placed", m -> System.out.println("pubsub loyalty got " + m));
        int delivered = broker.publish("orders.placed", "FC-1201");                       // (3)!
        System.out.println("delivered to " + delivered + " subscribers; publisher doesn't know who");
        System.out.println("nobody listening to orders.cancelled: " + broker.publish("orders.cancelled", "FC-1202") + " deliveries");
    }
}
