import java.util.List;

/** Modern Composite: a sealed interface + records. The compiler knows every node type, so switches are exhaustive. */
public class SealedCompositeDemo {

    sealed interface Node permits Item, Bundle, Discount { }                               // (1)!
    record Item(String name, long paise) implements Node { }
    record Bundle(String name, List<Node> children) implements Node {
        Bundle { children = List.copyOf(children); }                                      // (2)!
    }
    record Discount(int percent, Node target) implements Node { }

    static long price(Node n) {                                                            // (3)!
        return switch (n) {
            case Item i -> i.paise();
            case Bundle b -> b.children().stream().mapToLong(SealedCompositeDemo::price).sum();
            case Discount d -> price(d.target()) * (100 - d.percent()) / 100;
        };
    }

    static String describe(Node n) {
        return switch (n) {
            case Item(String name, long p) -> name;
            case Bundle(String name, List<Node> kids) -> name + kids.stream().map(SealedCompositeDemo::describe).toList();
            case Discount(int pct, Node t) -> pct + "% off " + describe(t);
        };
    }

    public static void main(String[] args) {
        Node party = new Bundle("Party pack", List.of(
                new Item("Chips", 3000),
                new Discount(20, new Bundle("Drinks", List.of(new Item("Cola 2L", 9500), new Item("Soda 2L", 8500)))),
                new Item("Cake", 45000)));
        System.out.println(describe(party));
        System.out.printf("price: Rs %.2f%n", price(party) / 100.0);
        // Add a new record to `permits` and both switches stop compiling until you handle it.   // (4)!
    }
}
