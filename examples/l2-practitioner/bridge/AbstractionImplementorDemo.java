/** Bridge: WHAT we send (abstraction) and HOW it's delivered (implementor) vary independently. */
public class AbstractionImplementorDemo {

    // ===================== Implementor side: how a message travels =====================
    interface Channel { String deliver(String to, String title, String body); }               // (1)!
    record Sms() implements Channel { public String deliver(String to, String t, String b) { return "SMS to " + to + ": " + t + " - " + b; } }
    record Push() implements Channel { public String deliver(String to, String t, String b) { return "PUSH to device of " + to + " [" + t + "] " + b; } }
    record Email() implements Channel { public String deliver(String to, String t, String b) { return "EMAIL to " + to + "@mail, subject '" + t + "'"; } }

    // ===================== Abstraction side: what kind of message =====================
    abstract static class Notification {                                                      // (2)!
        protected final Channel channel;
        Notification(Channel channel) { this.channel = channel; }
        abstract String send(String customer);
    }
    static final class OrderUpdate extends Notification {
        private final String orderId, status;
        OrderUpdate(Channel c, String orderId, String status) { super(c); this.orderId = orderId; this.status = status; }
        String send(String customer) { return channel.deliver(customer, "Order " + orderId, status); }
    }
    static final class PromoMessage extends Notification {
        private final int percent;
        PromoMessage(Channel c, int percent) { super(c); this.percent = percent; }
        String send(String customer) { return channel.deliver(customer, percent + "% off fruit", "today only"); }   // (3)!
    }

    public static void main(String[] args) {
        System.out.println(new OrderUpdate(new Sms(), "FC-5001", "out for delivery").send("asha"));
        System.out.println(new OrderUpdate(new Push(), "FC-5001", "delivered").send("asha"));
        System.out.println(new PromoMessage(new Email(), 20).send("ravi"));
        System.out.println(new PromoMessage(new Push(), 20).send("ravi"));
    }
}
