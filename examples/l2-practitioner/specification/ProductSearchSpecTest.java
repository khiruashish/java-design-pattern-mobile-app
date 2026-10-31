import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class ProductSearchSpecTest {

    static List<Map<String, String>> requests() {
        return List.of(
                Map.of(),
                Map.of("category", "fruit,dairy", "max_price", "100", "in_stock", "true"),
                Map.of("organic", "true", "exclude_category", "dairy"),
                Map.of("q", "milk", "min_price", "50"),
                Map.of("q", "100%"));
    }

    @ParameterizedTest
    @MethodSource("requests")
    void everyPlaceholderHasExactlyOneBindValue(Map<String, String> request) {
        var spec = ProductSearchSpecDemo.fromRequest(request);
        List<Object> binds = new ArrayList<>();
        String sql = ProductSearchSpecDemo.toSql(spec, binds);
        assertEquals(sql.chars().filter(c -> c == '?').count(), binds.size());
    }

    @Test
    void inMemoryMatchesTheExpectedProducts() {
        var spec = ProductSearchSpecDemo.fromRequest(Map.of("organic", "true", "exclude_category", "dairy"));
        List<String> skus = ProductSearchSpecDemo.HOT_CATALOG.stream().filter(p -> ProductSearchSpecDemo.test(spec, p)).map(ProductSearchSpecDemo.Product::sku).toList();
        assertEquals(List.of("APL", "SPN"), skus);
    }

    @Test
    void likeWildcardsInUserTextAreEscaped() {
        assertEquals("100\\%", ProductSearchSpecDemo.escapeLike("100%"));
        assertEquals("a\\_b", ProductSearchSpecDemo.escapeLike("a_b"));
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProductSearchSpecDemo.fromRequest(Map.of("category", "toys")));
        assertThrows(IllegalArgumentException.class, () -> ProductSearchSpecDemo.fromRequest(Map.of("min_price", "-1")));
        assertThrows(IllegalArgumentException.class, () -> ProductSearchSpecDemo.fromRequest(Map.of("sort", "price")));
    }

    @Test
    void composedSpecsDescribeThemselves() {
        assertEquals("((gold tier or 5+ orders in 30 days) and verified email)", CompositionDemo.EARLY_ACCESS.describe());
        assertFalse(CompositionDemo.EARLY_ACCESS.isSatisfiedBy(new CompositionDemo.Customer("K", "gold", 9, false)));
    }

    @Test
    void riderRulesExplainEveryFailure() {
        var why = RiderAssignmentDemo.CAN_TAKE.whyNot(new RiderAssignmentDemo.Rider("C", false, 4, true, 4.1),
                                                      new RiderAssignmentDemo.Order("O", 1, false));
        assertEquals(List.of("not on shift", "more than 3 km away"), why);
        assertTrue(RiderAssignmentDemo.CAN_TAKE.isSatisfiedBy(new RiderAssignmentDemo.Rider("B", true, 2, true, 1), new RiderAssignmentDemo.Order("O", 2, true)));
    }
}
