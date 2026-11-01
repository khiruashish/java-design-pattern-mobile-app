import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Production-grade iteration over a large table: keyset ("seek") pagination exposed as a lazy Stream.
 * - pages are fetched only as the stream pulls (limit/findFirst stop early)
 * - keyset cursor (WHERE id > lastId ORDER BY id LIMIT n): stable under inserts, unlike OFFSET
 * - a page fetch is retried on transient errors; a cursor that doesn't move is detected (no infinite loop)
 * - onClose releases resources; metrics record pages and rows
 */
public class CursorStreamDemo {

    record Order(long id, String customer, long paise) { }

    interface OrderTable { List<Order> after(long lastId, int limit); }                     // (1)!

    static final class Metrics { int pages, rows, retries; boolean closed; public String toString() { return "pages=" + pages + " rows=" + rows + " retries=" + retries + " closed=" + closed; } }

    static Stream<Order> streamAll(OrderTable table, int pageSize, int maxRetries, Metrics m) {
        Spliterator<Order> split = new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.DISTINCT) {
            private List<Order> page = List.of(); private int i; private long lastId = 0; private boolean exhausted;

            @Override public boolean tryAdvance(Consumer<? super Order> action) {
                if (i == page.size()) {
                    if (exhausted) return false;
                    page = fetchWithRetry(lastId);                                             // (2)!
                    i = 0; m.pages++;
                    if (page.size() < pageSize) exhausted = true;
                    if (page.isEmpty()) return false;
                    long newLast = page.get(page.size() - 1).id();
                    if (newLast <= lastId) throw new IllegalStateException("cursor did not advance past id " + lastId);   // (3)!
                    lastId = newLast;
                }
                m.rows++;
                action.accept(page.get(i++));
                return true;
            }

            private List<Order> fetchWithRetry(long after) {
                for (int attempt = 1; ; attempt++) {
                    try { return table.after(after, pageSize); }
                    catch (IllegalStateException e) {
                        if (attempt > maxRetries) throw e;
                        m.retries++;                                                           // (4)!
                    }
                }
            }
        };
        return StreamSupport.stream(split, false).onClose(() -> m.closed = true);              // (5)!
    }

    /** Fake table: 10 orders, ids 1..10; optionally fails N times, optionally returns a broken (non-advancing) page. */
    static final class FakeTable implements OrderTable {
        final List<Order> rows = new ArrayList<>(); int failures; boolean broken;
        FakeTable() { for (long id = 1; id <= 10; id++) rows.add(new Order(id, id % 2 == 0 ? "asha" : "ravi", 10_000 * id)); }
        public List<Order> after(long lastId, int limit) {
            if (failures-- > 0) throw new IllegalStateException("db timeout");
            if (broken && lastId > 0) return rows.subList(0, limit);
            return rows.stream().filter(o -> o.id() > lastId).limit(limit).toList();
        }
    }

    public static void main(String[] args) {
        Metrics m1 = new Metrics();
        try (Stream<Order> s = streamAll(new FakeTable(), 4, 2, m1)) {
            System.out.println("first asha order over Rs 300: " + s.filter(o -> o.customer().equals("asha") && o.paise() > 30_000).findFirst().orElseThrow());
        }
        System.out.println("  " + m1);

        Metrics m2 = new Metrics();
        FakeTable flaky = new FakeTable(); flaky.failures = 2;
        try (Stream<Order> s = streamAll(flaky, 4, 3, m2)) {
            System.out.println("export total: Rs " + s.collect(Collectors.summingLong(Order::paise)) / 100);
        }
        System.out.println("  " + m2);

        FakeTable broken = new FakeTable(); broken.broken = true;
        try (Stream<Order> s = streamAll(broken, 4, 0, new Metrics())) { s.forEach(o -> { }); }
        catch (IllegalStateException e) { System.out.println("broken cursor stopped: " + e.getMessage()); }
    }
}
