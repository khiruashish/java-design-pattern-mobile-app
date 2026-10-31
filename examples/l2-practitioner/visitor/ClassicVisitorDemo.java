import java.util.List;

/** Classic GoF Visitor: each element has accept(visitor); each operation is a visitor with one method per element type. */
public class ClassicVisitorDemo {

    // ===================== Before: every operation repeats an instanceof chain =====================
    static long gstBefore(Object item) {
        if (item instanceof LooseProduce l) return 0;                                              // (1)!
        if (item instanceof Packaged p) return p.paise() * 5 / 100;
        throw new IllegalArgumentException("forgot a type: " + item);
    }

    // ===================== After: visitors =====================
    interface ItemVisitor<R> { R visitLoose(LooseProduce l); R visitPackaged(Packaged p); }        // (2)!
    interface Item { <R> R accept(ItemVisitor<R> v); }

    record LooseProduce(String name, int grams, long paisePerKg) implements Item {
        public <R> R accept(ItemVisitor<R> v) { return v.visitLoose(this); }
    }
    record Packaged(String name, long paise, int gstPercent) implements Item {
        public <R> R accept(ItemVisitor<R> v) { return v.visitPackaged(this); }
    }

    static final class GstVisitor implements ItemVisitor<Long> {                                   // (3)!
        public Long visitLoose(LooseProduce l) { return 0L; }                                       // fresh produce: 0% GST
        public Long visitPackaged(Packaged p) { return p.paise() * p.gstPercent() / 100; }
    }
    static final class ShippingWeightVisitor implements ItemVisitor<Integer> {                     // (4)!
        public Integer visitLoose(LooseProduce l) { return l.grams() + 10; }                         // bag
        public Integer visitPackaged(Packaged p) { return p.name().contains("5kg") ? 5_000 : 500; }
    }

    public static void main(String[] args) {
        System.out.println("before: gst " + gstBefore(new Packaged("Atta 5kg", 25_000, 5)));
        List<Item> cart = List.of(new LooseProduce("Tomatoes", 750, 4_000), new Packaged("Atta 5kg", 25_000, 5), new Packaged("Chips", 2_000, 12));
        long gst = cart.stream().mapToLong(i -> i.accept(new GstVisitor())).sum();
        int grams = cart.stream().mapToInt(i -> i.accept(new ShippingWeightVisitor())).sum();
        System.out.printf("GST Rs %.2f, shipping weight %d g%n", gst / 100.0, grams);
    }
}
