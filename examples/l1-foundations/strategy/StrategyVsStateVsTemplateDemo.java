/** The same shape, three intents: Strategy (caller picks), State (object switches itself), Template Method (subclass fills a step). */
public class StrategyVsStateVsTemplateDemo {

    // ===================== Strategy: the CLIENT chooses how to round =====================
    interface Rounding { long round(long cents); }
    static long total(long cents, Rounding r) { return r.round(cents); }                    // (1)!

    // ===================== State: the OBJECT changes its own behaviour as it moves =====================
    static final class Order {
        private OrderState state = new Placed();
        String cancel() { String r = state.cancel(); state = state.next(); return r; }
        String advance() { state = state.next(); return "now " + state.getClass().getSimpleName(); }
    }
    interface OrderState { String cancel(); OrderState next(); }                          // (2)!
    record Placed() implements OrderState {
        public String cancel() { return "cancelled, full refund"; }
        public OrderState next() { return new Packed(); }
    }
    record Packed() implements OrderState {
        public String cancel() { return "cancelled, restocking fee"; }
        public OrderState next() { return new OutForDelivery(); }
    }
    record OutForDelivery() implements OrderState {
        public String cancel() { return "too late to cancel"; }
        public OrderState next() { return this; }
    }

    // ===================== Template Method: the SUBCLASS fills a step of a fixed algorithm =====================
    abstract static class Report {                                                        // (3)!
        final String render() { return header() + " | " + body() + " | end"; }
        String header() { return "FreshCart"; }
        abstract String body();
    }
    static final class DailySales extends Report { String body() { return "sales 1,204 orders"; } }

    public static void main(String[] args) {
        System.out.println("strategy: " + total(74_950, c -> (c + 99) / 100 * 100) + " / " + total(74_950, c -> c));
        Order o = new Order();
        System.out.println("state: " + o.advance() + ", cancel -> " + new Order().cancel());
        Order late = new Order(); late.advance(); late.advance();
        System.out.println("state: " + late.cancel());
        System.out.println("template: " + new DailySales().render());
    }
}
