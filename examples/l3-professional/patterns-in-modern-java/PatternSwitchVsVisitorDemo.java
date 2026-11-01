import java.util.List;

/** Pattern matching for switch (with record patterns and guards) replacing a classic Visitor. */
public class PatternSwitchVsVisitorDemo {

    // ---------- before: classic Visitor ----------
    interface LineVisitor<R> { R visitLoose(LooseV l); R visitPacked(PackedV p); }
    interface LineV { <R> R accept(LineVisitor<R> v); }
    record LooseV(String sku, int grams, long paisePerKg) implements LineV { public <R> R accept(LineVisitor<R> v) { return v.visitLoose(this); } }
    record PackedV(String sku, int qty, long unitPaise) implements LineV { public <R> R accept(LineVisitor<R> v) { return v.visitPacked(this); } }

    static final class PriceVisitor implements LineVisitor<Long> {                                     // (1)!
        public Long visitLoose(LooseV l) { return l.grams() * l.paisePerKg() / 1000; }
        public Long visitPacked(PackedV p) { return p.qty() * p.unitPaise(); }
    }

    // ---------- after: sealed records + switch ----------
    sealed interface Line permits Loose, Packed { }
    record Loose(String sku, int grams, long paisePerKg) implements Line { }
    record Packed(String sku, int qty, long unitPaise) implements Line { }

    static long price(Line line) {
        return switch (line) {
            case Loose(var sku, var grams, var perKg) -> grams * perKg / 1000;                        // (2)!
            case Packed(var sku, var qty, var unit) when qty >= 6 -> qty * unit * 95 / 100;           // (3)!
            case Packed(var sku, var qty, var unit) -> qty * unit;
        };
    }

    public static void main(String[] args) {
        List<LineV> old = List.of(new LooseV("TOMATO", 750, 4_000), new PackedV("MILK", 2, 6_400));
        System.out.println("visitor: " + old.stream().map(l -> l.accept(new PriceVisitor())).toList());
        List<Line> lines = List.of(new Loose("TOMATO", 750, 4_000), new Packed("MILK", 2, 6_400), new Packed("EGGS", 6, 1_000));
        System.out.println("switch:  " + lines.stream().map(PatternSwitchVsVisitorDemo::price).toList() + "  (6+ packs get 5% off)");
    }
}
