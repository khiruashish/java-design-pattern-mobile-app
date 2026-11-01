import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RiderSettlementTest {

    @Test
    void earningUsesZoneRateAndLatePenalty() {
        // 25 + 6.5 * 2 = 38.00; late: 34.20
        assertEquals(new BigDecimal("38.00"), RiderSettlementDemo.earning(new RiderSettlementDemo.Delivery("R", "CENTRAL", 2, false)));
        assertEquals(new BigDecimal("34.20"), RiderSettlementDemo.earning(new RiderSettlementDemo.Delivery("R", "CENTRAL", 2, true)));
    }

    @Test
    void badLinesBecomeValuesNotExceptions() {
        assertInstanceOf(RiderSettlementDemo.Bad.class, RiderSettlementDemo.parse(1, "R1,CENTRAL,x,late"));
        assertInstanceOf(RiderSettlementDemo.Bad.class, RiderSettlementDemo.parse(2, "R1,CENTRAL,3"));
        assertInstanceOf(RiderSettlementDemo.Good.class, RiderSettlementDemo.parse(3, "R1,CENTRAL,3,late"));
    }

    @Test
    void settlementDoesNotDependOnInputOrder() {
        List<RiderSettlementDemo.Delivery> ds = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 500; i++)
            ds.add(new RiderSettlementDemo.Delivery("R" + random.nextInt(20), random.nextBoolean() ? "CENTRAL" : "OUTER", 1 + random.nextInt(15), random.nextInt(10) == 0));
        var expected = RiderSettlementDemo.settle(ds);
        for (int round = 0; round < 5; round++) {
            Collections.shuffle(ds, random);
            assertEquals(expected, RiderSettlementDemo.settle(ds));
        }
    }

    @Test
    void validationCollectsAllErrorsButResultStopsAtFirst() {
        var v = OrderFormValidationDemo.validate(new OrderFormValidationDemo.Form("1", "2", "3", -1));
        assertEquals(4, v.errors().size());
        var r = ResultEitherDemo.parseLine("milk,zero");
        assertEquals(new ResultEitherDemo.Err<>("bad SKU 'milk'"), r);
    }

    @Test
    void composedPricingMatchesTheImperativeVersion() {
        for (long subtotal : new long[] {1_000, 49_900, 52_000, 200_000})
            for (boolean member : new boolean[] {true, false}) {
                var cart = new FunctionCompositionDemo.Cart(subtotal, member, "FRESH10");
                assertEquals(FunctionCompositionDemo.priceBefore(cart), FunctionCompositionDemo.PRICE.apply(cart));
            }
    }
}
