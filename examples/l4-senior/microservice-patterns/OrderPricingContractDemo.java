import java.util.Map;

/** Minimal: Ordering calls Pricing through a small, versioned contract, never through Pricing's internal model. */
public class OrderPricingContractDemo {

    // ----- the contract both teams agree on (published, versioned) -----
    record PriceQuoteV1(String sku, long unitPricePaise, String currency) {}              // (1)!
    interface PricingApi { PriceQuoteV1 quote(String sku); }

    // ----- Pricing's internal model can change freely -----
    record PriceRule(String sku, long basePaise, int promoPercent, String region) {}
    static final class PricingService implements PricingApi {
        private final Map<String, PriceRule> rules = Map.of(
                "MILK-1L", new PriceRule("MILK-1L", 6800, 0, "BLR"),
                "CURD-500G", new PriceRule("CURD-500G", 4500, 10, "BLR"));
        public PriceQuoteV1 quote(String sku) {
            PriceRule r = rules.get(sku);
            return new PriceQuoteV1(sku, r.basePaise() * (100 - r.promoPercent()) / 100, "INR");
        }
    }

    // ----- Ordering only depends on the contract -----
    static long orderTotalPaise(PricingApi pricing, Map<String, Integer> lines) {          // (2)!
        return lines.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .mapToLong(e -> pricing.quote(e.getKey()).unitPricePaise() * e.getValue()).sum();
    }

    public static void main(String[] args) {
        PricingApi pricing = new PricingService();
        long total = orderTotalPaise(pricing, Map.of("MILK-1L", 2, "CURD-500G", 1));
        System.out.println("CURD-500G quote: " + pricing.quote("CURD-500G"));
        System.out.printf("order total: Rs %d.%02d%n", total / 100, total % 100);
        PricingApi stub = sku -> new PriceQuoteV1(sku, 1000, "INR");
        System.out.println("with a stub (Ordering's own tests): Rs " + orderTotalPaise(stub, Map.of("MILK-1L", 3)) / 100);
    }
}
