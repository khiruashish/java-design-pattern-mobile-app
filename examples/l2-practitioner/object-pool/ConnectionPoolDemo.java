import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The ideas behind a JDBC connection pool like HikariCP, in miniature:
 * maximumPoolSize, connectionTimeout (wait for a free connection), and why holding a connection
 * during slow, non-database work starves everyone else.
 */
public class ConnectionPoolDemo {

    record Connection(int id) { String query(String sql) { return "conn" + id + ": " + sql; } }

    static final class MiniPool {
        private final BlockingQueue<Connection> free;
        MiniPool(int maximumPoolSize) {
            free = new ArrayBlockingQueue<>(maximumPoolSize);
            for (int i = 1; i <= maximumPoolSize; i++) free.add(new Connection(i));      // (1)!
        }
        Connection getConnection(long connectionTimeoutMs) throws InterruptedException {
            Connection c = free.poll(connectionTimeoutMs, TimeUnit.MILLISECONDS);         // (2)!
            if (c == null) throw new IllegalStateException("Connection is not available, request timed out after " + connectionTimeoutMs + "ms");
            return c;
        }
        void close(Connection c) { free.add(c); }
        int idle() { return free.size(); }
    }

    public static void main(String[] args) throws Exception {
        MiniPool pool = new MiniPool(2);

        // BAD: two requests hold connections while calling a slow payment API
        Connection a = pool.getConnection(100);
        Connection b = pool.getConnection(100);
        List<String> results = new ArrayList<>();
        try { pool.getConnection(100); } catch (IllegalStateException e) { results.add("third request: " + e.getMessage()); }   // (3)!
        pool.close(a); pool.close(b);

        // GOOD: borrow, query, return quickly; do slow work without a connection
        for (String order : List.of("FC-1", "FC-2", "FC-3", "FC-4")) {
            Connection c = pool.getConnection(100);
            try { results.add(c.query("SELECT total FROM orders WHERE id='" + order + "'")); }
            finally { pool.close(c); }                                                    // (4)!
        }
        results.forEach(System.out::println);
        System.out.println("idle connections at the end: " + pool.idle());
    }
}
