import java.util.List;

/** Null Object: instead of passing null for "no discount", pass an object that implements the interface and does nothing. */
public class NullObjectStructureDemo {

    record Line(String sku, long paise) { }

    // ---------- before: every caller must remember the null check ----------
    interface Discount { long apply(long subtotal); String label(); }
    record PercentOff(int percent) implements Discount {
        public long apply(long s) { return s - s * percent / 100; }
        public String label() { return percent + "% off"; }
    }

    static long totalBefore(List<Line> lines, Discount discount) {
        long subtotal = lines.stream().mapToLong(Line::paise).sum();
        return discount == null ? subtotal : discount.apply(subtotal);                              // (1)!
    }

    static String receiptBefore(Discount discount) {
        return "discount: " + discount.label();                                                       // forgot the check here
    }

    // ---------- after: a do-nothing implementation ----------
    enum NoDiscount implements Discount {                                                             // (2)!
        INSTANCE;
        public long apply(long subtotal) { return subtotal; }
        public String label() { return "none"; }
    }

    static long total(List<Line> lines, Discount discount) {
        return discount.apply(lines.stream().mapToLong(Line::paise).sum());                           // (3)!
    }

    public static void main(String[] args) {
        List<Line> cart = List.of(new Line("MILK-1L", 6_400), new Line("BREAD", 4_500));
        System.out.println("before, 10% off: " + totalBefore(cart, new PercentOff(10)));
        System.out.println("before, no discount: " + totalBefore(cart, null));
        try { receiptBefore(null); } catch (NullPointerException e) { System.out.println("before, receipt: NullPointerException"); }

        System.out.println("after, 10% off: " + total(cart, new PercentOff(10)) + ", label " + new PercentOff(10).label());
        System.out.println("after, no discount: " + total(cart, NoDiscount.INSTANCE) + ", label " + NoDiscount.INSTANCE.label());
    }
}
