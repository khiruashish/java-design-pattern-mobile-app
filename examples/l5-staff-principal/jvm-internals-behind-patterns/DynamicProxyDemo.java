import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic proxies: the JDK generates a class at runtime implementing given interfaces and routes every call to an
 * InvocationHandler. This is how Spring AOP (interface-based), Mockito-style mocks and many decorators work.
 * Class-based proxies (CGLIB, ByteBuddy) subclass instead, which is why final classes and methods can't be proxied.
 */
public class DynamicProxyDemo {

    interface PricingService { long price(String sku); }
    static final class RealPricing implements PricingService { public long price(String sku) { return sku.startsWith("MILK") ? 6800 : 4500; } }

    @SuppressWarnings("unchecked")
    static <T> T timed(T target, Class<T> type, List<String> log) {
        InvocationHandler handler = (proxy, method, args) -> {                                          // (1)!
            log.add("before " + method.getName() + "(" + args[0] + ")");
            Object result = method.invoke(target, args);
            log.add("after " + method.getName() + " -> " + result);
            return result;
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);       // (2)!
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        PricingService pricing = timed(new RealPricing(), PricingService.class, log);
        System.out.println("price: " + pricing.price("MILK-1L"));
        System.out.println("log: " + log);
        System.out.println("proxy class is generated: " + Proxy.isProxyClass(pricing.getClass())
                + ", implements PricingService: " + (pricing instanceof PricingService)
                + ", is a RealPricing: " + (pricing instanceof RealPricing));                           // (3)!
    }
}
