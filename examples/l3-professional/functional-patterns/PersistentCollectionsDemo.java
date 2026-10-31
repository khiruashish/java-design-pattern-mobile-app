import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Immutability: unmodifiable VIEWS vs immutable COPIES, and a tiny persistent list that shares structure
 * so "adding" returns a new version cheaply without changing the old one.
 */
public class PersistentCollectionsDemo {

    sealed interface PList<T> permits Cons, Nil {                                                      // (1)!
        default PList<T> prepend(T head) { return new Cons<>(head, this); }
        default int size() { return this instanceof Cons<T> c ? 1 + c.tail().size() : 0; }
        default <R> PList<R> map(Function<T, R> f) {
            return switch (this) { case Cons<T> c -> new Cons<>(f.apply(c.head()), c.tail().map(f)); case Nil<T> n -> new Nil<>(); };
        }
        default String show() { return this instanceof Cons<T> c ? c.head() + (c.tail() instanceof Cons<T> ? ", " : "") + c.tail().show() : ""; }
    }
    record Cons<T>(T head, PList<T> tail) implements PList<T> { }
    record Nil<T>() implements PList<T> { }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("MILK", "BREAD"));
        List<String> view = Collections.unmodifiableList(source);                                     // (2)!
        List<String> copy = List.copyOf(source);
        source.add("EGGS");
        System.out.println("unmodifiable view sees later change: " + view);
        System.out.println("List.copyOf does not:              " + copy);

        PList<String> v1 = new Nil<String>().prepend("BREAD").prepend("MILK");
        PList<String> v2 = v1.prepend("EGGS");                                                         // (3)!
        System.out.println("v1 = [" + v1.show() + "] size " + v1.size());
        System.out.println("v2 = [" + v2.show() + "] size " + v2.size());
        System.out.println("v2 shares v1's nodes: " + (((Cons<String>) v2).tail() == v1));
        System.out.println("mapped v2: [" + v2.map(String::toLowerCase).show() + "], v2 unchanged: [" + v2.show() + "]");
    }
}
