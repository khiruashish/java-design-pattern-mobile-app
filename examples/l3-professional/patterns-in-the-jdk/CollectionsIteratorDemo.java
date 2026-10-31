import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** The Collections Framework is a catalogue of patterns: Iterator, Adapter, Proxy/Decorator views, Factory methods. */
public class CollectionsIteratorDemo {

    /** Iterator: any class implementing Iterable works with for-each, without exposing its storage. */
    record DeliveryRoute(String... stops) implements Iterable<String> {                                  // (1)!
        public Iterator<String> iterator() {
            return new Iterator<>() {
                int i = 0;
                public boolean hasNext() { return i < stops.length; }
                public String next() { if (!hasNext()) throw new NoSuchElementException(); return stops[i++]; }
            };
        }
    }

    public static void main(String[] args) {
        for (String stop : new DeliveryRoute("Hub", "Baner", "Aundh")) System.out.print(stop + " -> ");
        System.out.println("done");

        String[] skus = {"MILK", "BREAD", "EGGS"};
        List<String> adapted = Arrays.asList(skus);                                                      // (2)!
        skus[0] = "CURD";
        System.out.println("Arrays.asList is an adapter over the array: " + adapted);
        try { adapted.add("JAM"); } catch (UnsupportedOperationException e) { System.out.println("  ...with fixed size: add() unsupported"); }

        List<String> cart = new ArrayList<>(List.of("MILK", "BREAD"));
        List<String> readOnly = Collections.unmodifiableList(cart);                                    // (3)!
        List<String> synced = Collections.synchronizedList(cart);
        synced.add("EGGS");
        System.out.println("unmodifiable proxy sees: " + readOnly + ", synchronized decorator wrote through: " + cart);
        System.out.println("subList is a view: " + cart.subList(1, 3) + "; List.of is a factory returning a hidden immutable class: "
                + List.of("A", "B").getClass().getName().startsWith("java.util.ImmutableCollections"));
    }
}
