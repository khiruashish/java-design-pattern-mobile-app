import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Change data capture: read the database's own change log (Postgres WAL, MySQL binlog) and turn each committed row
 * change into an event, without touching application code. The reader stores its log position to resume.
 */
public class ChangeDataCaptureDemo {

    record LogEntry(long lsn, String table, String op, Map<String, String> before, Map<String, String> after) {}

    static final class Database {                                                               // (1)!
        final Map<String, Map<String, String>> products = new LinkedHashMap<>();
        final List<LogEntry> wal = new ArrayList<>();
        void upsert(String sku, String price) {
            Map<String, String> before = products.get(sku);
            Map<String, String> after = Map.of("sku", sku, "price", price);
            products.put(sku, after);
            wal.add(new LogEntry(wal.size() + 1, "products", before == null ? "c" : "u", before, after));
        }
        void delete(String sku) { wal.add(new LogEntry(wal.size() + 1, "products", "d", products.remove(sku), null)); }
    }

    static final class CdcConnector {
        long committedLsn;                                                                       // (2)!
        List<String> poll(Database db, int max) {
            List<String> out = new ArrayList<>();
            for (LogEntry e : db.wal) {
                if (e.lsn() <= committedLsn || out.size() == max) continue;
                out.add("lsn=" + e.lsn() + " op=" + e.op() + " before=" + price(e.before()) + " after=" + price(e.after()));   // (3)!
                committedLsn = e.lsn();
            }
            return out;
        }
        static String price(Map<String, String> row) { return row == null ? "null" : row.get("sku") + "@" + row.get("price"); }
    }

    public static void main(String[] args) {
        Database db = new Database();
        CdcConnector connector = new CdcConnector();
        db.upsert("MILK-1L", "68");
        db.upsert("CURD-500G", "45");
        connector.poll(db, 10).forEach(System.out::println);
        db.upsert("MILK-1L", "70");
        db.delete("CURD-500G");
        db.upsert("PANEER-200G", "95");
        System.out.println("-- connector restarts, resumes after lsn " + connector.committedLsn);
        connector.poll(db, 2).forEach(System.out::println);
        connector.poll(db, 2).forEach(System.out::println);
    }
}
