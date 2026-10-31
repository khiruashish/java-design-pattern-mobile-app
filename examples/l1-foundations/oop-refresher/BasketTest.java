import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BasketTest {

    /** A hand-written fake: records events so the test can check them. */
    static final class RecordingEvents implements BasketDemo.BasketEvents {
        final List<String> log = new ArrayList<>();
        public void itemAdded(String basketId, BasketDemo.Line line) { log.add("added " + line.sku()); }
        public void itemRejected(String basketId, String sku, String reason) { log.add("rejected " + sku); }
    }

    private final BasketDemo.PriceCatalog catalog = new BasketDemo.InMemoryCatalog(
            Map.of("MILK-1L", new BasketDemo.Money(129)));
    private final RecordingEvents events = new RecordingEvents();

    @Test
    void totalsLinesUsingCatalogPrices() {
        var basket = new BasketDemo.Basket("B-1", 10, catalog, events);
        basket.add("MILK-1L", 3);
        assertEquals(new BasketDemo.Money(387), basket.total());
        assertEquals(List.of("added MILK-1L"), events.log);
    }

    @Test
    void rejectsItemsBeyondTheLimitAndReportsIt() {
        var basket = new BasketDemo.Basket("B-1", 2, catalog, events);
        assertThrows(BasketDemo.BasketLimitException.class, () -> basket.add("MILK-1L", 3));
        assertEquals(0, basket.itemCount());
        assertEquals(List.of("rejected MILK-1L"), events.log);
    }
}
