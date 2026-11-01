package com.freshcart.spring;

import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/** Spring's cross-cutting features (transactions, caching, security) are Proxies wrapped around your beans. */
public class ProxiesAopDemo {

    public interface StockService { int available(String sku); }

    // ---------- a proxy you build by hand with ProxyFactory ----------
    static StockService timed(StockService target, StringBuilder log) {                                // (1)!
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice((MethodInterceptor) invocation -> {
            log.append("before ").append(invocation.getMethod().getName()).append("; ");
            try { return invocation.proceed(); } finally { log.append("after; "); }
        });
        return (StockService) factory.getProxy();
    }

    // ---------- the proxy Spring builds for @Transactional ----------
    public static class InventoryService {
        private final JdbcTemplate jdbc;
        public InventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional                                                                                   // (2)!
        public void reserveAll(String... skus) {
            for (String sku : skus) {
                int updated = jdbc.update("UPDATE stock SET qty = qty - 1 WHERE sku = ? AND qty > 0", sku);
                if (updated == 0) throw new IllegalStateException(sku + " is out of stock");
            }
        }

        public void reserveAllViaSelfCall(String... skus) { reserveAll(skus); }                           // (3)!

        public int qty(String sku) { return jdbc.queryForObject("SELECT qty FROM stock WHERE sku = ?", Integer.class, sku); }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return Db.h2("classpath:/stock.sql");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager txManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean InventoryService inventoryService(JdbcTemplate jdbc) { return new InventoryService(jdbc); }
    }

    public static void main(String[] args) {
        StringBuilder log = new StringBuilder();
        StockService proxied = timed(sku -> 42, log);
        System.out.println("hand-made proxy: " + proxied.available("MILK") + " | " + log);

        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            InventoryService inventory = ctx.getBean(InventoryService.class);
            System.out.println("bean class is a CGLIB subclass: " + inventory.getClass().getSimpleName().contains("SpringCGLIB"));
            try { inventory.reserveAll("MILK", "PANEER"); } catch (IllegalStateException e) { System.out.println("through the proxy: " + e.getMessage()); }
            System.out.println("  MILK after rollback: " + inventory.qty("MILK"));                     // (4)!
            try { inventory.reserveAllViaSelfCall("MILK", "PANEER"); } catch (IllegalStateException e) { System.out.println("self-invocation: " + e.getMessage()); }
            System.out.println("  MILK after self-call: " + inventory.qty("MILK") + "   <- no transaction, partial update kept");
        }
    }
}
