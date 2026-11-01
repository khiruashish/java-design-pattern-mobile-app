import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cost of indirection: the same discount rule written directly and through Strategy + Factory + Registry. The
 * patterned version costs more types and a deeper call path for every reader; it pays off only when variants and
 * change make it worth it.
 */
public class IndirectionCostDemo {

    static int depthAtCalculation;
    static void markDepth() { depthAtCalculation = Thread.currentThread().getStackTrace().length; }

    // ----- direct: one method -----
    static long directDiscount(String tier, long totalPaise) {                                     // (1)!
        markDepth();
        return tier.equals("GOLD") ? totalPaise / 10 : 0;
    }

    // ----- patterned: interface, implementations, factory, registry -----
    interface DiscountStrategy { long discount(long totalPaise); }
    static final class GoldDiscount implements DiscountStrategy { public long discount(long t) { markDepth(); return t / 10; } }
    static final class NoDiscount implements DiscountStrategy { public long discount(long t) { markDepth(); return 0; } }
    static final class DiscountRegistry {
        private final Map<String, Supplier<DiscountStrategy>> byTier = new HashMap<>();
        DiscountRegistry register(String tier, Supplier<DiscountStrategy> s) { byTier.put(tier, s); return this; }
        DiscountStrategy forTier(String tier) { return byTier.getOrDefault(tier, NoDiscount::new).get(); }
    }
    static final class DiscountService {
        private final DiscountRegistry registry;
        DiscountService(DiscountRegistry registry) { this.registry = registry; }
        long discount(String tier, long total) { return lookup(tier).discount(total); }            // (2)!
        private DiscountStrategy lookup(String tier) { return registry.forTier(tier); }
    }

    public static void main(String[] args) {
        long d1 = directDiscount("GOLD", 50_000);
        int directDepth = depthAtCalculation;
        DiscountService service = new DiscountService(new DiscountRegistry().register("GOLD", GoldDiscount::new));
        long d2 = service.discount("GOLD", 50_000);
        int patternedDepth = depthAtCalculation;

        System.out.println("same answer: " + (d1 == d2) + " (Rs " + d1 / 100 + ")");
        System.out.println("types to read: direct 0, patterned 5 (interface, 2 strategies, registry, service)");
        System.out.println("runtime cost: " + (patternedDepth - directDepth) + " extra stack frame (negligible)");
        System.out.println("reader cost: direct 1 jump to the rule; patterned 5 (service, lookup, registry, registration, strategy)");
        System.out.println("to add a PLATINUM tier: direct = edit 1 method; patterned = add 1 class + 1 registration");   // (3)!
    }
}
