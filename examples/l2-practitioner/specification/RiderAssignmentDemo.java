import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Realistic: FreshCart assigns orders to riders. A rider is eligible if on shift AND has bag capacity AND
 * (has a cold box OR the order needs no cold chain) AND is within 3 km. Dispatch support must see WHY a rider was skipped.
 */
public class RiderAssignmentDemo {

    record Rider(String name, boolean onShift, int bagsFree, boolean coldBox, double kmAway) { }
    record Order(String id, int bags, boolean needsCold) { }

    /** A specification over (rider, order) that can say which leaf rules failed. */
    record Rule(String name, java.util.function.BiPredicate<Rider, Order> test) {
        List<String> failures(Rider r, Order o) { return test.test(r, o) ? List.of() : List.of(name); }
    }

    record AllOf(List<Rule> rules) {                                                                  // (1)!
        boolean isSatisfiedBy(Rider r, Order o) { return rules.stream().allMatch(x -> x.test().test(r, o)); }
        List<String> whyNot(Rider r, Order o) {
            List<String> out = new ArrayList<>();
            rules.forEach(x -> out.addAll(x.failures(r, o)));
            return out;
        }
    }

    static final Rule ON_SHIFT = new Rule("not on shift", (r, o) -> r.onShift());
    static final Rule CAPACITY = new Rule("no bag space", (r, o) -> r.bagsFree() >= o.bags());
    static final Rule COLD_CHAIN = new Rule("no cold box for chilled items", (r, o) -> r.coldBox() || !o.needsCold());   // (2)!
    static final Rule NEARBY = new Rule("more than 3 km away", (r, o) -> r.kmAway() <= 3.0);

    static final AllOf CAN_TAKE = new AllOf(List.of(ON_SHIFT, CAPACITY, COLD_CHAIN, NEARBY));

    public static void main(String[] args) {
        Order order = new Order("FC-3001", 2, true);
        List<Rider> riders = List.of(new Rider("Arjun", true, 3, false, 1.2), new Rider("Bina", true, 2, true, 2.5),
                                     new Rider("Chetan", false, 4, true, 4.1), new Rider("Divya", true, 1, true, 0.8));
        for (Rider r : riders) {
            List<String> why = CAN_TAKE.whyNot(r, order);                                             // (3)!
            System.out.println(r.name() + ": " + (why.isEmpty() ? "eligible" : "skipped " + why));
        }
        Predicate<Rider> eligible = r -> CAN_TAKE.isSatisfiedBy(r, order);
        System.out.println("assign to: " + riders.stream().filter(eligible).map(Rider::name).findFirst().orElse("nobody, queue the order"));
    }
}
