import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Realistic: FreshCart's GET /v2/customers/{id}/orders. Orders sort by (placedAt desc, id desc) because many orders
 * share a timestamp; the cursor encodes the last (placedAt, id) and the filter, and is signed so clients can't
 * forge or edit it.
 */
public class OrderHistoryApiDemo {

    record Order(String id, long placedAt, String status) {}
    record Page(List<String> items, String nextCursor) {}

    static final byte[] KEY = "demo-only-secret-rotate-me".getBytes(StandardCharsets.UTF_8);
    static final Comparator<Order> NEWEST_FIRST = Comparator.comparingLong(Order::placedAt).reversed().thenComparing(Order::id, Comparator.reverseOrder());

    static Page list(List<Order> all, String statusFilter, String cursor, int limit) {
        long afterTime = Long.MAX_VALUE; String afterId = "~"; String filter = statusFilter;
        if (cursor != null) {
            String[] parts = verify(cursor).split("\\|");
            afterTime = Long.parseLong(parts[0]); afterId = parts[1]; filter = parts[2];             // (1)!
        }
        final long t = afterTime; final String id = afterId; final String f = filter;
        List<Order> page = all.stream()
                .filter(o -> f.equals("ALL") || o.status().equals(f))
                .filter(o -> o.placedAt() < t || (o.placedAt() == t && o.id().compareTo(id) < 0))     // (2)!
                .sorted(NEWEST_FIRST).limit(limit + 1L).toList();
        boolean more = page.size() > limit;
        List<Order> shown = more ? page.subList(0, limit) : page;
        Order last = shown.isEmpty() ? null : shown.get(shown.size() - 1);
        String next = more ? sign(last.placedAt() + "|" + last.id() + "|" + f) : null;
        List<String> items = new ArrayList<>();
        shown.forEach(o -> items.add(o.id()));
        return new Page(items, next);
    }

    static String sign(String payload) {                                                           // (3)!
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + hmac(body);
    }
    static String verify(String cursor) {
        String[] parts = cursor.split("\\.");
        if (parts.length != 2 || !hmac(parts[0]).equals(parts[1])) throw new IllegalArgumentException("400 invalid cursor");
        return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    }
    static String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static void main(String[] args) {
        List<Order> orders = List.of(new Order("FC-101", 1000, "DELIVERED"), new Order("FC-102", 2000, "DELIVERED"),
                new Order("FC-103", 2000, "CANCELLED"), new Order("FC-104", 2000, "DELIVERED"),
                new Order("FC-105", 3000, "DELIVERED"), new Order("FC-106", 4000, "PLACED"));
        Page p1 = list(orders, "DELIVERED", null, 2);
        Page p2 = list(orders, "ignored: the cursor carries the filter", p1.nextCursor(), 2);
        System.out.println("page 1: " + p1.items() + " next=" + p1.nextCursor());
        System.out.println("page 2: " + p2.items() + " next=" + (p2.nextCursor() != null));
        String tampered = p1.nextCursor().replace(p1.nextCursor().substring(0, 4), "MXxG");
        try { list(orders, "ALL", tampered, 2); } catch (IllegalArgumentException e) { System.out.println("tampered cursor: " + e.getMessage()); }
    }
}
