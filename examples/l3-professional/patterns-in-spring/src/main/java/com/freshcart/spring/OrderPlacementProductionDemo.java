package com.freshcart.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Production: PayLink order placement with the Spring patterns that matter after the first outage.
 * - every payment mode must have a strategy bean, checked when the context starts
 * - checked exceptions roll back too (rollbackFor), so no half-written orders
 * - the customer SMS listens AFTER_COMMIT, so a rolled-back order never sends "order confirmed"
 * - an @Audited annotation adds an audit proxy through a BeanPostProcessor (the mechanism behind Spring AOP)
 */
public class OrderPlacementProductionDemo {

    @Inherited @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) public @interface Audited { }
    public enum Mode { UPI, CARD }
    public interface PaymentProcessor { Mode mode(); String charge(String orderId, long paise) throws PaymentDeclined; }
    public static class PaymentDeclined extends Exception { public PaymentDeclined(String m) { super(m); } }
    public record OrderConfirmed(String orderId) { }

    public static class PaymentRouter {
        private final Map<Mode, PaymentProcessor> byMode;
        public PaymentRouter(List<PaymentProcessor> processors) {                                          // (1)!
            byMode = processors.stream().collect(Collectors.toMap(PaymentProcessor::mode, p -> p));
            Set<Mode> missing = new TreeSet<>(Arrays.asList(Mode.values()));
            missing.removeAll(byMode.keySet());
            if (!missing.isEmpty()) throw new IllegalStateException("no PaymentProcessor bean for " + missing);
        }
        PaymentProcessor forMode(Mode m) { return byMode.get(m); }
    }

    @Audited
    public static class OrderService {
        private final JdbcTemplate jdbc; private final PaymentRouter router; private final ApplicationEventPublisher events;
        public OrderService(JdbcTemplate jdbc, PaymentRouter router, ApplicationEventPublisher events) { this.jdbc = jdbc; this.router = router; this.events = events; }

        @Transactional(rollbackFor = Exception.class)                                                     // (2)!
        public String place(String orderId, Mode mode, long paise) throws PaymentDeclined {
            jdbc.update("INSERT INTO orders VALUES (?, 'Pune', ?, 'PLACED')", orderId, paise);
            events.publishEvent(new OrderConfirmed(orderId));
            String ref = router.forMode(mode).charge(orderId, paise);                                   // may throw a CHECKED exception
            jdbc.update("INSERT INTO payments VALUES (?, ?, ?)", orderId, mode.name(), ref);
            return ref;
        }
    }

    public static class Notifications {
        final List<String> sent = new ArrayList<>(), naive = new ArrayList<>();
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)                                // (3)!
        public void sms(OrderConfirmed e) { sent.add(e.orderId()); }
        @EventListener                                                                                   // (4)!
        public void naiveSms(OrderConfirmed e) { naive.add(e.orderId()); }
    }

    static BeanPostProcessor auditing(List<String> auditLog) {                                           // (5)!
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean, String name) {
                if (!bean.getClass().isAnnotationPresent(Audited.class)) return bean;
                ProxyFactory f = new ProxyFactory(bean);
                f.setProxyTargetClass(true);
                f.addAdvice((MethodInterceptor) inv -> {
                    try { Object r = inv.proceed(); auditLog.add(name + "." + inv.getMethod().getName() + " ok"); return r; }
                    catch (Throwable t) { auditLog.add(name + "." + inv.getMethod().getName() + " failed: " + t.getMessage()); throw t; }
                });
                return f.getProxy();
            }
        };
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean static BeanPostProcessor auditProcessor() { return auditing(AUDIT); }
        @Bean DataSource dataSource() { return Db.h2("classpath:/orders.sql"); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean PaymentProcessor upi() { return processor(Mode.UPI); }
        @Bean PaymentProcessor card() { return processor(Mode.CARD); }
        @Bean PaymentRouter router(List<PaymentProcessor> ps) { return new PaymentRouter(ps); }
        @Bean OrderService orders(JdbcTemplate jdbc, PaymentRouter r, ApplicationEventPublisher ev) { return new OrderService(jdbc, r, ev); }
        @Bean Notifications notifications() { return new Notifications(); }
    }
    @Configuration
    @EnableTransactionManagement
    static class BrokenConfig {
        @Bean PaymentProcessor upi() { return processor(Mode.UPI); }
        @Bean PaymentRouter router(List<PaymentProcessor> ps) { return new PaymentRouter(ps); }
    }

    static final List<String> AUDIT = new ArrayList<>();

    static PaymentProcessor processor(Mode mode) {
        return new PaymentProcessor() {
            public Mode mode() { return mode; }
            public String charge(String id, long paise) throws PaymentDeclined {
                if (mode == Mode.CARD && paise > 50_000) throw new PaymentDeclined("card declined: limit");
                return mode + "-" + id;
            }
        };
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            OrderService orders = ctx.getBean(OrderService.class);
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            Notifications n = ctx.getBean(Notifications.class);
            try { System.out.println("FC-10 paid: " + orders.place("FC-10", Mode.UPI, 40_000)); } catch (PaymentDeclined e) { System.out.println(e.getMessage()); }
            try { orders.place("FC-11", Mode.CARD, 90_000); } catch (PaymentDeclined e) { System.out.println("FC-11: " + e.getMessage()); }
            System.out.println("orders in DB: " + jdbc.queryForList("SELECT id FROM orders WHERE id LIKE 'FC-1_' ORDER BY id", String.class));
            System.out.println("SMS after commit: " + n.sent + " | naive listener would have sent: " + n.naive);
            System.out.println("audit: " + AUDIT);
        }
        try (var broken = new AnnotationConfigApplicationContext(BrokenConfig.class)) { System.out.println("started?!"); }
        catch (Exception e) { System.out.println("startup refused: " + rootMessage(e)); }
    }

    static String rootMessage(Throwable t) { while (t.getCause() != null) t = t.getCause(); return t.getMessage(); }
}
