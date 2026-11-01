/** State: an object changes behaviour when its internal state changes, by delegating to a state object. */
public class StatePerClassDemo {

    // ===================== Before: every method switches on a status string =====================
    static final class OrderBefore {
        String status = "PLACED";
        String dispatch() {
            switch (status) {                                                                   // (1)!
                case "PLACED": return "can't dispatch: not packed yet";
                case "PACKED": status = "OUT_FOR_DELIVERY"; return "dispatched";
                case "CANCELLED": return "can't dispatch: cancelled";
                default: return "can't dispatch from " + status;
            }
        }
        // ...and the same switch again in pack(), deliver(), cancel()
    }

    // ===================== After: one class per state =====================
    interface OrderState {                                                                      // (2)!
        default OrderState pack(Order o) { return reject(o, "pack"); }
        default OrderState dispatch(Order o) { return reject(o, "dispatch"); }
        default OrderState deliver(Order o) { return reject(o, "deliver"); }
        default OrderState cancel(Order o) { return reject(o, "cancel"); }
        private OrderState reject(Order o, String action) { o.log("  can't " + action + " when " + name()); return this; }
        default String name() { return getClass().getSimpleName(); }
    }
    static final class Placed implements OrderState {
        public OrderState pack(Order o) { o.log("  packed"); return new Packed(); }
        public OrderState cancel(Order o) { o.log("  cancelled, nothing to refund yet"); return new Cancelled(); }
    }
    static final class Packed implements OrderState {
        public OrderState dispatch(Order o) { o.log("  handed to rider"); return new OutForDelivery(); }
        public OrderState cancel(Order o) { o.log("  cancelled, items returned to shelf"); return new Cancelled(); }   // (3)!
    }
    static final class OutForDelivery implements OrderState {
        public OrderState deliver(Order o) { o.log("  delivered"); return new Delivered(); }
    }
    static final class Delivered implements OrderState { }
    static final class Cancelled implements OrderState { }

    static final class Order {                                                                  // (4)!
        private OrderState state = new Placed();
        void log(String s) { System.out.println(s); }
        void pack() { state = state.pack(this); }
        void dispatch() { state = state.dispatch(this); }
        void deliver() { state = state.deliver(this); }
        void cancel() { state = state.cancel(this); }
        String state() { return state.name(); }
    }

    public static void main(String[] args) {
        System.out.println("before: " + new OrderBefore().dispatch());
        Order o = new Order();
        o.dispatch(); o.pack(); o.dispatch(); o.cancel(); o.deliver();
        System.out.println("final state: " + o.state());
    }
}
