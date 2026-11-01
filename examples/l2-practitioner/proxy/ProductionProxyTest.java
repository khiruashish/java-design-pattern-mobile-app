import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductionProxyTest {

    private final ProductionProxyDemo.RealOrderAdmin real = new ProductionProxyDemo.RealOrderAdmin();
    private final ProductionProxyDemo.Metrics metrics = new ProductionProxyDemo.Metrics();

    private ProductionProxyDemo.OrderAdmin proxy(String... roles) {
        return ProductionProxyDemo.enhance(ProductionProxyDemo.OrderAdmin.class, real, () -> Set.of(roles), metrics);
    }

    @Test
    void cachesPerArgument() {
        var admin = proxy();
        admin.orderSummary("A");
        admin.orderSummary("A");
        admin.orderSummary("B");
        assertEquals(2, real.summaryLoads);
    }

    @Test
    void deniesWithoutRoleAndAllowsWithIt() {
        assertThrows(SecurityException.class, () -> proxy("AGENT").refund("A", 100));
        assertTrue(proxy("SUPERVISOR").refund("A", 10_000).startsWith("refunded"));
    }

    @Test
    void rethrowsTheTargetsOwnException() {
        assertThrows(IllegalArgumentException.class, () -> proxy().orderSummary(" "));
        assertTrue(metrics.toString().contains("err"));
    }

    @Test
    void failedCallsAreNotCached() {
        var admin = proxy();
        assertThrows(IllegalArgumentException.class, () -> admin.orderSummary(" "));
        assertThrows(IllegalArgumentException.class, () -> admin.orderSummary(" "));
        assertEquals(2, real.summaryLoads);
    }
}
