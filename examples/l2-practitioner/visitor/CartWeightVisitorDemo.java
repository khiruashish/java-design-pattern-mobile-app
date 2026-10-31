import java.util.List;

/** Minimal Visitor: add a "total weight" operation over cart items without adding a method to every item class. */
public class CartWeightVisitorDemo {

    sealed interface Item permits Loose, Packaged { }
    record Loose(String name, int grams) implements Item { }
    record Packaged(String name, int netGrams, int packagingGrams) implements Item { }

    static int weight(Item i) {                                                                     // (1)!
        return switch (i) {
            case Loose l -> l.grams();
            case Packaged p -> p.netGrams() + p.packagingGrams();                                   // (2)!
        };
    }

    public static void main(String[] args) {
        List<Item> cart = List.of(new Loose("Potatoes", 2_000), new Packaged("Milk 1L", 1_030, 20), new Packaged("Bread", 400, 15));
        System.out.println("total " + cart.stream().mapToInt(CartWeightVisitorDemo::weight).sum() + " g");
    }
}
