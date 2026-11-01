import java.util.Map;

public class NotifierDemo {

    // ---- Without a pattern: every new channel means editing this method ----
    static String notifyWithSwitch(String channel, String customer, String message) {
        switch (channel) {
            case "sms":   return "SMS to " + customer + ": " + message;
            case "email": return "Email to " + customer + ": " + message;
            default: throw new IllegalArgumentException("Unknown channel: " + channel);
        }
    }

    // ---- With the Strategy pattern: each channel is an object behind one interface ----
    interface Notifier {                                              // (1)!
        String send(String customer, String message);
    }

    static final class SmsNotifier implements Notifier {              // (2)!
        public String send(String customer, String message) { return "SMS to " + customer + ": " + message; }
    }

    static final class EmailNotifier implements Notifier {
        public String send(String customer, String message) { return "Email to " + customer + ": " + message; }
    }

    static final class PushNotifier implements Notifier {             // (3)!
        public String send(String customer, String message) { return "Push to " + customer + ": " + message; }
    }

    public static void main(String[] args) {
        System.out.println(notifyWithSwitch("sms", "Asha", "Order placed"));

        Map<String, Notifier> notifiers = Map.of(
                "sms", new SmsNotifier(), "email", new EmailNotifier(), "push", new PushNotifier());
        for (String channel : new String[] {"sms", "email", "push"}) {
            System.out.println(notifiers.get(channel).send("Asha", "Order placed"));  // (4)!
        }
    }
}
