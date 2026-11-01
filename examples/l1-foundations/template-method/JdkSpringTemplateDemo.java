import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Template Method in the JDK (AbstractList) and the Spring JdbcTemplate/TransactionTemplate style, in miniature. */
public class JdkSpringTemplateDemo {

    // ===================== JDK: implement 2 primitive methods, inherit ~30 others =====================
    static final class DeliveryWindows extends AbstractList<String> {                    // (1)!
        private final int firstHour, lastHour;
        DeliveryWindows(int firstHour, int lastHour) { this.firstHour = firstHour; this.lastHour = lastHour; }
        @Override public String get(int i) {
            if (i < 0 || i >= size()) throw new IndexOutOfBoundsException(i);
            return "%02d:00-%02d:00".formatted(firstHour + i, firstHour + i + 1);
        }
        @Override public int size() { return lastHour - firstHour; }
    }

    // ===================== Spring-style: the template owns the resource, you supply the callback =====================
    static final class FakeConnection {
        final List<String> log = new ArrayList<>();
        void begin() { log.add("BEGIN"); }
        void exec(String sql) { log.add(sql); if (sql.contains("FAIL")) throw new IllegalStateException("constraint violated"); }
        void commit() { log.add("COMMIT"); }
        void rollback() { log.add("ROLLBACK"); }
        void close() { log.add("CLOSE"); }
    }

    static final class TxTemplate {
        <T> T inTransaction(FakeConnection c, Function<FakeConnection, T> work) {         // (2)!
            c.begin();
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (RuntimeException e) {
                c.rollback();                                                             // (3)!
                throw e;
            } finally {
                c.close();
            }
        }
    }

    public static void main(String[] args) {
        DeliveryWindows windows = new DeliveryWindows(8, 12);
        System.out.println("windows " + windows + ", contains 10:00-11:00? " + windows.contains("10:00-11:00")
                + ", indexOf 11:00-12:00 = " + windows.indexOf("11:00-12:00") + ", subList " + windows.subList(1, 3));

        TxTemplate tx = new TxTemplate();
        FakeConnection ok = new FakeConnection();
        int rows = tx.inTransaction(ok, c -> { c.exec("UPDATE stock SET qty = qty - 1 WHERE sku = 'MILK'"); return 1; });
        System.out.println("ok (" + rows + " row): " + ok.log);

        FakeConnection bad = new FakeConnection();
        try { tx.inTransaction(bad, c -> { c.exec("INSERT order"); c.exec("INSERT FAIL"); return 0; }); }
        catch (IllegalStateException e) { System.out.println("failed: " + bad.log + " -> " + e.getMessage()); }
    }
}
