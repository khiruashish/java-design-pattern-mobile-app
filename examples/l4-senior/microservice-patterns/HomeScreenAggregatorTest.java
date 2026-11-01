import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HomeScreenAggregatorTest {

    HomeScreenAggregatorDemo.Registry registry;
    Map<String, HomeScreenAggregatorDemo.Reply> replies;
    List<Map<String, String>> seenHeaders;
    HomeScreenAggregatorDemo.HomeScreenAggregator gw;

    @BeforeEach
    void setUp() {
        registry = new HomeScreenAggregatorDemo.Registry();
        registry.register("catalog", "c1");
        registry.register("cart", "k1");
        registry.register("offers", "o1");
        replies = new HashMap<>(Map.of(
                "c1", new HomeScreenAggregatorDemo.Reply(20, "catalog"),
                "k1", new HomeScreenAggregatorDemo.Reply(30, "cart"),
                "o1", new HomeScreenAggregatorDemo.Reply(50, "offers")));
        seenHeaders = new ArrayList<>();
        gw = new HomeScreenAggregatorDemo.HomeScreenAggregator(registry, (address, path, headers) -> {
            seenHeaders.add(headers);
            return replies.get(address);
        }, 100);
    }

    @Test
    void allHealthyReturnsEverySectionAndSlowestLatency() {
        var screen = gw.render("r1", "C-9");
        assertEquals(200, screen.status());
        assertEquals(List.of("catalog", "cart", "offers"), List.copyOf(screen.sections().keySet()));
        assertEquals(50, screen.latencyMs());
    }

    @Test
    void everyDownstreamCallCarriesTheRequestId() {
        gw.render("r-42", "C-9");
        assertEquals(3, seenHeaders.size());
        seenHeaders.forEach(h -> assertEquals("r-42", h.get("X-Request-Id")));
    }

    @Test
    void slowOptionalDependencyDegradesAndIsCappedByDeadline() {
        replies.put("o1", new HomeScreenAggregatorDemo.Reply(900, "offers"));
        var screen = gw.render("r2", "C-9");
        assertEquals(200, screen.status());
        assertEquals(List.of("offers"), screen.degraded());
        assertEquals(100, screen.latencyMs());
    }

    @Test
    void erroringRequiredDependencyFailsTheRequest() {
        replies.put("k1", new HomeScreenAggregatorDemo.Reply(10, "503 overloaded"));
        assertEquals(503, gw.render("r3", "C-9").status());
        assertTrue(gw.outcomes.contains("r3 cart=error"));
    }

    @Test
    void missingRequiredInstanceFailsWithoutCallingIt() {
        registry.deregister("catalog", "c1");
        assertEquals(503, gw.render("r4", "C-9").status());
        assertTrue(gw.outcomes.contains("r4 catalog=no-instance"));
        assertEquals(2, seenHeaders.size());
    }

    @Test
    void roundRobinsAcrossInstances() {
        registry.register("catalog", "c2");
        replies.put("c2", new HomeScreenAggregatorDemo.Reply(20, "catalog-2"));
        assertEquals("catalog", gw.render("a", "C").sections().get("catalog"));
        assertEquals("catalog-2", gw.render("b", "C").sections().get("catalog"));
    }
}
