import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC is the textbook Bridge. Your code uses the abstraction (DriverManager, Connection, Statement);
 * each database vendor ships an implementor (a Driver). Mini version below; real JDBC works the same way.
 */
public class JdbcBridgeDemo {

    // ===================== Implementor interface (what vendors implement) =====================
    interface Driver { boolean acceptsUrl(String url); Connection connect(String url); }     // (1)!
    interface Connection { List<String> query(String sql); }

    // ===================== Abstraction (what application code uses) =====================
    static final class DriverManager {                                                       // (2)!
        private static final List<Driver> DRIVERS = new ArrayList<>();
        static void registerDriver(Driver d) { DRIVERS.add(d); }
        static Connection getConnection(String url) {
            return DRIVERS.stream().filter(d -> d.acceptsUrl(url)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No suitable driver found for " + url)).connect(url);
        }
    }

    // ===================== Two "vendors" =====================
    static final class MemDbDriver implements Driver {
        public boolean acceptsUrl(String url) { return url.startsWith("jdbc:memdb:"); }
        public Connection connect(String url) { return sql -> List.of("memdb row for '" + sql + "'"); }
    }
    static final class CsvDriver implements Driver {
        final Map<String, List<String>> files = new LinkedHashMap<>(Map.of("stock", List.of("MILK,40", "BREAD,12")));
        public boolean acceptsUrl(String url) { return url.startsWith("jdbc:csv:"); }
        public Connection connect(String url) { return sql -> files.getOrDefault(sql.substring(sql.lastIndexOf(' ') + 1), List.of()); }
    }

    // ===================== Application code: depends only on the abstraction =====================
    static List<String> loadStock(String url) {                                              // (3)!
        Connection c = DriverManager.getConnection(url);
        return c.query("SELECT * FROM stock");
    }

    public static void main(String[] args) {
        DriverManager.registerDriver(new MemDbDriver());                                       // (4)!
        DriverManager.registerDriver(new CsvDriver());
        System.out.println("memdb: " + loadStock("jdbc:memdb:freshcart"));
        System.out.println("csv:   " + loadStock("jdbc:csv:/data/freshcart"));
        try { loadStock("jdbc:otherdb://reports-db"); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
    }
}
