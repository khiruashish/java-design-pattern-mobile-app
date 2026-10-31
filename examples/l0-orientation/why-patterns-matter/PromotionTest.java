import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PromotionTest {

    @ParameterizedTest(name = "{0} items, basket {1} cents -> {2} cents off")
    @CsvSource({
            "9,  4800, 0",     // one item short of the threshold
            "10, 4800, 250",   // exactly at the threshold
            "12, 4800, 300"})
    void bulkDiscountStartsAtTheThreshold(int items, long totalInCents, long expectedDiscount) {
        var promo = new PromotionDemo.BuyManySaveMore("BULK_10", 10, 25);
        assertEquals(expectedDiscount, promo.discountInCents(new PromotionDemo.Basket(totalInCents, items)));
    }
}
