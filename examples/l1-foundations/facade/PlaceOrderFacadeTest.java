import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceOrderFacadeTest {

    private final PlaceOrderFacadeDemo.Inventory inventory = new PlaceOrderFacadeDemo.Inventory();
    private final PlaceOrderFacadeDemo.Coupons coupons = new PlaceOrderFacadeDemo.Coupons();
    private final PlaceOrderFacadeDemo.Payments payments = new PlaceOrderFacadeDemo.Payments();
    private final PlaceOrderFacadeDemo.Orders orders = new PlaceOrderFacadeDemo.Orders();
    private final PlaceOrderFacadeDemo.PlaceOrderFacade facade =
            new PlaceOrderFacadeDemo.PlaceOrderFacade(inventory, coupons, payments, orders, new PlaceOrderFacadeDemo.Notifier());

    private PlaceOrderFacadeDemo.PlaceOrder cmd(String key, String customer, String coupon) {
        return new PlaceOrderFacadeDemo.PlaceOrder(key, "o-" + key, customer, List.of("MILK"), 10_000, coupon);
    }

    @Test
    void sameIdempotencyKeyChargesOnce() {
        facade.place(cmd("k", "asha", null));
        facade.place(cmd("k", "asha", null));
        assertEquals(1, payments.charges);
        assertEquals(List.of("o-k"), orders.saved);
    }

    @Test
    void paymentFailureUndoesEarlierStepsInReverseOrder() {
        var outcome = facade.place(cmd("k", "declined", "SAVE"));
        var rejected = assertInstanceOf(PlaceOrderFacadeDemo.Rejected.class, outcome);
        assertEquals(List.of("coupon restored", "stock released"), rejected.undone());
        assertTrue(inventory.reserved.isEmpty());
        assertTrue(coupons.used.isEmpty());
    }

    @Test
    void notificationFailureIsAWarningNotAFailure() {
        var placed = assertInstanceOf(PlaceOrderFacadeDemo.Placed.class, facade.place(cmd("k", "no-sms", null)));
        assertEquals(1, placed.warnings().size());
    }
}
