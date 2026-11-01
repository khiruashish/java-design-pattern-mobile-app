/** Same structure, different intent: a Strategy is chosen from outside and stays; a State replaces itself as things happen. */
public class StateVsStrategyDemo {

    // ===================== Strategy: the caller picks how delivery fees are computed =====================
    interface FeeStrategy { long fee(long orderPaise); }
    record Checkout(FeeStrategy fees) { long total(long p) { return p + fees.fee(p); } }      // (1)!

    // ===================== State: the rider's own state decides what "next job" does, and changes itself =====================
    interface RiderState { RiderState nextJob(Rider r); String name(); }
    record Available() implements RiderState {
        public RiderState nextJob(Rider r) { r.jobs++; return r.jobs >= 2 ? new OnBreak() : this; }   // (2)!
        public String name() { return "AVAILABLE"; }
    }
    record OnBreak() implements RiderState {
        public RiderState nextJob(Rider r) { System.out.println("  on break: job goes to another rider"); r.jobs = 0; return new Available(); }
        public String name() { return "ON_BREAK"; }
    }
    static final class Rider { RiderState state = new Available(); int jobs; void offerJob() { state = state.nextJob(this); } }   // (3)!

    public static void main(String[] args) {
        System.out.println("strategy: standard " + new Checkout(p -> 2_500).total(30_000) + ", member " + new Checkout(p -> 0).total(30_000));
        Rider r = new Rider();
        for (int i = 1; i <= 3; i++) { r.offerJob(); System.out.println("state after offer " + i + ": " + r.state.name()); }
    }
}
