import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** One contract, run against every adapter: the real PayLink adapter and the in-memory fake used by other tests. */
abstract class PaymentGatewayContractTest {

    abstract PaymentProviderAdapterDemo.PaymentGateway gateway();

    private PaymentProviderAdapterDemo.ChargeRequest request(String customer, long cents, String key) {
        return new PaymentProviderAdapterDemo.ChargeRequest("o-" + key, customer, cents, key);
    }

    @Test
    void approvesAValidCharge() {
        assertInstanceOf(PaymentProviderAdapterDemo.Approved.class, gateway().charge(request("cust-1", 1_000, "k1")));
    }

    @Test
    void sameIdempotencyKeyNeverChargesTwice() {
        var g = gateway();
        var first = (PaymentProviderAdapterDemo.Approved) g.charge(request("cust-1", 1_000, "k2"));
        var second = (PaymentProviderAdapterDemo.Approved) g.charge(request("cust-1", 1_000, "k2"));
        assertEquals(first.paymentId(), second.paymentId());
    }

    @Test
    void insufficientFundsIsADeclineNotAFailure() {
        assertEquals(new PaymentProviderAdapterDemo.Declined(PaymentProviderAdapterDemo.DeclineReason.INSUFFICIENT_FUNDS),
                gateway().charge(request("broke", 1_000, "k3")));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () -> gateway().charge(request("cust-1", 0, "k4")));
    }
}

class PayLinkGatewayContractTest extends PaymentGatewayContractTest {
    PaymentProviderAdapterDemo.PaymentGateway gateway() {
        return new PaymentProviderAdapterDemo.PayLinkGateway(new PaymentProviderAdapterDemo.PayLinkSdk());
    }
}

class FakeGatewayContractTest extends PaymentGatewayContractTest {
    PaymentProviderAdapterDemo.PaymentGateway gateway() {
        Map<String, String> byKey = new HashMap<>();
        return r -> {
            if (r.amountCents() <= 0) throw new IllegalArgumentException("amount must be positive");
            if (r.customerId().equals("broke")) return new PaymentProviderAdapterDemo.Declined(PaymentProviderAdapterDemo.DeclineReason.INSUFFICIENT_FUNDS);
            return new PaymentProviderAdapterDemo.Approved(byKey.computeIfAbsent(r.idempotencyKey(), k -> "fake_" + byKey.size()));
        };
    }
}
