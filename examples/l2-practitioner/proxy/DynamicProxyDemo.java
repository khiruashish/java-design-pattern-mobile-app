import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** JDK dynamic proxies: ONE handler adds behaviour to ANY interface, generated at runtime. */
public class DynamicProxyDemo {

    interface OrderApi { String status(String id); String cancel(String id); }
    interface RiderApi { String locate(String riderId); }

    static final List<String> LOG = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static <T> T logged(Class<T> iface, T target) {
        InvocationHandler h = (proxy, method, args) -> {                                   // (1)!
            if (method.getDeclaringClass() == Object.class) return method.invoke(target, args);   // (2)!
            LOG.add(iface.getSimpleName() + "." + method.getName() + "(" + args[0] + ")");
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();                                                        // (3)!
            }
        };
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, h);   // (4)!
    }

    public static void main(String[] args) {
        OrderApi orders = logged(OrderApi.class, new OrderApi() {
            public String status(String id) { return id + ": out for delivery"; }
            public String cancel(String id) { throw new IllegalStateException(id + " already dispatched"); }
        });
        RiderApi riders = logged(RiderApi.class, id -> id + " at 12.97,77.59");

        System.out.println(orders.status("FC-3001"));
        System.out.println(riders.locate("R-42"));
        try { orders.cancel("FC-3001"); } catch (IllegalStateException e) { System.out.println("caller sees the real exception: " + e.getMessage()); }
        System.out.println("log: " + LOG);
        System.out.println("proxy class: " + orders.getClass().getSimpleName().replaceAll("\\d+", "N") + ", is proxy: " + Proxy.isProxyClass(orders.getClass()));
        try {
            Proxy.newProxyInstance(DynamicProxyDemo.class.getClassLoader(), new Class<?>[] {ArrayList.class}, (p, m, a) -> null);
        } catch (IllegalArgumentException e) { System.out.println("classes can't be proxied: " + e.getMessage()); }
    }
}
