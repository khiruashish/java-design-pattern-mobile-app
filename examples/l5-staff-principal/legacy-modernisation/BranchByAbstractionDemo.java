import java.util.List;

/**
 * Branch by abstraction: replace a component on trunk, in small safe steps, without a long-lived branch.
 * 1) put an abstraction in front of the old implementation, 2) move callers onto it, 3) build the new
 * implementation behind it, 4) switch with a toggle, 5) delete the old implementation and maybe the abstraction.
 */
public class BranchByAbstractionDemo {

    interface TaxCalculator { long gstPaise(String hsnCode, long pricePaise); }                         // (1)!

    /** The legacy code, untouched: one GST rate for everything except a hard-coded list. */
    static final class LegacyTaxTable implements TaxCalculator {
        public long gstPaise(String hsn, long price) { return hsn.equals("0401") ? 0 : price * 18 / 100; }
    }

    /** The new implementation: rate slabs per HSN chapter, built behind the same abstraction. */
    static final class GstSlabCalculator implements TaxCalculator {
        public long gstPaise(String hsn, long price) {
            int rate = switch (hsn.substring(0, 2)) { case "04" -> hsn.equals("0401") ? 0 : 5; case "19" -> 18; default -> 12; };
            return price * rate / 100;
        }
    }

    static boolean useNewTax = false;                                                                    // (2)!
    static TaxCalculator tax() { return useNewTax ? new GstSlabCalculator() : new LegacyTaxTable(); }

    static String invoiceLine(String item, String hsn, long price) { return item + " GST Rs " + tax().gstPaise(hsn, price) / 100.0; }   // (3)!

    public static void main(String[] args) {
        List<String[]> items = List.of(new String[] {"Milk 1L", "0401", "6800"}, new String[] {"Paneer 200g", "0406", "9500"},
                new String[] {"Biscuits", "1905", "3000"});
        for (boolean flag : new boolean[] {false, true}) {
            useNewTax = flag;
            System.out.println("toggle " + (flag ? "on " : "off") + ": " + items.stream().map(i -> invoiceLine(i[0], i[1], Long.parseLong(i[2]))).toList());
        }
        System.out.println("both implementations lived on trunk; step 5 deletes LegacyTaxTable and the toggle");
    }
}
