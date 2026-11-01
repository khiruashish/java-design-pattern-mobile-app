import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The contract as real JUnit tests: an abstract class with the tests, one nested subclass per implementation. */
class PaymentGatewayContractTest {

    abstract static class Contract {
        static final String OK_CARD = "4111111111111111";
        VerifiedFakeDemo.PaymentGateway gateway;

        abstract VerifiedFakeDemo.PaymentGateway create();

        @BeforeEach
        void setUp() { gateway = create(); }

        @Test
        @DisplayName("approves a normal card for the full amount")
        void approves() {
            // act
            var result = gateway.charge("k1", OK_CARD, 64_900);
            // assert
            var approved = assertInstanceOf(VerifiedFakeDemo.Approved.class, result);
            assertEquals(64_900, approved.paise());
        }

        @Test
        @DisplayName("a retry with the same idempotency key returns the same payment")
        void idempotent() {
            var first = gateway.charge("k2", OK_CARD, 10_000);
            var retry = gateway.charge("k2", OK_CARD, 10_000);
            assertEquals(first, retry);
        }

        @Test
        @DisplayName("declines the magic decline card")
        void declines() {
            var result = gateway.charge("k3", "4000000000000002", 100);
            assertEquals(new VerifiedFakeDemo.Declined("card_declined"), result);
        }

        @Test
        @DisplayName("refunds add up and can't exceed the captured amount")
        void refundLimit() {
            var paid = (VerifiedFakeDemo.Approved) gateway.charge("k4", OK_CARD, 10_000);
            assertEquals(6_000, gateway.refund(paid.paymentId(), 6_000));
            assertEquals(10_000, gateway.refund(paid.paymentId(), 4_000));
            assertThrows(IllegalArgumentException.class, () -> gateway.refund(paid.paymentId(), 1));
        }

        @Test
        @DisplayName("the timeout card raises GatewayTimeout")
        void timeout() {
            assertThrows(VerifiedFakeDemo.GatewayTimeout.class, () -> gateway.charge("k5", "4000000000000119", 100));
        }
    }

    @Nested
    @DisplayName("PayLink HTTP adapter against the sandbox")
    class HttpAdapter extends Contract {
        VerifiedFakeDemo.PaymentGateway create() { return new VerifiedFakeDemo.PayLinkHttpAdapter(new VerifiedFakeDemo.SimulatedPayLinkServer()); }
    }

    @Nested
    @DisplayName("in-memory fake used by other tests")
    class Fake extends Contract {
        VerifiedFakeDemo.PaymentGateway create() { return new VerifiedFakeDemo.FakePaymentGateway(); }
    }
}
