import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentStackFactoryTest {

    private static final PaymentStackFactoryDemo.Config LIVE_OK =
            new PaymentStackFactoryDemo.Config("https://api.paylink.example", "live_k", "whsec_live_0123456789");
    private static final PaymentStackFactoryDemo.Config SANDBOX_OK =
            new PaymentStackFactoryDemo.Config("https://sandbox.paylink.example", "test_k", "whsec_test_0123456789");

    @Test
    void everyProductFromOneFactoryHasTheSameEnvironment() {
        for (var env : PaymentStackFactoryDemo.Env.values()) {
            var f = new PaymentStackFactoryDemo.PayLinkStackFactory(env, env == PaymentStackFactoryDemo.Env.LIVE ? LIVE_OK : SANDBOX_OK);
            assertDoesNotThrow(() -> PaymentStackFactoryDemo.PaymentStack.from(f));
            assertEquals(List.of(env, env, env), List.of(f.charges().env(), f.webhooks().env(), f.refunds().env()));
        }
    }

    @Test
    void liveFactoryRejectsSandboxEndpointsAndTestKeys() {
        var bad = new PaymentStackFactoryDemo.Config("https://sandbox.paylink.example", "test_k", "whsec_live_0123456789");
        var e = assertThrows(IllegalStateException.class, () -> new PaymentStackFactoryDemo.PayLinkStackFactory(PaymentStackFactoryDemo.Env.LIVE, bad));
        assertTrue(e.getMessage().contains("sandbox endpoint"));
        assertTrue(e.getMessage().contains("test_ key"));
    }

    @Test
    void mixedStacksCannotBeAssembled() {
        var live = new PaymentStackFactoryDemo.PayLinkStackFactory(PaymentStackFactoryDemo.Env.LIVE, LIVE_OK);
        var sandbox = new PaymentStackFactoryDemo.PayLinkStackFactory(PaymentStackFactoryDemo.Env.SANDBOX, SANDBOX_OK);
        assertThrows(IllegalStateException.class, () -> new PaymentStackFactoryDemo.PaymentStack(live.charges(), live.webhooks(), sandbox.refunds()));
    }
}
