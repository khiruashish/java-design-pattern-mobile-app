import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Minimal Flyweight: 10,000 products in a list, but only one badge object per category. */
public class CategoryBadgeDemo {

    enum Category { DAIRY, BAKERY, FRUIT, VEG }
    record Badge(String text, String colour, String icon) { }                                 // (1)!

    static final Map<Category, Badge> BADGES = new EnumMap<>(Map.of(                         // (2)!
            Category.DAIRY, new Badge("Dairy", "#2E86DE", "milk.svg"),
            Category.BAKERY, new Badge("Bakery", "#D35400", "bread.svg"),
            Category.FRUIT, new Badge("Fruit", "#27AE60", "apple.svg"),
            Category.VEG, new Badge("Veg", "#16A085", "leaf.svg")));

    record ProductTile(String name, Badge badge) { }

    public static void main(String[] args) {
        List<ProductTile> tiles = new ArrayList<>();
        Category[] cats = Category.values();
        for (int i = 0; i < 10_000; i++) tiles.add(new ProductTile("Product " + i, BADGES.get(cats[i % cats.length])));
        long distinct = tiles.stream().map(ProductTile::badge).distinct().count();
        System.out.println(tiles.size() + " tiles share " + distinct + " badge objects");
        System.out.println(tiles.get(2).name() + " shows " + tiles.get(2).badge());
    }
}
