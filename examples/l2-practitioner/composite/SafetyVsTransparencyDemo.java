import java.util.ArrayList;
import java.util.List;

/** Where do add()/remove() live? On the component (transparent) or only on the composite (safe)? */
public class SafetyVsTransparencyDemo {

    // ===================== Transparent: every node has add(), leaves reject it at runtime =====================
    interface TNode {
        String name();
        default void add(TNode child) { throw new UnsupportedOperationException(name() + " can't have children"); }   // (1)!
        default List<TNode> children() { return List.of(); }
    }
    record TItem(String name) implements TNode { }
    static final class TCategory implements TNode {
        final String name; final List<TNode> kids = new ArrayList<>();
        TCategory(String name) { this.name = name; }
        public String name() { return name; }
        @Override public void add(TNode c) { kids.add(c); }
        @Override public List<TNode> children() { return kids; }
    }

    // ===================== Safe: only the composite has add(); the compiler stops the mistake =====================
    interface SNode { String name(); }
    record SItem(String name) implements SNode { }
    static final class SCategory implements SNode {
        final String name; final List<SNode> kids = new ArrayList<>();
        SCategory(String name) { this.name = name; }
        public String name() { return name; }
        void add(SNode c) { kids.add(c); }                                                // (2)!
    }

    static int countTransparent(TNode n) {                                               // (3)!
        int c = 1; for (TNode k : n.children()) c += countTransparent(k); return c;
    }

    public static void main(String[] args) {
        TNode dairy = new TCategory("Dairy");
        dairy.add(new TItem("Milk"));
        dairy.add(new TItem("Curd"));
        System.out.println("transparent: generic code works on any node, count = " + countTransparent(dairy));
        try { new TItem("Milk").add(new TItem("Cheese")); }
        catch (UnsupportedOperationException e) { System.out.println("transparent: mistake found at RUNTIME -> " + e.getMessage()); }

        SCategory bakery = new SCategory("Bakery");
        bakery.add(new SItem("Bread"));
        // new SItem("Bread").add(...) does not compile: the mistake is found at COMPILE time   // (4)!
        System.out.println("safe: " + bakery.name() + " has " + bakery.kids.size() + " child; leaves have no add() at all");
    }
}
