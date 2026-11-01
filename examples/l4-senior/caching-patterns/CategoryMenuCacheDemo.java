import java.util.List;

/** Minimal: the app's category menu changes a few times a day, so one cached copy with a 5-minute TTL is plenty. */
public class CategoryMenuCacheDemo {

    static List<String> cachedMenu; static long cachedAt = Long.MIN_VALUE; static int dbQueries;
    static final long TTL_SECONDS = 300;

    static List<String> menu(long nowSeconds) {
        if (cachedMenu == null || nowSeconds - cachedAt >= TTL_SECONDS) {                             // (1)!
            dbQueries++;
            cachedMenu = List.of("Dairy", "Fruits", "Vegetables", "Bakery");
            cachedAt = nowSeconds;                                                                   // (2)!
        }
        return cachedMenu;
    }

    public static void main(String[] args) {
        for (long t = 0; t < 900; t += 1) menu(t);       // one app open per second for 15 minutes
        System.out.println("900 menu requests -> " + dbQueries + " database queries");
        System.out.println(menu(900));
    }
}
