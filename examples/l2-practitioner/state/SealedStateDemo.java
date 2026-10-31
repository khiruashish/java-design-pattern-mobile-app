import java.time.Instant;

/** States as sealed records: each state carries ONLY the data that exists in that state; transitions are an exhaustive switch. */
public class SealedStateDemo {

    sealed interface OrderState permits Placed, Packed, OutForDelivery, Delivered, Cancelled { }   // (1)!
    record Placed(Instant at) implements OrderState { }
    record Packed(Instant at, int bags) implements OrderState { }
    record OutForDelivery(String riderId) implements OrderState { }                                  // (2)!
    record Delivered(Instant at, String receivedBy) implements OrderState { }
    record Cancelled(String reason) implements OrderState { }

    sealed interface Event permits Pack, Dispatch, Deliver, Cancel { }
    record Pack(int bags) implements Event { }
    record Dispatch(String riderId) implements Event { }
    record Deliver(String receivedBy) implements Event { }
    record Cancel(String reason) implements Event { }

    static OrderState apply(OrderState s, Event e, Instant now) {
        return switch (s) {                                                                          // (3)!
            case Placed p when e instanceof Pack(int bags) -> new Packed(now, bags);
            case Placed p when e instanceof Cancel(String r) -> new Cancelled(r);
            case Packed p when e instanceof Dispatch(String rider) -> new OutForDelivery(rider);
            case Packed p when e instanceof Cancel(String r) -> new Cancelled(r + " (restock " + p.bags() + " bags)");
            case OutForDelivery o when e instanceof Deliver(String who) -> new Delivered(now, who);
            default -> throw new IllegalStateException(e.getClass().getSimpleName() + " not allowed in " + s.getClass().getSimpleName());   // (4)!
        };
    }

    public static void main(String[] args) {
        Instant t = Instant.parse("2026-11-02T10:00:00Z");
        OrderState s = new Placed(t);
        s = apply(s, new Pack(3), t.plusSeconds(600));
        s = apply(s, new Dispatch("R-42"), t.plusSeconds(900));
        System.out.println(s);
        try { apply(s, new Cancel("customer changed mind"), t); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        s = apply(s, new Deliver("security desk"), t.plusSeconds(2400));
        System.out.println(s);
        if (s instanceof Delivered(Instant at, String who)) System.out.println("delivered to " + who + " at " + at);
    }
}
