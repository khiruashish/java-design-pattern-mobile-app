import java.util.List;

/** Classic GoF Factory Method: the base class's algorithm calls an abstract creation method. */
public class ClassicFactoryMethodDemo {

    interface Receipt { String render(String orderId, long totalInCents); }

    record SmsReceipt() implements Receipt {
        public String render(String id, long cents) { return "FreshCart: order " + id + " paid " + money(cents); }
    }
    record EmailReceipt(String brandColour) implements Receipt {
        public String render(String id, long cents) { return "<h1 style='color:" + brandColour + "'>Order " + id + "</h1><p>Total " + money(cents) + "</p>"; }
    }

    /** Creator: owns the workflow, defers WHICH receipt to subclasses. */
    abstract static class ReceiptSender {
        protected abstract Receipt createReceipt();                                       // (1)!

        final String send(String orderId, long totalInCents) {                           // (2)!
            if (totalInCents <= 0) throw new IllegalArgumentException("total must be positive");
            Receipt receipt = createReceipt();
            return channel() + " -> " + receipt.render(orderId, totalInCents);
        }

        protected abstract String channel();
    }

    static final class SmsReceiptSender extends ReceiptSender {                          // (3)!
        protected Receipt createReceipt() { return new SmsReceipt(); }
        protected String channel() { return "sms"; }
    }

    static final class EmailReceiptSender extends ReceiptSender {
        protected Receipt createReceipt() { return new EmailReceipt("#2e7d32"); }
        protected String channel() { return "email"; }
    }

    static String money(long c) { return "$%d.%02d".formatted(c / 100, c % 100); }

    public static void main(String[] args) {
        for (ReceiptSender sender : List.of(new SmsReceiptSender(), new EmailReceiptSender())) {
            System.out.println(sender.send("FC-2101", 4_250));
        }
        try { new SmsReceiptSender().send("FC-2102", 0); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
