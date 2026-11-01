import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CursorStreamTest {

    @Test
    void readsEveryRowExactlyOnceInOrder() {
        var m = new CursorStreamDemo.Metrics();
        try (Stream<CursorStreamDemo.Order> s = CursorStreamDemo.streamAll(new CursorStreamDemo.FakeTable(), 3, 0, m)) {
            assertEquals(java.util.stream.LongStream.rangeClosed(1, 10).boxed().toList(), s.map(CursorStreamDemo.Order::id).toList());
        }
        assertEquals(4, m.pages);
    }

    @Test
    void stopsFetchingWhenTheConsumerStops() {
        var m = new CursorStreamDemo.Metrics();
        try (var s = CursorStreamDemo.streamAll(new CursorStreamDemo.FakeTable(), 3, 0, m)) { s.limit(2).toList(); }
        assertEquals(1, m.pages);
        assertTrue(m.closed);
    }

    @Test
    void retriesTransientPageFailures() {
        var table = new CursorStreamDemo.FakeTable();
        table.failures = 1;
        var m = new CursorStreamDemo.Metrics();
        try (var s = CursorStreamDemo.streamAll(table, 5, 1, m)) { assertEquals(10, s.count()); }
        assertEquals(1, m.retries);
    }

    @Test
    void detectsACursorThatDoesNotMove() {
        var table = new CursorStreamDemo.FakeTable();
        table.broken = true;
        assertThrows(IllegalStateException.class, () -> {
            try (var s = CursorStreamDemo.streamAll(table, 4, 0, new CursorStreamDemo.Metrics())) { s.toList(); }
        });
    }
}
