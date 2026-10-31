import java.util.EnumSet;
import java.util.Set;

/** A compact state machine: an enum where each constant declares which states it may move to. */
public class EnumStateMachineDemo {

    enum OrderStatus {                                                                          // (1)!
        PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

        Set<OrderStatus> allowedNext() {
            return switch (this) {
                case PLACED -> EnumSet.of(PACKED, CANCELLED);
                case PACKED -> EnumSet.of(OUT_FOR_DELIVERY, CANCELLED);
                case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
                case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);                  // (2)!
            };
        }
        boolean isTerminal() { return allowedNext().isEmpty(); }

        OrderStatus moveTo(OrderStatus next) {                                                   // (3)!
            if (!allowedNext().contains(next)) throw new IllegalStateException(this + " -> " + next + " is not allowed");
            return next;
        }
    }

    public static void main(String[] args) {
        OrderStatus s = OrderStatus.PLACED;
        s = s.moveTo(OrderStatus.PACKED);
        s = s.moveTo(OrderStatus.OUT_FOR_DELIVERY);
        System.out.println("now " + s);
        try { s.moveTo(OrderStatus.CANCELLED); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        s = s.moveTo(OrderStatus.DELIVERED);
        System.out.println("now " + s + ", terminal: " + s.isTerminal());
    }
}
