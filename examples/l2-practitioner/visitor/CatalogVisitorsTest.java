import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogVisitorsTest {

    private static CatalogVisitorsDemo.Packaged packaged(String sku, String price) {
        return new CatalogVisitorsDemo.Packaged(sku, sku, new BigDecimal(price), "8901000000011");
    }

    @Test
    void pricesNestedBundlesWithDiscounts() {
        var inner = new CatalogVisitorsDemo.Bundle("B2", "inner", 50, List.of(packaged("A", "100")));      // 50
        var outer = new CatalogVisitorsDemo.Bundle("B1", "outer", 10, List.of(inner, packaged("B", "50"))); // (50 + 50) * 0.9
        assertEquals(0, new BigDecimal("90").compareTo(CatalogVisitorsDemo.fold(outer, CatalogVisitorsDemo.PRICE)));
    }

    @Test
    void jsonEscapesQuotes() {
        var p = new CatalogVisitorsDemo.Packaged("A", "Say \"hi\"", BigDecimal.ONE, "8901000000011");
        assertTrue(CatalogVisitorsDemo.fold(p, CatalogVisitorsDemo.JSON).contains("Say \\\"hi\\\""));
    }

    @Test
    void validationReportsEveryProblemWithItsPath() {
        var broken = new CatalogVisitorsDemo.Bundle("B1", "x", 40, List.of(
                new CatalogVisitorsDemo.Packaged("P1", "x", BigDecimal.ONE, "1"),
                new CatalogVisitorsDemo.Bundle("B2", "y", 0, List.of())));
        List<String> problems = CatalogVisitorsDemo.validate(broken);
        assertEquals(3, problems.size());
        assertTrue(problems.contains("B1 > B2: empty bundle"));
    }

    @Test
    void validProductHasNoProblems() {
        assertTrue(CatalogVisitorsDemo.validate(packaged("A", "1")).isEmpty());
    }
}
