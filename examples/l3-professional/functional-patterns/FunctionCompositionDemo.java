import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Compose small functions into a pipeline with andThen / compose instead of nesting calls or growing one big method. */
public class FunctionCompositionDemo {

    record Cart(long subtotalPaise, boolean member, String coupon) { }

    // ---------- before: one method that does everything, in an order you must read carefully ----------
    static long priceBefore(Cart c) {                                                                  // (1)!
        long p = c.subtotalPaise();
        if (c.member()) p = p * 95 / 100;
        if ("FRESH10".equals(c.coupon())) p = p - Math.min(p / 10, 10_000);
        p = p + (p >= 49_900 ? 0 : 2_500);
        return p + p * 5 / 100;
    }

    // ---------- after: each rule is a function; the pipeline is a composition ----------
    record Priced(Cart cart, long paise) { }
    static final UnaryOperator<Priced> MEMBER_DISCOUNT = p -> p.cart().member() ? new Priced(p.cart(), p.paise() * 95 / 100) : p;
    static final UnaryOperator<Priced> COUPON = p -> "FRESH10".equals(p.cart().coupon()) ? new Priced(p.cart(), p.paise() - Math.min(p.paise() / 10, 10_000)) : p;
    static final UnaryOperator<Priced> DELIVERY = p -> new Priced(p.cart(), p.paise() + (p.paise() >= 49_900 ? 0 : 2_500));
    static final UnaryOperator<Priced> TAX = p -> new Priced(p.cart(), p.paise() + p.paise() * 5 / 100);

    static final Function<Cart, Long> PRICE =                                                           // (2)!
            ((Function<Cart, Priced>) c -> new Priced(c, c.subtotalPaise()))
                    .andThen(MEMBER_DISCOUNT).andThen(COUPON).andThen(DELIVERY).andThen(TAX)
                    .andThen(Priced::paise);

    static Function<Cart, Long> pipeline(List<UnaryOperator<Priced>> steps) {                          // (3)!
        Function<Priced, Priced> all = steps.stream().map(s -> (Function<Priced, Priced>) s).reduce(Function.identity(), Function::andThen);
        return c -> all.apply(new Priced(c, c.subtotalPaise())).paise();
    }

    public static void main(String[] args) {
        Cart cart = new Cart(52_000, true, "FRESH10");
        System.out.println("before:   " + priceBefore(cart));
        System.out.println("composed: " + PRICE.apply(cart));
        System.out.println("festival config (no tax step, for a tax-free day): " + pipeline(List.of(MEMBER_DISCOUNT, COUPON, DELIVERY)).apply(cart));
    }
}
