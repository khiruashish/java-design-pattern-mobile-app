import java.util.Collections;
import java.util.List;

/** Singletons, Flyweights and Null Objects in the JDK, and the == gotcha that the Integer cache causes. */
public class RuntimeSingletonsDemo {

    public static void main(String[] args) {
        Runtime r1 = Runtime.getRuntime(), r2 = Runtime.getRuntime();                                  // (1)!
        System.out.println("Runtime.getRuntime() is one instance: " + (r1 == r2) + ", processors > 0: " + (r1.availableProcessors() > 0));

        Integer a = 127, b = 127, c = 128, d = 128;                                                      // (2)!
        System.out.println("Integer 127 == 127: " + (a == b) + ", 128 == 128: " + (c == d) + ", equals: " + c.equals(d));
        System.out.println("Boolean.valueOf shares instances: " + (Boolean.valueOf("true") == Boolean.TRUE));

        List<String> e1 = Collections.emptyList(), e2 = Collections.emptyList();                        // (3)!
        System.out.println("emptyList() is a shared immutable Null Object: " + (e1 == e2) + ", size " + e1.size());
    }
}
