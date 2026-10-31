import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Spliterator is the iterator behind streams: it can split for parallelism and reports characteristics. */
public class SpliteratorStreamsDemo {

    /** A paged source exposed as a Spliterator so it can feed a Stream lazily. */
    static final class PageSpliterator extends Spliterators.AbstractSpliterator<String> {
        private final List<List<String>> pages; private int page; private int index; final List<Integer> fetched = new ArrayList<>();
        PageSpliterator(List<List<String>> pages) { super(Long.MAX_VALUE, ORDERED | NONNULL); this.pages = pages; }   // (1)!
        @Override public boolean tryAdvance(Consumer<? super String> action) {                  // (2)!
            while (page < pages.size()) {
                if (index == 0) fetched.add(page);
                List<String> p = pages.get(page);
                if (index < p.size()) { action.accept(p.get(index++)); return true; }
                page++; index = 0;
            }
            return false;
        }
    }

    public static void main(String[] args) {
        List<List<String>> pages = List.of(List.of("MILK", "BREAD"), List.of("EGGS", "RICE"), List.of("ATTA", "DAL"));
        PageSpliterator source = new PageSpliterator(pages);
        List<String> firstThree = StreamSupport.stream(source, false).limit(3).toList();        // (3)!
        System.out.println("limit(3): " + firstThree + ", pages fetched: " + source.fetched);

        Spliterator<Integer> whole = List.of(1, 2, 3, 4, 5, 6, 7, 8).spliterator();
        Spliterator<Integer> half = whole.trySplit();                                            // (4)!
        List<Integer> a = new ArrayList<>(), b = new ArrayList<>();
        half.forEachRemaining(a::add); whole.forEachRemaining(b::add);
        System.out.println("trySplit: " + a + " and " + b + ", SIZED: " + whole.hasCharacteristics(Spliterator.SIZED));

        String joined = Stream.of("milk", "bread").map(String::toUpperCase).collect(Collectors.joining(", "));
        System.out.println("streams iterate for you (internal iteration): " + joined);
    }
}
