import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;

/**
 * Pagination: offset/limit is simple but skips or repeats items when data changes between pages, and gets slower
 * for deep pages. Cursor (keyset) pagination continues "after the last item seen" and stays correct and fast.
 */
public class PaginationDemo {

    static final TreeMap<Integer, String> orders = new TreeMap<>();   // order number -> description, newest has highest number

    static List<String> offsetPage(int offset, int limit) {                                     // (1)!
        return orders.descendingMap().values().stream().skip(offset).limit(limit).toList();
    }

    record Page(List<String> items, String nextCursor) {}
    static Page cursorPage(String cursor, int limit) {
        int before = cursor == null ? Integer.MAX_VALUE
                : Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));   // (2)!
        List<String> items = new ArrayList<>();
        int last = -1;
        for (var e : orders.headMap(before, false).descendingMap().entrySet()) {
            if (items.size() == limit) break;
            items.add(e.getValue()); last = e.getKey();
        }
        String next = items.size() < limit ? null
                : Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(last).getBytes(StandardCharsets.UTF_8));   // (3)!
        return new Page(items, next);
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 7; i++) orders.put(i, "FC-" + i);
        List<String> p1 = offsetPage(0, 3);
        Page c1 = cursorPage(null, 3);
        orders.put(8, "FC-8");                       // a new order arrives while the customer is scrolling
        System.out.println("offset:  page1=" + p1 + " page2=" + offsetPage(3, 3) + "  (FC-5 shown twice)");
        System.out.println("cursor:  page1=" + c1.items() + " page2=" + cursorPage(c1.nextCursor(), 3).items() + "  next cursor=" + c1.nextCursor());
    }
}
