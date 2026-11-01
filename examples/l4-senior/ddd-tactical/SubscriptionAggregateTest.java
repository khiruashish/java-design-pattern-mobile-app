import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionAggregateTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 1);   // a Sunday
    SubscriptionAggregateDemo.SubscriptionRepository repo;
    SubscriptionAggregateDemo.Subscription sub;

    @BeforeEach
    void setUp() {
        repo = new SubscriptionAggregateDemo.SubscriptionRepository();
        sub = new SubscriptionAggregateDemo.SubscriptionFactory().start("C-1", 1, new SubscriptionAggregateDemo.Money(6_800));
        repo.save(sub);
    }

    @Test
    void skipRespectsTheCutoff() {
        var tomorrow = TODAY.plusDays(1);
        assertThrows(IllegalStateException.class, () -> sub.skip(tomorrow, TODAY, 22));
        sub.skip(tomorrow, TODAY, 21);
        assertFalse(sub.deliversOn(tomorrow));
    }

    @Test
    void pauseAbsorbsSkipsAndIsLimited() {
        var d = LocalDate.of(2026, 11, 10);
        sub.skip(d, TODAY, 9);
        sub.pause(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12), TODAY);
        assertFalse(sub.deliversOn(d));
        sub.resume();
        assertTrue(sub.deliversOn(d), "the skip was absorbed by the pause and doesn't come back");
        assertThrows(IllegalStateException.class, () -> sub.pause(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), TODAY));
    }

    @Test
    void secondWriterGetsAConcurrencyErrorAndNoEventsAreLeaked() {
        var a = repo.load(sub.id()).orElseThrow();
        var b = repo.load(sub.id()).orElseThrow();
        a.skip(LocalDate.of(2026, 11, 4), TODAY, 9);
        repo.save(a);
        b.skip(LocalDate.of(2026, 11, 5), TODAY, 9);
        int publishedBefore = repo.published.size();
        assertThrows(SubscriptionAggregateDemo.ConcurrentModification.class, () -> repo.save(b));
        assertEquals(publishedBefore, repo.published.size());
        assertTrue(repo.load(sub.id()).orElseThrow().deliversOn(LocalDate.of(2026, 11, 5)));
    }

    @Test
    void billingCountsOnlyDeliveryDays() {
        var s = repo.load(sub.id()).orElseThrow();
        // November 2026 has 30 days and 5 Sundays: 25 deliveries of 1 litre at Rs 68
        assertEquals(25 * 6_800, new SubscriptionAggregateDemo.MonthlyBilling().billFor(s, LocalDate.of(2026, 11, 1)).paise());
    }

    @Test
    void litresAreAValueObjectWithLimits() {
        assertThrows(IllegalArgumentException.class, () -> new SubscriptionAggregateDemo.Litres(6));
    }

    @Test
    void cartEnforcesOneCouponAndFreezesAfterCheckout() {
        var cart = new CartAggregateDemo.Cart("C");
        cart.add(new CartAggregateDemo.Sku("MILK-1L"), 1, new CartAggregateDemo.Money(6_800));
        cart.applyCoupon("FRESH10");
        assertThrows(IllegalStateException.class, () -> cart.applyCoupon("OTHER"));
        cart.checkout();
        assertThrows(IllegalStateException.class, () -> cart.add(new CartAggregateDemo.Sku("X"), 1, new CartAggregateDemo.Money(1)));
    }
}
