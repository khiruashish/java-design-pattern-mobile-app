import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Realistic: FreshCart wraps admin services in an audit proxy so every admin action is logged with the operator,
 * without touching the services. The proxy also shows the classic limit of proxy-based AOP: calls a service makes
 * to itself don't go through the proxy.
 */
public class AuditProxyDemo {

    interface PriceAdmin {
        void setPrice(String sku, long paise);
        void bulkDiscount(List<String> skus, int percent);
    }

    static final class PriceAdminService implements PriceAdmin {
        final java.util.Map<String, Long> prices = new java.util.TreeMap<>(java.util.Map.of("MILK-1L", 6800L, "CURD-500G", 4500L));
        public void setPrice(String sku, long paise) {
            if (paise <= 0) throw new IllegalArgumentException("price must be positive");
            prices.put(sku, paise);
        }
        public void bulkDiscount(List<String> skus, int percent) {
            for (String s : skus) setPrice(s, prices.get(s) * (100 - percent) / 100);                   // (1)!
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T audited(T target, Class<T> api, String operator, List<String> audit) {
        return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> {
            String call = operator + " " + method.getName() + java.util.Arrays.toString(args);
            try {
                Object result = method.invoke(target, args);
                audit.add("OK   " + call);
                return result;
            } catch (InvocationTargetException e) {                                                   // (2)!
                audit.add("FAIL " + call + ": " + e.getCause().getMessage());
                throw e.getCause();                                                                   // (3)!
            }
        });
    }

    public static void main(String[] args) {
        List<String> audit = new ArrayList<>();
        PriceAdminService service = new PriceAdminService();
        PriceAdmin admin = audited(service, PriceAdmin.class, "ops:meena", audit);
        admin.setPrice("MILK-1L", 7000);
        try { admin.setPrice("CURD-500G", 0); } catch (IllegalArgumentException e) { System.out.println("caller saw: " + e.getMessage()); }
        admin.bulkDiscount(List.of("MILK-1L", "CURD-500G"), 10);
        audit.forEach(System.out::println);
        System.out.println("prices: " + service.prices + " (the two setPrice calls inside bulkDiscount were not audited)");
    }
}
