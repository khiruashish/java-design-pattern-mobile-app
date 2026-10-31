import java.util.ArrayList;
import java.util.List;

/** Minimal Composite: a delivery box holds items and bags; bags hold items and smaller bags. Weight works the same at every level. */
public class DeliveryBoxDemo {

    interface Packable { int grams(); }                                                    // (1)!
    record Product(String name, int grams) implements Packable { }
    record Bag(String label, List<Packable> contents) implements Packable {
        public int grams() { return 15 + contents.stream().mapToInt(Packable::grams).sum(); }   // (2)!
    }

    public static void main(String[] args) {
        Bag cold = new Bag("cold bag", List.of(new Product("Milk 1L", 1030), new Product("Paneer", 200)));
        Bag box = new Bag("box", new ArrayList<>(List.of(cold, new Product("Rice 5kg", 5000), new Bag("veg bag", List.of(new Product("Tomatoes", 500))))));
        System.out.println("cold bag: " + cold.grams() + " g");
        System.out.println("whole box: " + box.grams() + " g" + (box.grams() > 6000 ? " -> needs a scooter with a top box" : ""));
    }
}
