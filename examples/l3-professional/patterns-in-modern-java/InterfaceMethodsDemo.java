import java.util.List;

/** Default, static and private interface methods: template steps, factories and shared helpers inside the interface. */
public class InterfaceMethodsDemo {

    interface Notifier {
        void deliver(String to, String text);                                                       // the one abstract method

        default void orderPlaced(String to, String orderId) { deliver(to, format("Order %s placed", orderId)); }   // (1)!
        default void outForDelivery(String to, String orderId, int etaMin) { deliver(to, format("Order %s arrives in %d min", orderId, etaMin)); }

        static Notifier sms(List<String> outbox) { return (to, text) -> outbox.add("SMS " + to + ": " + text); }   // (2)!
        static Notifier push(List<String> outbox) { return (to, text) -> outbox.add("PUSH " + to + ": " + text); }
        static Notifier both(Notifier a, Notifier b) { return (to, text) -> { a.deliver(to, text); b.deliver(to, text); }; }

        private String format(String template, Object... args) { return "[FreshCart] " + String.format(template, args); }   // (3)!
    }

    public static void main(String[] args) {
        List<String> outbox = new java.util.ArrayList<>();
        Notifier n = Notifier.both(Notifier.sms(outbox), Notifier.push(outbox));
        n.orderPlaced("98450", "FC-1");
        n.outForDelivery("98450", "FC-1", 12);
        outbox.forEach(System.out::println);
    }
}
