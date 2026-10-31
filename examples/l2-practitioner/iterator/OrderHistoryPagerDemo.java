import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Realistic Iterator: the app's order history comes from a cursor-paginated API; the iterator fetches pages lazily. */
public class OrderHistoryPagerDemo {

    record Page(List<String> orders, String nextCursor) { }

    interface OrdersApi { Page list(String customer, String cursor, int pageSize); }        // (1)!

    static final class FakeOrdersApi implements OrdersApi {
        private final List<String> all = List.of("FC-9007", "FC-9006", "FC-9005", "FC-9004", "FC-9003", "FC-9002", "FC-9001");
        int calls;
        public Page list(String customer, String cursor, int size) {
            calls++;
            int from = cursor == null ? 0 : Integer.parseInt(cursor);
            int to = Math.min(all.size(), from + size);
            return new Page(all.subList(from, to), to < all.size() ? String.valueOf(to) : null);
        }
    }

    static Iterable<String> history(OrdersApi api, String customer, int pageSize) {
        return () -> new Iterator<>() {                                                        // (2)!
            private Iterator<String> current = List.<String>of().iterator();
            private String cursor; private boolean last;
            public boolean hasNext() {
                while (!current.hasNext() && !last) {
                    Page p = api.list(customer, cursor, pageSize);
                    current = p.orders().iterator();
                    cursor = p.nextCursor();
                    last = cursor == null;                                                     // (3)!
                }
                return current.hasNext();
            }
            public String next() { if (!hasNext()) throw new NoSuchElementException(); return current.next(); }
        };
    }

    public static void main(String[] args) {
        FakeOrdersApi api = new FakeOrdersApi();
        for (String id : history(api, "asha", 3)) {
            System.out.println("show " + id);
            if (id.equals("FC-9004")) break;                                                     // the customer stopped scrolling
        }
        System.out.println("API calls: " + api.calls + " (the last page was never requested)");

        FakeOrdersApi api2 = new FakeOrdersApi();
        int count = 0; for (String ignored : history(api2, "asha", 3)) count++;
        System.out.println("full history: " + count + " orders in " + api2.calls + " calls");
    }
}
