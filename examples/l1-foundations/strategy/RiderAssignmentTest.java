import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RiderAssignmentTest {

    private final List<RiderAssignmentDemo.Rider> riders = List.of(
            new RiderAssignmentDemo.Rider("near-busy", 0.3, 2, false),
            new RiderAssignmentDemo.Rider("far-free", 2.0, 0, true));

    @Test
    void eachStrategyMakesItsOwnChoice() {
        var order = new RiderAssignmentDemo.Order("o1", false);
        var s = RiderAssignmentDemo.STRATEGIES;
        assertEquals("near-busy", s.get(RiderAssignmentDemo.StrategyName.NEAREST).choose(order, riders).orElseThrow().id());
        assertEquals("far-free", s.get(RiderAssignmentDemo.StrategyName.LEAST_LOADED).choose(order, riders).orElseThrow().id());
    }

    @Test
    void coldOrdersOnlyGoToRidersWithColdBoxes() {
        var order = new RiderAssignmentDemo.Order("o2", true);
        var nearest = RiderAssignmentDemo.STRATEGIES.get(RiderAssignmentDemo.StrategyName.NEAREST);
        assertEquals("far-free", nearest.choose(order, riders).orElseThrow().id());
    }

    @Test
    void fallsBackToNearestAndCountsIt() {
        var d = new RiderAssignmentDemo.Dispatcher(Map.of("s", new RiderAssignmentDemo.StoreConfig(RiderAssignmentDemo.StrategyName.BATCHING, null, 0)));
        String result = d.assign("s", new RiderAssignmentDemo.Order("o3", true), riders);
        assertTrue(result.endsWith("far-free via NEAREST"), result);
        assertEquals(1, d.metrics.get("assign.fallback.BATCHING").sum());
    }

    @Test
    void rolloutPercentageIsRoughlyRespectedAndStable() {
        var d = new RiderAssignmentDemo.Dispatcher(Map.of("s", new RiderAssignmentDemo.StoreConfig(
                RiderAssignmentDemo.StrategyName.NEAREST, RiderAssignmentDemo.StrategyName.LEAST_LOADED, 20)));
        long candidate = IntStream.range(0, 10_000)
                .filter(i -> d.strategyFor("s", "FC-" + i) == RiderAssignmentDemo.StrategyName.LEAST_LOADED).count();
        assertTrue(candidate > 1_700 && candidate < 2_300, "got " + candidate);
        assertEquals(d.strategyFor("s", "FC-42"), d.strategyFor("s", "FC-42"));
    }
}
