import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Realistic Composite: the app's category menu. Counts in-stock products per category, hides empty branches, renders the tree. */
public class CatalogMenuDemo {

    sealed interface MenuNode permits Product, Category { }
    record Product(String sku, String name) implements MenuNode { }
    record Category(String name, List<MenuNode> children) implements MenuNode { }

    static int inStock(MenuNode n, Map<String, Integer> stock) {                          // (1)!
        return switch (n) {
            case Product p -> stock.getOrDefault(p.sku(), 0) > 0 ? 1 : 0;
            case Category c -> c.children().stream().mapToInt(k -> inStock(k, stock)).sum();
        };
    }

    static List<String> render(MenuNode n, Map<String, Integer> stock, String indent) {   // (2)!
        List<String> lines = new ArrayList<>();
        switch (n) {
            case Product p -> { if (stock.getOrDefault(p.sku(), 0) > 0) lines.add(indent + "- " + p.name()); }
            case Category c -> {
                int count = inStock(c, stock);
                if (count == 0) break;                                                    // (3)!
                lines.add(indent + c.name() + " (" + count + ")");
                for (MenuNode k : c.children()) lines.addAll(render(k, stock, indent + "  "));
            }
        }
        return lines;
    }

    public static void main(String[] args) {
        MenuNode menu = new Category("FreshCart", List.of(
                new Category("Fruits & Veg", List.of(
                        new Category("Fresh fruit", List.of(new Product("F1", "Banana"), new Product("F2", "Mango"))),
                        new Category("Exotic", List.of(new Product("E1", "Dragon fruit"))))),
                new Category("Dairy", List.of(new Product("D1", "Milk 1L"), new Product("D2", "Curd 400g")))));
        Map<String, Integer> stockAtKoramangala = Map.of("F1", 40, "F2", 0, "E1", 0, "D1", 120, "D2", 8);   // (4)!
        render(menu, stockAtKoramangala, "").forEach(System.out::println);
    }
}
