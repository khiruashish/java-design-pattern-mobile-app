import java.util.List;
import java.util.Optional;

/** Minimal Chain of Responsibility: the first coupon rule that applies to the cart wins. */
public class CouponChainDemo {

    record Cart(long paise, boolean firstOrder, String code) { }
    interface CouponRule { Optional<String> apply(Cart c); }                                       // (1)!

    static final List<CouponRule> RULES = List.of(
            c -> "FRESH50".equals(c.code()) && c.firstOrder() ? Optional.of("Rs 50 off first order") : Optional.empty(),
            c -> "FRUIT10".equals(c.code()) ? Optional.of("10% off fruit") : Optional.empty(),
            c -> c.paise() >= 99_900 ? Optional.of("free delivery over Rs 999") : Optional.empty());

    static String discount(Cart c) {
        return RULES.stream().map(r -> r.apply(c)).flatMap(Optional::stream).findFirst().orElse("no discount");   // (2)!
    }

    public static void main(String[] args) {
        System.out.println(discount(new Cart(40_000, true, "FRESH50")));
        System.out.println(discount(new Cart(40_000, false, "FRESH50")));
        System.out.println(discount(new Cart(120_000, false, null)));
    }
}
