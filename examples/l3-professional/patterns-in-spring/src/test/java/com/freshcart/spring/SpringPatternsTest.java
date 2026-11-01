package com.freshcart.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spring behaviour the playbook relies on, checked against the real framework. */
class SpringPatternsTest {

    @Test
    void transactionalProxyRollsBackButSelfInvocationDoesNot() {
        try (var ctx = new AnnotationConfigApplicationContext(ProxiesAopDemo.Config.class)) {
            var inventory = ctx.getBean(ProxiesAopDemo.InventoryService.class);
            assertThrows(IllegalStateException.class, () -> inventory.reserveAll("MILK", "PANEER"));
            assertEquals(10, inventory.qty("MILK"));
            assertThrows(IllegalStateException.class, () -> inventory.reserveAllViaSelfCall("MILK", "PANEER"));
            assertEquals(9, inventory.qty("MILK"));
        }
    }

    @Test
    void listInjectionHonoursOrderAndMapInjectionUsesBeanNames() {
        try (var ctx = new AnnotationConfigApplicationContext(StrategyBeansDemo.Config.class)) {
            var fees = ctx.getBean(StrategyBeansDemo.FeeCalculator.class);
            assertEquals(List.of("base", "free>=499", "rain"), fees.rules().stream().map(StrategyBeansDemo.DeliveryFeeRule::name).toList());
            assertEquals(List.of("upi", "card"), List.copyOf(ctx.getBean(StrategyBeansDemo.PaymentRouter.class).byName().keySet()));
        }
    }

    @Test
    void afterCommitListenerSkipsRolledBackOrders() throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(OrderPlacementProductionDemo.Config.class)) {
            var orders = ctx.getBean(OrderPlacementProductionDemo.OrderService.class);
            var notifications = ctx.getBean(OrderPlacementProductionDemo.Notifications.class);
            orders.place("T-1", OrderPlacementProductionDemo.Mode.UPI, 100);
            assertThrows(OrderPlacementProductionDemo.PaymentDeclined.class, () -> orders.place("T-2", OrderPlacementProductionDemo.Mode.CARD, 90_000));
            assertEquals(List.of("T-1"), notifications.sent);
            assertEquals(List.of("T-1"), ctx.getBean(JdbcTemplate.class).queryForList("SELECT id FROM orders WHERE id LIKE 'T-%'", String.class));
        }
    }

    @Test
    void missingStrategyBeanFailsStartup() {
        var e = assertThrows(Exception.class, () -> new AnnotationConfigApplicationContext(OrderPlacementProductionDemo.BrokenConfig.class).close());
        assertTrue(OrderPlacementProductionDemo.rootMessage(e).contains("CARD"));
    }

    @Test
    void factoryBeanIsCalledOncePerSingleton() {
        System.setProperty("env", "dev");
        try (var ctx = new AnnotationConfigApplicationContext(FactoryBeanDemo.Config.class)) {
            ctx.getBean(FactoryBeanDemo.SmsClient.class);
            ctx.getBean(FactoryBeanDemo.SmsClient.class);
            assertEquals(1, ((FactoryBeanDemo.SmsClientFactoryBean) ctx.getBean("&smsClient")).created);
        }
    }
}
