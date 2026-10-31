import java.util.ArrayList;
import java.util.List;

/** Shallow copy shares inner objects; deep copy duplicates them. The difference shows up when someone mutates. */
public class ShallowDeepCopyDemo {

    static final class Line {
        String sku; int qty;
        Line(String sku, int qty) { this.sku = sku; this.qty = qty; }
        Line copy() { return new Line(sku, qty); }
        public String toString() { return sku + "x" + qty; }
    }

    static final class Basket {
        String owner; List<Line> lines;
        Basket(String owner, List<Line> lines) { this.owner = owner; this.lines = lines; }

        Basket shallowCopy() { return new Basket(owner, lines); }                          // (1)!

        Basket deepCopy() {                                                                // (2)!
            List<Line> copied = new ArrayList<>();
            for (Line l : lines) copied.add(l.copy());
            return new Basket(owner, copied);
        }
        public String toString() { return owner + lines; }
    }

    public static void main(String[] args) {
        Basket template = new Basket("template", new ArrayList<>(List.of(new Line("MILK", 2), new Line("BREAD", 1))));

        Basket shallow = template.shallowCopy();
        shallow.owner = "asha";
        shallow.lines.get(0).qty = 5;                                                      // (3)!
        shallow.lines.add(new Line("EGGS", 1));
        System.out.println("after editing the SHALLOW copy, template = " + template);

        Basket fresh = new Basket("template", new ArrayList<>(List.of(new Line("MILK", 2), new Line("BREAD", 1))));
        Basket deep = fresh.deepCopy();
        deep.owner = "ravi";
        deep.lines.get(0).qty = 5;
        deep.lines.add(new Line("EGGS", 1));
        System.out.println("after editing the DEEP copy,    template = " + fresh + ", copy = " + deep);
    }
}
