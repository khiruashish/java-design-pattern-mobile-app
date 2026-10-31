/**
 * What ByteBuddy and CGLIB generate when there is no interface: a SUBCLASS that overrides methods and adds behaviour.
 * Written by hand here so you can see it. Spring uses this for @Transactional/@Cacheable on classes.
 */
public class SubclassProxyDemo {

    static class InventoryService {
        public String reserve(String sku) { return "reserved " + sku; }
        public final String release(String sku) { return "released " + sku; }              // (1)!
    }

    static class InventoryService$$Proxy extends InventoryService {                         // (2)!
        private final InventoryService target;
        InventoryService$$Proxy(InventoryService target) { this.target = target; }
        @Override public String reserve(String sku) {
            System.out.println("  [tx] begin");
            String r = target.reserve(sku);
            System.out.println("  [tx] commit");
            return r;
        }
        // release(...) is final: it can't be overridden, so it runs WITHOUT the transaction   // (3)!
    }

    public static void main(String[] args) {
        InventoryService svc = new InventoryService$$Proxy(new InventoryService());
        System.out.println(svc.reserve("MILK-1L"));
        System.out.println(svc.release("MILK-1L") + "  <- no [tx] lines: final method not intercepted");
        System.out.println("caller sees an InventoryService: " + (svc instanceof InventoryService) + ", class: " + svc.getClass().getSimpleName());
    }
}
