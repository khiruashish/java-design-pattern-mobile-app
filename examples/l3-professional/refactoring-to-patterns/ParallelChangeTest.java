import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ParallelChangeTest {

    @Test
    void shadowModeAlwaysReturnsTheControlResult() {
        var fees = new ParallelChangeDemo.RoutingFees(new ParallelChangeDemo.LegacyFees(), new ParallelChangeDemo.V2Fees(),
                new ParallelChangeDemo.Rollout(0, false), true);
        for (int i = 0; i < 200; i++) {
            long amount = ParallelChangeDemo.amountFor(i);
            assertEquals(new ParallelChangeDemo.LegacyFees().fee("x", amount), fees.fee("FC-" + i, amount));
        }
        assertTrue(fees.shadow.mismatched > 0);
    }

    @Test
    void candidateFailuresNeverReachTheCaller() {
        var exp = new ParallelChangeDemo.Experiment<String>();
        assertEquals("ok", exp.run("k", () -> "ok", () -> { throw new IllegalStateException("boom"); }));
        assertEquals(1, exp.candidateErrors);
    }

    @Test
    void rolloutIsDeterministicMonotonicAndKillable() {
        List<String> ids = IntStream.range(0, 5_000).mapToObj(i -> "FC-" + i).toList();
        var r10 = new ParallelChangeDemo.Rollout(10, false);
        var r50 = new ParallelChangeDemo.Rollout(50, false);
        for (String id : ids) {
            assertEquals(r10.useNew(id), r10.useNew(id));
            if (r10.useNew(id)) assertTrue(r50.useNew(id), "raising the percentage must keep earlier users on v2");
        }
        long share = ids.stream().filter(r50::useNew).count();
        assertTrue(share > 2_250 && share < 2_750, "about half: " + share);
        assertFalse(ids.stream().anyMatch(new ParallelChangeDemo.Rollout(100, true)::useNew));
        assertThrows(IllegalArgumentException.class, () -> new ParallelChangeDemo.Rollout(101, false));
    }

    @Test
    void refactoredDiscountsMatchTheOriginalOnEdgeCases() {
        var engine = new DiscountRulesRefactorDemo.DiscountEngine(List.of(new DiscountRulesRefactorDemo.FirstOrder(),
                new DiscountRulesRefactorDemo.MemberFivePercent(), new DiscountRulesRefactorDemo.DairyBundle(),
                new DiscountRulesRefactorDemo.Fresh10Coupon()), 50);
        for (long subtotal : new long[] {0, 1, 29_999, 30_000, 50_000, 1_000_000})
            for (boolean first : new boolean[] {true, false}) {
                var cart = new DiscountRulesRefactorDemo.Cart(subtotal, true, first, 3, "FRESH10");
                assertEquals(DiscountRulesRefactorDemo.discountBefore(cart), engine.apply(cart).total());
            }
    }

    @Test
    void everyRefactoringStepMatchesTheGoldenMaster() {
        var inputs = RefactoringWorkflowDemo.sampleInputs();
        for (var o : inputs) {
            String golden = RefactoringWorkflowDemo.v0(o);
            assertEquals(golden, RefactoringWorkflowDemo.v1(o));
            assertEquals(golden, RefactoringWorkflowDemo.v2(o));
            assertEquals(golden, RefactoringWorkflowDemo.v3(o));
        }
    }
}
