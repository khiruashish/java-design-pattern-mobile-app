package com.freshcart.spring;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/**
 * Realistic: FreshCart checkout as Spring wires it in production. Payment strategies are beans in a Map,
 * the checkout is a transactional proxy around JdbcTemplate calls, and follow-up work listens for an event.
 */
public class CheckoutFlowDemo {

    public interface PaymentProcessor { String charge(String orderId, long paise); }
    public record OrderPaid(String orderId, String mode) { }

    public static class CheckoutService {
        private final JdbcTemplate jdbc; private final Map<String, PaymentProcessor> processors; private final ApplicationEventPublisher events;
        public CheckoutService(JdbcTemplate jdbc, Map<String, PaymentProcessor> processors, ApplicationEventPublisher events) {
            this.jdbc = jdbc; this.processors = processors; this.events = events;
        }

        @Transactional                                                                                   // (1)!
        public String pay(String orderId, String mode) {
            long total = jdbc.queryForObject("SELECT total_paise FROM orders WHERE id = ?", Long.class, orderId);
            PaymentProcessor processor = processors.get(mode);                                         // (2)!
            if (processor == null) throw new IllegalArgumentException("unsupported mode " + mode);
            String ref = processor.charge(orderId, total);
            jdbc.update("INSERT INTO payments VALUES (?, ?, ?)", orderId, mode, ref);
            jdbc.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId);
            events.publishEvent(new OrderPaid(orderId, mode));
            return ref;
        }
    }

    public static class ReceiptListener {
        @EventListener public void onPaid(OrderPaid e) { System.out.println("  receipt queued for " + e.orderId() + " (" + e.mode() + ")"); }   // (3)!
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return Db.h2("classpath:/orders.sql"); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean PaymentProcessor upi() { return (id, p) -> "UPI-" + id; }
        @Bean PaymentProcessor card() { return (id, p) -> { if (p > 50_000) throw new IllegalStateException("card limit exceeded"); return "CARD-" + id; }; }
        @Bean CheckoutService checkout(JdbcTemplate jdbc, Map<String, PaymentProcessor> ps, ApplicationEventPublisher ev) { return new CheckoutService(jdbc, ps, ev); }
        @Bean ReceiptListener receipts() { return new ReceiptListener(); }
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            CheckoutService checkout = ctx.getBean(CheckoutService.class);
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            System.out.println("FC-2 by upi: " + checkout.pay("FC-2", "upi"));
            try { checkout.pay("FC-1", "card"); } catch (IllegalStateException e) { System.out.println("FC-1 by card failed: " + e.getMessage()); }
            System.out.println("payments rows: " + jdbc.queryForList("SELECT order_id FROM payments", String.class)
                    + ", FC-1 status: " + jdbc.queryForObject("SELECT status FROM orders WHERE id = 'FC-1'", String.class));
        }
    }
}
