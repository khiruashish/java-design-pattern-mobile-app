import java.util.function.Function;
import java.util.function.Supplier;

/**
 * invokedynamic and the lambda metafactory: a lambda isn't an anonymous inner class file. javac emits an
 * invokedynamic instruction; on first execution LambdaMetafactory spins a hidden class implementing the functional
 * interface. Non-capturing lambdas are typically cached as a single instance; capturing ones allocate per evaluation.
 */
public class LambdaMetafactoryDemo {

    static Supplier<String> nonCapturing() { return () -> "FreshCart"; }                                 // (1)!
    static Function<Long, Long> capturing(long percent) { return price -> price * (100 - percent) / 100; }   // (2)!

    public static void main(String[] args) {
        Supplier<String> a = nonCapturing(), b = nonCapturing();
        Function<Long, Long> c = capturing(10), d = capturing(10);
        System.out.println("non-capturing lambda reused: " + (a == b));
        System.out.println("capturing lambda reused: " + (c == d));
        Class<?> k = a.getClass();
        System.out.println("hidden class: " + k.isHidden() + ", synthetic: " + k.isSynthetic()
                + ", anonymous inner class: " + k.isAnonymousClass());                                     // (3)!
        System.out.println("10% off Rs 136: Rs " + c.apply(13_600L) / 100);
    }
}
