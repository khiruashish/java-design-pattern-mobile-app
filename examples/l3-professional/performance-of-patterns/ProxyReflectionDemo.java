import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.LongUnaryOperator;

/** Direct call vs interface vs JDK dynamic proxy vs reflection vs MethodHandle: same result, different costs. */
public class ProxyReflectionDemo {

    public interface Pricing { long price(long paise); }
    public static final class GstPricing implements Pricing { public long price(long p) { return p + p * 5 / 100; } }

    static long run(String label, LongUnaryOperator call) {
        int n = 5_000_000;
        long sum = 0;
        for (int w = 0; w < 5; w++) for (int i = 0; i < n; i++) sum += call.applyAsLong(i);
        sum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) sum += call.applyAsLong(i);
        System.err.printf("%-14s %7.2f ns/call%n", label, (System.nanoTime() - start) / (double) n);
        return sum;
    }

    public static void main(String[] args) throws Throwable {
        GstPricing target = new GstPricing();
        Pricing viaInterface = target;
        Pricing proxy = (Pricing) Proxy.newProxyInstance(Pricing.class.getClassLoader(), new Class<?>[] {Pricing.class},   // (1)!
                (p, method, a) -> method.invoke(target, a));
        Method reflective = GstPricing.class.getMethod("price", long.class);                                             // (2)!
        MethodHandle handle = MethodHandles.lookup().findVirtual(GstPricing.class, "price", MethodType.methodType(long.class, long.class))
                .bindTo(target);                                                                                          // (3)!

        long direct = run("direct", target::price);
        long iface = run("interface", viaInterface::price);
        long prox = run("dynamic proxy", proxy::price);
        long refl = run("reflection", p -> { try { return (long) reflective.invoke(target, p); } catch (Exception e) { throw new RuntimeException(e); } });
        long mh = run("MethodHandle", p -> { try { return (long) handle.invokeExact(p); } catch (Throwable e) { throw new RuntimeException(e); } });

        System.out.println("all five give the same total: " + (direct == iface && iface == prox && prox == refl && refl == mh));
        System.out.println("total = " + direct);
    }
}
