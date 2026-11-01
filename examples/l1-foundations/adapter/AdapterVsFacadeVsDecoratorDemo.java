/** Same "wrapper" shape, three intents: Adapter changes the interface, Facade simplifies many, Decorator adds behaviour. */
public class AdapterVsFacadeVsDecoratorDemo {

    interface Notifier { String send(String to, String text); }

    /** A vendor SDK with a different interface. */
    static final class TextBlastSdk { int blast(String body, String[] numbers) { return numbers.length; } }

    // Adapter: SAME capability, DIFFERENT interface -> our interface
    static final class TextBlastAdapter implements Notifier {                             // (1)!
        private final TextBlastSdk sdk = new TextBlastSdk();
        public String send(String to, String text) { return "sent to " + sdk.blast(text, new String[]{to}) + " number"; }
    }

    // Decorator: SAME interface, EXTRA behaviour
    static final class QuietHoursNotifier implements Notifier {                           // (2)!
        private final Notifier inner; private final int hour;
        QuietHoursNotifier(Notifier inner, int hour) { this.inner = inner; this.hour = hour; }
        public String send(String to, String text) { return hour >= 22 || hour < 7 ? "held until 07:00" : inner.send(to, text); }
    }

    // Facade: ONE simple entry point over SEVERAL subsystems
    static final class OrderUpdates {                                                     // (3)!
        private final Notifier sms; private final java.util.List<String> audit = new java.util.ArrayList<>();
        OrderUpdates(Notifier sms) { this.sms = sms; }
        String outForDelivery(String orderId, String phone, String rider) {
            String r = sms.send(phone, "Order " + orderId + " is on the way with " + rider);
            audit.add(orderId + " notified");
            return r + ", audit=" + audit;
        }
    }

    public static void main(String[] args) {
        Notifier adapter = new TextBlastAdapter();
        System.out.println("adapter:   " + adapter.send("+919800000001", "hi"));
        System.out.println("decorator: " + new QuietHoursNotifier(adapter, 23).send("+919800000001", "hi"));
        System.out.println("facade:    " + new OrderUpdates(adapter).outForDelivery("FC-1801", "+919800000001", "Ravi"));
    }
}
