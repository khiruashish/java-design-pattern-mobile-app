package com.freshcart.spring;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring's *Template classes: the fixed algorithm (open, run, map, translate errors, close) is theirs; your callback is the varying step. */
public class TemplateClassesDemo {

    record OrderRow(String id, String city, long totalPaise) { }

    // ---------- before: raw JDBC; the useful line is buried in resource handling ----------
    static List<OrderRow> deliveredBefore(DataSource ds, String city) throws SQLException {           // (1)!
        List<OrderRow> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, city, total_paise FROM orders WHERE status = 'DELIVERED' AND city = ? ORDER BY id")) {
            ps.setString(1, city);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new OrderRow(rs.getString("id"), rs.getString("city"), rs.getLong("total_paise")));
            }
        }
        return out;
    }

    // ---------- after: JdbcTemplate (Template Method with a callback) ----------
    static List<OrderRow> delivered(JdbcTemplate jdbc, String city) {
        return jdbc.query("SELECT id, city, total_paise FROM orders WHERE status = 'DELIVERED' AND city = ? ORDER BY id",
                (rs, rowNum) -> new OrderRow(rs.getString("id"), rs.getString("city"), rs.getLong("total_paise")), city);   // (2)!
    }

    public static void main(String[] args) throws SQLException {
        DataSource ds = Db.h2("classpath:/orders.sql");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        System.out.println("raw JDBC:     " + deliveredBefore(ds, "Pune"));
        System.out.println("JdbcTemplate: " + delivered(jdbc, "Pune"));

        try { jdbc.update("INSERT INTO orders VALUES ('FC-1', 'Pune', 1, 'NEW')"); }
        catch (DataAccessException e) { System.out.println("SQL error translated to: " + e.getClass().getSimpleName()); }   // (3)!

        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE orders SET status = 'REFUNDED' WHERE id = 'FC-2'");
                throw new IllegalStateException("payment gateway refused refund");
            });
        } catch (IllegalStateException e) { System.out.println("TransactionTemplate rolled back: " + e.getMessage()); }
        System.out.println("FC-2 status: " + jdbc.queryForObject("SELECT status FROM orders WHERE id = 'FC-2'", String.class));
    }
}
