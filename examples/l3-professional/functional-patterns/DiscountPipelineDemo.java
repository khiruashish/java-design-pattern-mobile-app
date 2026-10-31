import java.util.List;
import java.util.function.UnaryOperator;

/** Minimal: QuickDrop's active discounts are a list of functions, folded into one with andThen. */
public class DiscountPipelineDemo {

    static UnaryOperator<Long> percentOff(int pct) { return p -> p - p * pct / 100; }
    static UnaryOperator<Long> flatOff(long paise) { return p -> Math.max(0, p - paise); }

    public static void main(String[] args) {
        List<UnaryOperator<Long>> active = List.of(percentOff(10), flatOff(5_000));                    // (1)!
        UnaryOperator<Long> all = active.stream().reduce(UnaryOperator.identity(), (a, b) -> x -> b.apply(a.apply(x)));   // (2)!
        for (long cart : new long[] {30_000, 80_000, 4_000})
            System.out.println("Rs " + cart / 100 + " -> Rs " + all.apply(cart) / 100.0);
    }
}
