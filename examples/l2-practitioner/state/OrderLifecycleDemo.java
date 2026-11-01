import java.util.ArrayList;
import java.util.List;

/** Realistic State: order states with side effects. What "cancel" does depends on the state: free, restock, or refused. */
public class OrderLifecycleDemo {

    interface Effects { void notifyCustomer(String text); void refund(long paise); void restock(int bags); }

    sealed interface State permits Placed, Packed, OutForDelivery, Delivered, Cancelled { }
    record Placed(long paidPaise) implements State { }
    record Packed(long paidPaise, int bags) implements State { }
    record OutForDelivery(long paidPaise, String rider) implements State { }
    record Delivered() implements State { }
    record Cancelled(String reason) implements State { }

    static State cancel(State s, String reason, Effects fx) {                                    // (1)!
        return switch (s) {
            case Placed p -> { fx.refund(p.paidPaise()); fx.notifyCustomer("Order cancelled, full refund"); yield new Cancelled(reason); }
            case Packed p -> { fx.restock(p.bags()); fx.refund(p.paidPaise()); fx.notifyCustomer("Order cancelled, full refund"); yield new Cancelled(reason); }
            case OutForDelivery o -> { fx.notifyCustomer("Rider " + o.rider() + " is on the way; cancel at the door instead"); yield o; }   // (2)!
            case Delivered d -> throw new IllegalStateException("delivered orders are returned, not cancelled");
            case Cancelled c -> c;                                                                // (3)!
        };
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        Effects fx = new Effects() {
            public void notifyCustomer(String t) { log.add("sms: " + t); }
            public void refund(long p) { log.add("refund Rs " + p / 100); }
            public void restock(int b) { log.add("restock " + b + " bags"); }
        };
        for (State s : List.<State>of(new Placed(64_900), new Packed(64_900, 2), new OutForDelivery(64_900, "R-42"), new Cancelled("dup"))) {
            log.clear();
            State after = cancel(s, "customer request", fx);
            System.out.println(s.getClass().getSimpleName() + " -> " + after.getClass().getSimpleName() + " " + log);
        }
        try { cancel(new Delivered(), "x", fx); } catch (IllegalStateException e) { System.out.println("Delivered: " + e.getMessage()); }
    }
}
