import java.util.List;
import java.util.Optional;

/** Realistic chain: assign a new order to a rider, trying cheaper options first and falling back to partners and a queue. */
public class RiderAssignmentChainDemo {

    record Order(String id, double kmFromStore, boolean bulky) { }
    record Assignment(String order, String who, String why) { }

    interface AssignStep { Optional<Assignment> tryAssign(Order o); String name(); }           // (1)!

    record IdleRiderNearby(List<String> idleRiders) implements AssignStep {
        public Optional<Assignment> tryAssign(Order o) {
            return o.kmFromStore() <= 3 && !o.bulky() && !idleRiders.isEmpty()
                    ? Optional.of(new Assignment(o.id(), idleRiders.get(0), "idle rider within 3 km")) : Optional.empty();
        }
        public String name() { return "idle rider"; }
    }
    record BatchWithRoute(String routeId, double routeEndKm) implements AssignStep {
        public Optional<Assignment> tryAssign(Order o) {
            return Math.abs(o.kmFromStore() - routeEndKm) < 1.0 ? Optional.of(new Assignment(o.id(), routeId, "added to a route ending nearby")) : Optional.empty();
        }
        public String name() { return "batching"; }
    }
    record PartnerFleet(String partner) implements AssignStep {
        public Optional<Assignment> tryAssign(Order o) {
            return o.kmFromStore() <= 12 ? Optional.of(new Assignment(o.id(), partner, "partner fleet (costs more)")) : Optional.empty();   // (2)!
        }
        public String name() { return "partner"; }
    }

    static Assignment assign(List<AssignStep> chain, Order o) {
        for (AssignStep s : chain) { Optional<Assignment> a = s.tryAssign(o); if (a.isPresent()) return a.get(); }
        return new Assignment(o.id(), "queue", "no option now; retry in 2 minutes");               // (3)!
    }

    public static void main(String[] args) {
        List<AssignStep> chain = List.of(new IdleRiderNearby(List.of("R-42")), new BatchWithRoute("ROUTE-7", 5.5), new PartnerFleet("ParcelGo"));
        for (Order o : List.of(new Order("FC-9201", 1.2, false), new Order("FC-9202", 5.1, false), new Order("FC-9203", 2.0, true), new Order("FC-9204", 18, false)))
            System.out.println(assign(chain, o));
    }
}
