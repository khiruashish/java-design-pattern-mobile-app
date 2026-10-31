import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Typical recursive operations on a composite tree: totals, search with path, printing, and an iterative walk for deep trees. */
public class RecursiveOperationsDemo {

    static final class Category {
        final String name; final List<Category> subs = new ArrayList<>(); final List<String> products = new ArrayList<>();
        Category(String name, String... products) { this.name = name; this.products.addAll(List.of(products)); }
        Category sub(Category c) { subs.add(c); return this; }

        int productCount() {                                                               // (1)!
            int n = products.size();
            for (Category c : subs) n += c.productCount();
            return n;
        }

        Optional<List<String>> pathTo(String product) {                                  // (2)!
            if (products.contains(product)) return Optional.of(new ArrayList<>(List.of(name)));
            for (Category c : subs) {
                Optional<List<String>> p = c.pathTo(product);
                if (p.isPresent()) { p.get().add(0, name); return p; }
            }
            return Optional.empty();
        }

        void print(String indent) {                                                        // (3)!
            System.out.println(indent + name + " (" + productCount() + ")");
            for (Category c : subs) c.print(indent + "  ");
        }

        int maxDepthIterative() {                                                          // (4)!
            record Frame(Category c, int depth) { }
            Deque<Frame> stack = new ArrayDeque<>(List.of(new Frame(this, 1)));
            int max = 0;
            while (!stack.isEmpty()) {
                Frame f = stack.pop();
                max = Math.max(max, f.depth());
                for (Category c : f.c().subs) stack.push(new Frame(c, f.depth() + 1));
            }
            return max;
        }
    }

    public static void main(String[] args) {
        Category root = new Category("All")
                .sub(new Category("Fruits", "Banana", "Apple").sub(new Category("Citrus", "Orange", "Lemon")))
                .sub(new Category("Dairy", "Milk").sub(new Category("Cheese", "Paneer", "Cheddar")));
        root.print("");
        System.out.println("path to Lemon: " + String.join(" > ", root.pathTo("Lemon").orElseThrow()));
        System.out.println("path to Bread: " + root.pathTo("Bread").map(Object::toString).orElse("not found"));

        Category deep = new Category("level-1"), cur = deep;
        for (int i = 2; i <= 200_000; i++) { Category next = new Category("level-" + i); cur.sub(next); cur = next; }
        try { deep.productCount(); System.out.println("recursion survived"); }
        catch (StackOverflowError e) { System.out.println("recursive walk on 200,000 levels: StackOverflowError"); }
        System.out.println("iterative walk on 200,000 levels: depth " + deep.maxDepthIterative());
    }
}
