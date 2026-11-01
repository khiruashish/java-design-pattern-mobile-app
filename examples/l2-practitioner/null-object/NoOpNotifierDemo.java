import java.util.ArrayList;
import java.util.List;

/** Minimal: TextBlast order SMS. Customers who opted out get a notifier that does nothing, so checkout code has no if-statements. */
public class NoOpNotifierDemo {

    interface Notifier { void orderPlaced(String orderId); }

    record SmsNotifier(String phone, List<String> outbox) implements Notifier {
        public void orderPlaced(String orderId) { outbox.add("SMS " + phone + ": order " + orderId + " placed"); }
    }

    enum NoOpNotifier implements Notifier {                                                           // (1)!
        INSTANCE;
        public void orderPlaced(String orderId) { }
    }

    static Notifier notifierFor(String phone, boolean optedIn, List<String> outbox) {                  // (2)!
        return optedIn ? new SmsNotifier(phone, outbox) : NoOpNotifier.INSTANCE;
    }

    public static void main(String[] args) {
        List<String> outbox = new ArrayList<>();
        notifierFor("98450", true, outbox).orderPlaced("FC-1001");
        notifierFor("70000", false, outbox).orderPlaced("FC-1002");
        System.out.println("messages sent: " + outbox.size());
        outbox.forEach(System.out::println);
    }
}
