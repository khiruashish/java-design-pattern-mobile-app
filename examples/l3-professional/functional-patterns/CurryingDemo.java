import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Currying turns f(a, b) into a -> (b -> ...); partial application fixes some arguments now and supplies the rest later. */
public class CurryingDemo {

    static final BiFunction<Integer, Long, Long> addTax = (ratePercent, paise) -> paise + paise * ratePercent / 100;

    static final Function<Integer, Function<Long, Long>> addTaxCurried = rate -> paise -> paise + paise * rate / 100;   // (1)!

    static <A, B, R> Function<B, R> partial(BiFunction<A, B, R> f, A a) { return b -> f.apply(a, b); }   // (2)!

    static final Function<Long, Function<Double, Function<Boolean, Long>>> deliveryFee =                 // (3)!
            base -> perKm -> raining -> base + Math.round(perKm * (raining ? 1.5 : 1.0) * 5);

    public static void main(String[] args) {
        Function<Long, Long> gst5 = addTaxCurried.apply(5);
        Function<Long, Long> gst18 = partial(addTax, 18);
        System.out.println("groceries at 5%:  " + List.of(10_000L, 25_000L).stream().map(gst5).toList());
        System.out.println("services at 18%: " + List.of(10_000L, 25_000L).stream().map(gst18).toList());

        Function<Boolean, Long> puneFee = deliveryFee.apply(2_000L).apply(600.0);
        System.out.println("Pune 5 km, dry: " + puneFee.apply(false) + ", raining: " + puneFee.apply(true));
    }
}
