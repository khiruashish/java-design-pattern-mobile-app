import java.util.List;

/** Abstract Factory: one factory per region creates a FAMILY of objects that must be used together. */
public class ProductFamiliesDemo {

    // ===================== Abstract products =====================
    interface TaxCalculator { long taxCents(long netCents); String label(); }
    interface PhoneValidator { boolean valid(String phone); }
    interface MoneyFormatter { String format(long cents); }

    // ===================== Abstract factory =====================
    interface RegionFactory {                                                              // (1)!
        TaxCalculator tax();
        PhoneValidator phones();
        MoneyFormatter money();
    }

    // ===================== Family 1: India =====================
    static final class IndiaFactory implements RegionFactory {
        public TaxCalculator tax() { return new TaxCalculator() {
            public long taxCents(long net) { return net * 5 / 100; } public String label() { return "GST 5%"; } }; }
        public PhoneValidator phones() { return p -> p.matches("\\+91[6-9]\\d{9}"); }
        public MoneyFormatter money() { return c -> "INR " + c / 100 + "." + String.format("%02d", c % 100); }
    }

    // ===================== Family 2: UAE =====================
    static final class UaeFactory implements RegionFactory {
        public TaxCalculator tax() { return new TaxCalculator() {
            public long taxCents(long net) { return net * 5 / 100; } public String label() { return "VAT 5%"; } }; }
        public PhoneValidator phones() { return p -> p.matches("\\+9715\\d{8}"); }
        public MoneyFormatter money() { return c -> "AED " + c / 100 + "." + String.format("%02d", c % 100); }
    }

    /** Client code: depends only on the abstract factory, so it can never mix India tax with UAE money. */
    static String receipt(RegionFactory region, long netCents, String phone) {            // (2)!
        TaxCalculator tax = region.tax();
        MoneyFormatter money = region.money();
        return money.format(netCents) + " + " + tax.label() + " " + money.format(tax.taxCents(netCents))
                + " = " + money.format(netCents + tax.taxCents(netCents)) + ", phone ok: " + region.phones().valid(phone);
    }

    public static void main(String[] args) {
        for (var entry : List.of(java.util.Map.entry(new IndiaFactory(), "+919876543210"), java.util.Map.entry(new UaeFactory(), "+971501234567"))) {
            System.out.println(entry.getKey().getClass().getSimpleName() + ": " + receipt(entry.getKey(), 120_000, entry.getValue()));   // (3)!
        }
    }
}
