import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;

/** Custom iterators: walk a tree without recursion, and fetch pages from an API only when needed. */
public class CustomIteratorDemo {

    // ===================== Depth-first tree iterator with an explicit stack =====================
    record Category(String name, List<Category> subs) implements Iterable<String> {
        public Iterator<String> iterator() {
            Deque<Category> stack = new ArrayDeque<>(List.of(this));
            return new Iterator<>() {                                                          // (1)!
                public boolean hasNext() { return !stack.isEmpty(); }
                public String next() {
                    if (stack.isEmpty()) throw new NoSuchElementException();
                    Category c = stack.pop();
                    for (int i = c.subs().size() - 1; i >= 0; i--) stack.push(c.subs().get(i));
                    return c.name();
                }
            };
        }
    }

    // ===================== Lazy paged iterator: fetch the next page only when the current one runs out =====================
    static final class PagedIterator<T> implements Iterator<T> {
        private final IntFunction<List<T>> fetchPage; private int page = 0; private Iterator<T> current = List.<T>of().iterator(); private boolean done;
        int pagesFetched;
        PagedIterator(IntFunction<List<T>> fetchPage) { this.fetchPage = fetchPage; }
        public boolean hasNext() {
            while (!current.hasNext() && !done) {                                              // (2)!
                List<T> next = fetchPage.apply(page++); pagesFetched++;
                if (next.isEmpty()) done = true; else current = next.iterator();
            }
            return current.hasNext();
        }
        public T next() { if (!hasNext()) throw new NoSuchElementException(); return current.next(); }
    }

    public static void main(String[] args) {
        Category root = new Category("All", List.of(
                new Category("Fruits", List.of(new Category("Citrus", List.of()))),
                new Category("Dairy", List.of())));
        System.out.print("tree:");
        for (String name : root) System.out.print(" " + name);
        System.out.println();

        List<List<String>> server = List.of(List.of("FC-1", "FC-2"), List.of("FC-3", "FC-4"), List.of("FC-5"));
        PagedIterator<String> orders = new PagedIterator<>(p -> p < server.size() ? server.get(p) : List.of());
        String first3 = orders.next() + "," + orders.next() + "," + orders.next();             // (3)!
        System.out.println("first 3 orders: " + first3 + " -> pages fetched: " + orders.pagesFetched);
    }
}
