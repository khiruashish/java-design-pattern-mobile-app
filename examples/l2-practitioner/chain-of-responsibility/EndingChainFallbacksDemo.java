import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Every chain needs a defined ending: what happens when no handler takes the request? Here: a fallback chain for prices. */
public class EndingChainFallbacksDemo {

    interface PriceSource { Optional<Long> price(String sku); String name(); }

    record Source(String name, Function<String, Optional<Long>> lookup) implements PriceSource {
        public Optional<Long> price(String sku) { return lookup.apply(sku); }
    }

    static String lookup(List<PriceSource> chain, String sku) {
        for (PriceSource s : chain) {                                                              // (1)!
            try {
                Optional<Long> p = s.price(sku);
                if (p.isPresent()) return sku + " = Rs " + p.get() / 100 + " (from " + s.name() + ")";
            } catch (RuntimeException e) {
                System.out.println("  " + s.name() + " failed: " + e.getMessage() + ", trying next");   // (2)!
            }
        }
        return sku + " = price unavailable, item hidden from the app";                              // (3)!
    }

    public static void main(String[] args) {
        List<PriceSource> chain = List.of(
                new Source("cache", sku -> sku.equals("MILK-1L") ? Optional.of(6_800L) : Optional.empty()),
                new Source("database", sku -> { if (sku.equals("RICE-5KG")) throw new IllegalStateException("db timeout"); return sku.startsWith("BREAD") ? Optional.of(4_500L) : Optional.empty(); }),
                new Source("GreenValley supplier", sku -> sku.startsWith("MANGO") ? Optional.of(18_000L) : Optional.empty()));
        for (String sku : List.of("MILK-1L", "BREAD-WHITE", "MANGO-1KG", "RICE-5KG")) System.out.println(lookup(chain, sku));
    }
}
