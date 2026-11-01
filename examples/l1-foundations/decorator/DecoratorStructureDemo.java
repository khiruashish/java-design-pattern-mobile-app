/** Decorator structure: decorators implement the same interface as what they wrap, and add one behaviour each. */
public class DecoratorStructureDemo {

    interface PriceCalculator { long priceCents(String sku, int qty); }                  // (1)!

    record ListPrice(java.util.Map<String, Long> prices) implements PriceCalculator {    // the concrete component
        public long priceCents(String sku, int qty) { return prices.get(sku) * qty; }
    }

    record MemberDiscount(PriceCalculator inner, int percent) implements PriceCalculator {   // (2)!
        public long priceCents(String sku, int qty) {
            long p = inner.priceCents(sku, qty);                                          // (3)!
            return p - p * percent / 100;
        }
    }

    record Gst(PriceCalculator inner, int percent) implements PriceCalculator {
        public long priceCents(String sku, int qty) { long p = inner.priceCents(sku, qty); return p + p * percent / 100; }
    }

    public static void main(String[] args) {
        PriceCalculator base = new ListPrice(java.util.Map.of("OIL-1L", 18_900L));
        PriceCalculator member = new MemberDiscount(base, 10);
        PriceCalculator memberWithTax = new Gst(member, 5);                               // (4)!
        System.out.println("list:            " + base.priceCents("OIL-1L", 2));
        System.out.println("member:          " + member.priceCents("OIL-1L", 2));
        System.out.println("member + 5% GST: " + memberWithTax.priceCents("OIL-1L", 2));
    }
}
