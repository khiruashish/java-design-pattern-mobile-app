import java.lang.reflect.Proxy;

/** The classic proxy trap: a call from inside the object to itself (this.method()) never goes through the proxy. */
public class SelfInvocationDemo {

    interface Orders { void placeAll(String... ids); void place(String id); }

    static final class OrderService implements Orders {
        public void placeAll(String... ids) { for (String id : ids) this.place(id); }        // (1)!
        public void place(String id) { System.out.println("    place " + id); }
    }

    static Orders transactional(Orders target) {
        return (Orders) Proxy.newProxyInstance(Orders.class.getClassLoader(), new Class<?>[] {Orders.class}, (p, m, a) -> {
            System.out.println("  [tx begin] " + m.getName());
            Object r = m.invoke(target, a);
            System.out.println("  [tx commit] " + m.getName());
            return r;
        });
    }

    static final class FixedOrderService implements Orders {
        Orders self;                                                                         // (2)!
        public void placeAll(String... ids) { for (String id : ids) self.place(id); }
        public void place(String id) { System.out.println("    place " + id); }
    }

    public static void main(String[] args) {
        System.out.println("trap: one transaction around placeAll, none around each place():");
        transactional(new OrderService()).placeAll("FC-1", "FC-2");

        System.out.println("fix: call through the proxy (better: move place() to another bean):");
        FixedOrderService target = new FixedOrderService();
        Orders proxy = transactional(target);
        target.self = proxy;                                                                 // (3)!
        proxy.placeAll("FC-1", "FC-2");
    }
}
