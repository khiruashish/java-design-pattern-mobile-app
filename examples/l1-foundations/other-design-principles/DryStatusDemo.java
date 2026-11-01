import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** DRY: the order lifecycle rules live in ONE place, and every client asks that place. */
public class DryStatusDemo {

    // ===================== BEFORE: the rule copied into two clients =====================
    static boolean apiCanCancel(String status) { return status.equals("PLACED") || status.equals("PACKED"); }   // (1)!
    static boolean supportToolCanCancel(String status) { return status.equals("PLACED"); }

    // ===================== AFTER: one authoritative state machine =====================
    enum OrderStatus {                                                                   // (2)!
        PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

        private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
                PLACED, EnumSet.of(PACKED, CANCELLED),
                PACKED, EnumSet.of(OUT_FOR_DELIVERY, CANCELLED),
                OUT_FOR_DELIVERY, EnumSet.of(DELIVERED),
                DELIVERED, EnumSet.noneOf(OrderStatus.class),
                CANCELLED, EnumSet.noneOf(OrderStatus.class));

        boolean canMoveTo(OrderStatus next) { return ALLOWED.get(this).contains(next); }

        OrderStatus moveTo(OrderStatus next) {                                           // (3)!
            if (!canMoveTo(next)) throw new IllegalStateException("cannot go from " + this + " to " + next);
            return next;
        }
    }

    public static void main(String[] args) {
        System.out.println("before, PACKED cancellable? api=" + apiCanCancel("PACKED") + " support=" + supportToolCanCancel("PACKED"));

        OrderStatus s = OrderStatus.PLACED;
        for (OrderStatus next : new OrderStatus[] {OrderStatus.PACKED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED}) {
            try {
                s = s.moveTo(next);
                System.out.println("moved to " + s);
            } catch (IllegalStateException e) {
                System.out.println("rejected: " + e.getMessage());
            }
        }
        System.out.println("after, PACKED cancellable? everyone=" + OrderStatus.PACKED.canMoveTo(OrderStatus.CANCELLED));  // (4)!
    }
}
