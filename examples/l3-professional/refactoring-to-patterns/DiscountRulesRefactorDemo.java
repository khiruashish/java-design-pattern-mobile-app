import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FreshCart discounts: a growing if-chain refactored, step by step under a golden master, into a list of rule
 * objects (Strategy + Chain of Responsibility). New promotions become new classes, not new branches.
 */
public class DiscountRulesRefactorDemo {

    record Cart(long subtotal, boolean member, boolean firstOrder, int dairyItems, String coupon) {}

    // ---- Before ----
    static long discountBefore(Cart c) {
        long d = 0;
        if (c.firstOrder()) d += Math.min(c.subtotal() / 5, 10_000);
        if (c.member() && c.subtotal() >= 30_000) d += c.subtotal() / 20;
        if (c.dairyItems() >= 3) d += 2_000;
        if ("FRESH10".equals(c.coupon()) && !c.firstOrder()) d += c.subtotal() / 10;
        return Math.min(d, c.subtotal() / 2);
    }

    // ---- After ----
    interface DiscountRule {                                                       // (1)!
        String name();
        long discount(Cart cart);
    }
    record FirstOrder() implements DiscountRule {
        public String name() { return "first order 20% (max Rs 100)"; }
        public long discount(Cart c) { return c.firstOrder() ? Math.min(c.subtotal() / 5, 10_000) : 0; }
    }
    record MemberFivePercent() implements DiscountRule {
        public String name() { return "member 5% over Rs 300"; }
        public long discount(Cart c) { return c.member() && c.subtotal() >= 30_000 ? c.subtotal() / 20 : 0; }
    }
    record DairyBundle() implements DiscountRule {
        public String name() { return "3+ dairy items Rs 20"; }
        public long discount(Cart c) { return c.dairyItems() >= 3 ? 2_000 : 0; }
    }
    record Fresh10Coupon() implements DiscountRule {
        public String name() { return "FRESH10 coupon"; }
        public long discount(Cart c) { return "FRESH10".equals(c.coupon()) && !c.firstOrder() ? c.subtotal() / 10 : 0; }
    }

    record Breakdown(List<String> applied, long total) {}

    record DiscountEngine(List<DiscountRule> rules, int capPercent) {             // (2)!
        Breakdown apply(Cart c) {
            List<String> applied = new ArrayList<>();
            long sum = 0;
            for (DiscountRule r : rules) {
                long d = r.discount(c);
                if (d > 0) { applied.add(r.name() + " -" + d); sum += d; }
            }
            return new Breakdown(applied, Math.min(sum, c.subtotal() * capPercent / 100));
        }
    }

    public static void main(String[] args) {
        var engine = new DiscountEngine(List.of(new FirstOrder(), new MemberFivePercent(), new DairyBundle(), new Fresh10Coupon()), 50);
        Random random = new Random(11);
        int mismatches = 0;
        for (int i = 0; i < 20_000; i++) {                                           // (3)!
            Cart c = new Cart(random.nextInt(200_000), random.nextBoolean(), random.nextInt(5) == 0, random.nextInt(6),
                    random.nextBoolean() ? "FRESH10" : null);
            if (engine.apply(c).total() != discountBefore(c)) mismatches++;
        }
        System.out.println("golden master: 20000 random carts, mismatches: " + mismatches);
        Breakdown b = engine.apply(new Cart(60_000, true, false, 4, "FRESH10"));
        System.out.println("receipt lines: " + b.applied() + ", total " + b.total());
    }
}
