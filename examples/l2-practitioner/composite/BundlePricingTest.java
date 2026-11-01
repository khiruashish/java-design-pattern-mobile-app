import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundlePricingTest {

    private static BundlePricingDemo.Sku sku(String code, String price) { return BundlePricingDemo.sku(code, code, price); }
    private static BundlePricingDemo.Bundle bundle(int pct, String max, BundlePricingDemo.Line... lines) {
        return new BundlePricingDemo.Bundle("b", pct, new BigDecimal(max), List.of(lines));
    }

    @Test
    void leafAndCompositeAnswerTheSameQuestion() {
        assertEquals(new BigDecimal("100"), BundlePricingDemo.price(sku("A", "100")));
        assertEquals(0, new BigDecimal("150").compareTo(BundlePricingDemo.price(bundle(0, "0", sku("A", "100"), sku("B", "50")))));
    }

    @Test
    void discountIsCapped() {
        // 10% of 1000 = 100, capped at 30
        assertEquals(0, new BigDecimal("970").compareTo(BundlePricingDemo.price(bundle(10, "30", sku("A", "1000")))));
    }

    @Test
    void nestedDiscountsApplyAtEachLevel() {
        var inner = bundle(10, "1000", sku("A", "100"));          // 90
        var outer = bundle(10, "1000", inner, sku("B", "10"));    // (90 + 10) * 0.9 = 90
        assertEquals(0, new BigDecimal("90").compareTo(BundlePricingDemo.price(outer)));
    }

    @Test
    void oneMissingLeafMakesTheWholeBundleUnavailable() {
        var out = new BundlePricingDemo.Sku("Z", "z", BigDecimal.ONE, false);
        assertFalse(BundlePricingDemo.available(bundle(0, "0", sku("A", "1"), bundle(0, "0", out))));
    }

    @Test
    void rejectsDuplicateSkusAcrossLevels() {
        assertThrows(IllegalArgumentException.class, () -> BundlePricingDemo.validate(bundle(0, "0", sku("A", "1"), bundle(0, "0", sku("A", "1")))));
    }
}
