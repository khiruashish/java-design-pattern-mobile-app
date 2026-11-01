import java.util.Map;
import java.util.function.Function;

/** Golden hammer: using a favourite tool (here, "more patterns") for every problem, whether it fits or not. */
public class GoldenHammerDemo {

    // ---- Before: GST calculation via interface + factory + registry + strategy, for a single multiplication. ----
    interface TaxStrategy { long apply(long paise); }                        // (1)!
    interface TaxStrategyFactory { TaxStrategy create(String category); }
    static final class DefaultTaxStrategyFactory implements TaxStrategyFactory {
        private final Map<String, Function<Void, TaxStrategy>> registry = Map.of(
                "FOOD", v -> p -> p * 5 / 100,
                "OTHER", v -> p -> p * 18 / 100);
        public TaxStrategy create(String category) { return registry.get(category).apply(null); }
    }
    static final class TaxStrategyFactoryProvider { static TaxStrategyFactory get() { return new DefaultTaxStrategyFactory(); } }

    // ---- After: the rule is data; the code is one method. ----
    enum GstRate {                                                           // (2)!
        FOOD(5), OTHER(18);
        final int percent;
        GstRate(int percent) { this.percent = percent; }
        long taxOn(long paise) { return paise * percent / 100; }
    }

    public static void main(String[] args) {
        long before = TaxStrategyFactoryProvider.get().create("FOOD").apply(20_000);
        long after = GstRate.FOOD.taxOn(20_000);                             // (3)!
        System.out.println("before: " + before + " (5 types, 1 lambda registry), after: " + after + " (1 enum)");
        System.out.println("same for OTHER: " + (TaxStrategyFactoryProvider.get().create("OTHER").apply(20_000) == GstRate.OTHER.taxOn(20_000)));
    }
}
