import java.util.List;

/** Acyclic Visitor: one small interface per element type; visitors implement only the ones they care about. New types don't break old visitors. */
public class AcyclicVisitorDemo {

    interface Visitor { }                                                                           // (1)!
    interface LooseVisitor extends Visitor { void visit(LooseProduce l); }
    interface PackagedVisitor extends Visitor { void visit(Packaged p); }
    interface GiftCardVisitor extends Visitor { void visit(GiftCard g); }

    interface Item { void accept(Visitor v); }
    record LooseProduce(String name, int grams) implements Item {
        public void accept(Visitor v) { if (v instanceof LooseVisitor lv) lv.visit(this); }         // (2)!
    }
    record Packaged(String name, int grams) implements Item {
        public void accept(Visitor v) { if (v instanceof PackagedVisitor pv) pv.visit(this); }
    }
    record GiftCard(long paise) implements Item {                                                   // added later
        public void accept(Visitor v) { if (v instanceof GiftCardVisitor gv) gv.visit(this); }
    }

    /** Written before GiftCard existed; still compiles and works. */
    static final class WeightVisitor implements LooseVisitor, PackagedVisitor {                     // (3)!
        int grams;
        public void visit(LooseProduce l) { grams += l.grams(); }
        public void visit(Packaged p) { grams += p.grams(); }
    }

    public static void main(String[] args) {
        List<Item> cart = List.of(new LooseProduce("Onions", 1_000), new Packaged("Rice 5kg", 5_000), new GiftCard(50_000));
        WeightVisitor w = new WeightVisitor();
        cart.forEach(i -> i.accept(w));
        System.out.println("weight " + w.grams + " g (the gift card was skipped: WeightVisitor doesn't implement GiftCardVisitor)");
    }
}
