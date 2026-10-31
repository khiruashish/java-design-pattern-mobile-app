import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FeeAndPayoutTest {

    private final FeeAndPayoutDemo.DeliveryFeePolicy fee = new FeeAndPayoutDemo.DeliveryFeePolicy(199, 60, 5_000);
    private final FeeAndPayoutDemo.RiderPayoutPolicy payout = new FeeAndPayoutDemo.RiderPayoutPolicy(150, 45, 300);

    @Test
    void freeDeliveryDoesNotChangeTheRiderPayout() {
        assertEquals(0, fee.feeInCents(6.5, 7_200));
        assertEquals(443, payout.payoutInCents(6.5));
    }

    @Test
    void shortTripsPayTheRiderMinimum() {
        assertEquals(300, payout.payoutInCents(0.5));
    }

    @Test
    void bothPoliciesRejectTheSameInvalidDistances() {
        assertThrows(IllegalArgumentException.class, () -> fee.feeInCents(0, 1_000));
        assertThrows(IllegalArgumentException.class, () -> payout.payoutInCents(25.1));
    }
}
