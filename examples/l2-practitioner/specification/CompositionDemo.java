import java.util.List;

/** and / or / not as default methods: small named rules combine into bigger ones, and the result can describe itself. */
public class CompositionDemo {

    record Customer(String name, String tier, int ordersLast30Days, boolean emailVerified) { }

    interface Spec<T> {
        boolean isSatisfiedBy(T t);
        String describe();

        default Spec<T> and(Spec<T> other) { return of("(" + describe() + " and " + other.describe() + ")", t -> isSatisfiedBy(t) && other.isSatisfiedBy(t)); }   // (1)!
        default Spec<T> or(Spec<T> other) { return of("(" + describe() + " or " + other.describe() + ")", t -> isSatisfiedBy(t) || other.isSatisfiedBy(t)); }
        default Spec<T> not() { return of("not " + describe(), t -> !isSatisfiedBy(t)); }

        static <T> Spec<T> of(String description, java.util.function.Predicate<T> test) {          // (2)!
            return new Spec<>() {
                public boolean isSatisfiedBy(T t) { return test.test(t); }
                public String describe() { return description; }
            };
        }
    }

    static final Spec<Customer> GOLD = Spec.of("gold tier", c -> c.tier().equals("gold"));
    static final Spec<Customer> FREQUENT = Spec.of("5+ orders in 30 days", c -> c.ordersLast30Days() >= 5);
    static final Spec<Customer> VERIFIED = Spec.of("verified email", Customer::emailVerified);

    static final Spec<Customer> EARLY_ACCESS = GOLD.or(FREQUENT).and(VERIFIED);                      // (3)!

    public static void main(String[] args) {
        System.out.println("rule: " + EARLY_ACCESS.describe());
        for (Customer c : List.of(new Customer("Asha", "gold", 1, true), new Customer("Ravi", "silver", 7, true),
                                  new Customer("Meena", "silver", 2, true), new Customer("Kiran", "gold", 9, false)))
            System.out.println("  " + c.name() + ": " + EARLY_ACCESS.isSatisfiedBy(c));
        System.out.println("rule: " + VERIFIED.not().describe() + " -> Kiran " + VERIFIED.not().isSatisfiedBy(new Customer("Kiran", "gold", 9, false)));
    }
}
