import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentGatewayFactoryTest {

    private final Map<String, String> config = Map.of(
            "m1.type", "card", "m1.endpoint", "https://cards.example/v2",
            "m2.type", "sandbox",
            "m3.type", "card", "m3.endpoint", "https://cards.example/v2", "m3.timeoutMs", "50");

    @Test
    void createsTheConfiguredTypeOnceAndCachesIt() {
        var factory = new PaymentGatewayFactoryDemo.GatewayFactory("prod", config);
        var gateway = factory.forMerchant("m1");
        assertInstanceOf(PaymentGatewayFactoryDemo.CardNetworkGateway.class, gateway);
        assertSame(gateway, factory.forMerchant("m1"));
        assertEquals(1, factory.created.get());
    }

    @Test
    void refusesSandboxInProductionAndLiveOutsideIt() {
        assertThrows(IllegalStateException.class, () -> new PaymentGatewayFactoryDemo.GatewayFactory("prod", config).forMerchant("m2"));
        assertThrows(IllegalStateException.class, () -> new PaymentGatewayFactoryDemo.GatewayFactory("staging", config).forMerchant("m1"));
    }

    @Test
    void rejectsOutOfRangeTimeouts() {
        var e = assertThrows(IllegalArgumentException.class, () -> new PaymentGatewayFactoryDemo.GatewayFactory("prod", config).forMerchant("m3"));
        assertEquals("m3: timeoutMs must be 100..30000", e.getMessage());
    }
}
