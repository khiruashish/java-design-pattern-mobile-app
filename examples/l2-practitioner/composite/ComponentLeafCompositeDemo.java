import java.util.ArrayList;
import java.util.List;

/** Component, leaf and composite: a combo pack can contain items AND other packs, and callers treat both alike. */
public class ComponentLeafCompositeDemo {

    // ===================== Before: the caller must know every shape =====================
    record PlainItem(String name, long paise) { }
    record PlainPack(String name, List<Object> contents) { }

    static long priceBefore(Object o) {                                                   // (1)!
        if (o instanceof PlainItem i) return i.paise();
        if (o instanceof PlainPack p) { long sum = 0; for (Object c : p.contents()) sum += priceBefore(c); return sum; }
        throw new IllegalArgumentException("unknown thing " + o);
    }

    // ===================== After: one Component interface =====================
    interface CartEntry { String name(); long paise(); }                                   // (2)!

    record Item(String name, long paise) implements CartEntry { }                          // (3)!

    static final class Pack implements CartEntry {                                        // (4)!
        private final String name; private final List<CartEntry> children = new ArrayList<>();
        Pack(String name) { this.name = name; }
        Pack add(CartEntry e) { children.add(e); return this; }
        public String name() { return name; }
        public long paise() { return children.stream().mapToLong(CartEntry::paise).sum(); }
    }

    public static void main(String[] args) {
        System.out.println("before: " + priceBefore(new PlainPack("combo", List.of(new PlainItem("bread", 4500), new PlainItem("eggs", 8400)))));

        Pack breakfast = new Pack("Breakfast pack").add(new Item("Bread", 4500)).add(new Item("Eggs x6", 8400));
        Pack weekly = new Pack("Weekly box").add(breakfast).add(new Item("Rice 5kg", 42000)).add(new Pack("Fruit bag").add(new Item("Bananas", 6000)));
        List<CartEntry> cart = List.of(new Item("Milk 1L", 6800), weekly);
        for (CartEntry e : cart) System.out.printf("%-12s Rs %.2f%n", e.name(), e.paise() / 100.0);
        System.out.printf("cart total   Rs %.2f%n", cart.stream().mapToLong(CartEntry::paise).sum() / 100.0);
    }
}
