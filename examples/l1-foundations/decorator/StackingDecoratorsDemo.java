import java.util.ArrayList;
import java.util.List;

/** Stacking decorators: each adds one concern; the ORDER of wrapping changes behaviour. */
public class StackingDecoratorsDemo {

    interface Notifier { void send(String to, String text); }

    static final class FlakySms implements Notifier {                                     // fails the first call
        final List<String> delivered = new ArrayList<>(); private int calls;
        public void send(String to, String text) {
            if (++calls == 1) throw new IllegalStateException("gateway timeout");
            delivered.add(to + ": " + text);
        }
    }

    record Retrying(Notifier inner, int attempts) implements Notifier {
        public void send(String to, String text) {
            for (int i = 1; ; i++) {
                try { inner.send(to, text); return; }
                catch (RuntimeException e) { if (i == attempts) throw e; }
            }
        }
    }

    record Logging(Notifier inner, List<String> log) implements Notifier {
        public void send(String to, String text) {
            log.add("send " + to);
            try { inner.send(to, text); log.add("ok " + to); }
            catch (RuntimeException e) { log.add("error " + to + ": " + e.getMessage()); throw e; }
        }
    }

    record Truncating(Notifier inner, int max) implements Notifier {
        public void send(String to, String text) { inner.send(to, text.length() > max ? text.substring(0, max - 3) + "..." : text); }
    }

    public static void main(String[] args) {
        List<String> log1 = new ArrayList<>();
        FlakySms sms1 = new FlakySms();
        Notifier a = new Logging(new Retrying(new Truncating(sms1, 20), 3), log1);         // (1)!
        a.send("+91980001", "Your order FC-2101 is out for delivery");
        System.out.println("Logging(Retrying(...)): log " + log1 + ", delivered " + sms1.delivered);

        List<String> log2 = new ArrayList<>();
        FlakySms sms2 = new FlakySms();
        Notifier b = new Retrying(new Logging(new Truncating(sms2, 20), log2), 3);         // (2)!
        b.send("+91980001", "Your order FC-2101 is out for delivery");
        System.out.println("Retrying(Logging(...)): log " + log2 + ", delivered " + sms2.delivered);
    }
}
