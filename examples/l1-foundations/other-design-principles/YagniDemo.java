import java.util.List;

/** YAGNI: ship the rule the business asked for; keep it easy to extend when a real need arrives. */
public class YagniDemo {

    record Basket(long totalInCents, String city) { }

    // ===================== Speculative: built for needs nobody has =====================
    interface DiscountProvider { long discount(Basket b); }                                // (1)!
    interface DiscountProviderFactory { DiscountProvider create(String region, String channel, String tier); }
    static final class ConfigurableDiscountProviderFactory implements DiscountProviderFactory {
        public DiscountProvider create(String region, String channel, String tier) {
            // region, channel and tier are ignored: there is only one rule today
            return b -> b.totalInCents() >= 5_000 ? 500 : 0;
        }
    }

    // ===================== YAGNI: exactly today's requirement =====================
    /** "$5 off orders of $50 or more." */
    static long discountInCents(Basket basket) {                                           // (2)!
        if (basket.totalInCents() < 0) throw new IllegalArgumentException("total must not be negative");
        return basket.totalInCents() >= 5_000 ? 500 : 0;
    }

    // ===================== Months later, a REAL second rule arrives =====================
    /** "Pune customers get $7 off instead." Now an extension point is justified. */
    interface DiscountRule { long discountInCents(Basket b); }                              // (3)!
    static DiscountRule ruleFor(String city) {
        return switch (city) {
            case "Pune" -> b -> b.totalInCents() >= 5_000 ? 700 : 0;
            default -> YagniDemo::discountInCents;
        };
    }

    public static void main(String[] args) {
        Basket mumbai = new Basket(6_200, "Mumbai");
        Basket pune = new Basket(6_200, "Pune");
        System.out.println("speculative: " + new ConfigurableDiscountProviderFactory().create("IN", "app", "gold").discount(mumbai));
        System.out.println("yagni:       " + discountInCents(mumbai));
        for (Basket b : List.of(mumbai, pune)) {
            System.out.println("later, " + b.city() + ": " + ruleFor(b.city()).discountInCents(b));
        }
    }
}
