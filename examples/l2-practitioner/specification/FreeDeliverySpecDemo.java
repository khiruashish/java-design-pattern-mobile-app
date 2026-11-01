import java.util.List;

/** Minimal: QuickDrop's free-delivery rule as one named, reusable specification. */
public class FreeDeliverySpecDemo {

    record Order(String id, long totalPaise, double distanceKm, boolean firstOrder) { }

    interface Spec<T> {
        boolean isSatisfiedBy(T t);
        default Spec<T> and(Spec<T> o) { return t -> isSatisfiedBy(t) && o.isSatisfiedBy(t); }
        default Spec<T> or(Spec<T> o) { return t -> isSatisfiedBy(t) || o.isSatisfiedBy(t); }
    }

    static final Spec<Order> FREE_DELIVERY =                                                          // (1)!
            ((Spec<Order>) o -> o.totalPaise() >= 49_900).or(Order::firstOrder)
                    .and(o -> o.distanceKm() <= 5.0);

    public static void main(String[] args) {
        for (Order o : List.of(new Order("Q1", 52_000, 3.2, false), new Order("Q2", 21_000, 2.0, true),
                               new Order("Q3", 21_000, 2.0, false), new Order("Q4", 80_000, 7.5, false)))
            System.out.println(o.id() + " free delivery: " + FREE_DELIVERY.isSatisfiedBy(o));   // (2)!
    }
}
