/** Minimal Factory Method: callers ask for "a notifier for this channel" and never call `new` themselves. */
public class NotifierFactoryDemo {

    interface Notifier { String send(String to, String text); }

    record Sms() implements Notifier { public String send(String to, String t) { return "SMS " + to + ": " + t; } }
    record Push() implements Notifier { public String send(String to, String t) { return "PUSH " + to + ": " + t; } }

    static Notifier notifierFor(String channel) {                                       // (1)!
        return switch (channel) {
            case "sms" -> new Sms();
            case "push" -> new Push();
            default -> throw new IllegalArgumentException("unknown channel " + channel);
        };
    }

    public static void main(String[] args) {
        System.out.println(notifierFor("sms").send("+919876543210", "Your order is packed"));
        System.out.println(notifierFor("push").send("asha-phone", "Rider is 5 min away"));
        try { notifierFor("fax"); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
