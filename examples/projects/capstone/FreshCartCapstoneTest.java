import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FreshCartCapstoneTest {

    static final FreshCartCapstoneDemo.Product MILK = new FreshCartCapstoneDemo.Product("MILK-1L", "Milk", new FreshCartCapstoneDemo.Money(6800), Set.of("dairy"));
    static final FreshCartCapstoneDemo.Product SPINACH = new FreshCartCapstoneDemo.Product("SPINACH-250G", "Spinach", new FreshCartCapstoneDemo.Money(6000), Set.of("organic"));

    FreshCartCapstoneDemo.Stock stock;
    FreshCartCapstoneDemo.PayLink payLink;

    @BeforeEach
    void setUp() {
        stock = new FreshCartCapstoneDemo.Stock();
        stock.units.putAll(Map.of("MILK-1L", 10, "SPINACH-250G", 10));
        payLink = new FreshCartCapstoneDemo.PayLink();
    }

    private FreshCartCapstoneDemo.CheckoutFacade facade(FreshCartCapstoneDemo.DeliveryPartner quickDrop) {
        return new FreshCartCapstoneDemo.CheckoutFacade(stock, payLink,
                new FreshCartCapstoneDemo.PartnerRouter(quickDrop, new FreshCartCapstoneDemo.ParcelGoAdapter(new FreshCartCapstoneDemo.ParcelGoApi())));
    }

    private static FreshCartCapstoneDemo.Order order(String id, int milk, int spinach) {
        return new FreshCartCapstoneDemo.Order(id, "asha", "Bengaluru",
                List.of(new FreshCartCapstoneDemo.Line(MILK, milk), new FreshCartCapstoneDemo.Line(SPINACH, spinach)));
    }

    @Test
    void lineQuantityInvariantIsEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new FreshCartCapstoneDemo.Line(MILK, 21));
    }

    @Test
    void couponDiscountsOnlyOrganicItemsWhenEligible() {
        var quote = FreshCartCapstoneDemo.price(order("A", 2, 3), FreshCartCapstoneDemo.GREEN10, FreshCartCapstoneDemo.STANDARD_FEE);
        assertEquals(1800, quote.discount().paise());                // 10% of 3 x Rs 60
        var small = FreshCartCapstoneDemo.price(order("B", 1, 1), FreshCartCapstoneDemo.GREEN10, FreshCartCapstoneDemo.STANDARD_FEE);
        assertEquals(0, small.discount().paise());                   // basket under Rs 300
    }

    @Test
    void sameIdempotencyKeyChargesOnce() {
        var checkout = facade(new FreshCartCapstoneDemo.QuickDropAdapter(new FreshCartCapstoneDemo.QuickDropApi()));
        var order = order("A", 1, 1);
        var first = checkout.checkout("k1", order, null);
        assertSame(first, checkout.checkout("k1", order, null));
        assertEquals(1, payLink.ledger.size());
    }

    @Test
    void failedDeliveryBookingRefundsAndReleasesStock() {
        var broken = new FreshCartCapstoneDemo.DeliveryPartner() {
            public String name() { return "QuickDrop"; }
            public String book(String orderId) { throw new IllegalStateException("QuickDrop unavailable"); }
        };
        var receipt = facade(broken).checkout("k2", order("A", 2, 1), null);
        assertEquals("CANCELLED", receipt.status());
        assertEquals(List.of("charge A Rs 225.00", "refund PL-A Rs 225.00"), payLink.ledger);
        assertEquals(10, stock.units.get("MILK-1L"));
    }

    @Test
    void cancelledOrderCannotBeDispatched() {
        var order = order("A", 1, 1);
        order.cancel("customer request");
        assertInstanceOf(FreshCartCapstoneDemo.Cancelled.class, order.state());
        assertThrows(IllegalStateException.class, () -> order.markDispatched("QuickDrop"));
    }
}
