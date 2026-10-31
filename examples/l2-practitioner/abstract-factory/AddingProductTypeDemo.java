import java.util.List;

/**
 * The known weakness of Abstract Factory: adding a NEW product type touches the interface and every factory.
 * Shown: (a) the compiler forces every family to decide; (b) a default method avoids that but can silently mismatch.
 */
public class AddingProductTypeDemo {

    interface TaxCalculator { String label(); }
    interface MoneyFormatter { String format(long cents); }
    interface InvoiceNumbering { String next(int seq); }                                    // the NEW product type

    interface RegionFactory {
        TaxCalculator tax();
        MoneyFormatter money();
        InvoiceNumbering invoices();                                                       // (1)!
    }

    static final class IndiaFactory implements RegionFactory {
        public TaxCalculator tax() { return () -> "GST"; }
        public MoneyFormatter money() { return c -> "INR " + c / 100; }
        public InvoiceNumbering invoices() { return n -> "FC/IN/2026-27/" + String.format("%06d", n); }   // GST invoice series by financial year
    }
    static final class UaeFactory implements RegionFactory {
        public TaxCalculator tax() { return () -> "VAT"; }
        public MoneyFormatter money() { return c -> "AED " + c / 100; }
        public InvoiceNumbering invoices() { return n -> "FC-AE-" + n; }
    }

    // ===================== The tempting shortcut: a default product =====================
    interface RegionFactoryWithDefault {
        TaxCalculator tax();
        default InvoiceNumbering invoices() { return n -> "FC-" + n; }                     // (2)!
    }
    static final class SaudiFactory implements RegionFactoryWithDefault {
        public TaxCalculator tax() { return () -> "VAT 15%"; }                              // forgot invoices(): compiles anyway
    }

    public static void main(String[] args) {
        for (RegionFactory f : List.of(new IndiaFactory(), new UaeFactory())) {
            System.out.println(f.getClass().getSimpleName() + ": " + f.tax().label() + " invoice " + f.invoices().next(42));
        }
        RegionFactoryWithDefault saudi = new SaudiFactory();
        System.out.println("SaudiFactory: " + saudi.tax().label() + " invoice " + saudi.invoices().next(42) + "   <- generic number, probably not compliant");   // (3)!
    }
}
