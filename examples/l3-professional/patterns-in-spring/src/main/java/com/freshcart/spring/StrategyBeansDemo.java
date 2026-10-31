package com.freshcart.spring;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Strategy with Spring: declare each strategy as a bean, then inject List<Strategy> (ordered) or Map<String, Strategy> (by bean name). */
public class StrategyBeansDemo {

    public interface DeliveryFeeRule { long apply(long feePaise, long cartPaise); String name(); }

    @Order(1) static class BaseFee implements DeliveryFeeRule {
        public long apply(long fee, long cart) { return 2_500; } public String name() { return "base"; } }
    @Order(2) static class FreeAbove499 implements DeliveryFeeRule {
        public long apply(long fee, long cart) { return cart >= 49_900 ? 0 : fee; } public String name() { return "free>=499"; } }
    @Order(3) static class RainSurcharge implements DeliveryFeeRule {
        public long apply(long fee, long cart) { return fee + 1_000; } public String name() { return "rain"; } }

    public interface PaymentProcessor { String pay(long paise); }

    record FeeCalculator(List<DeliveryFeeRule> rules) {                                                 // (1)!
        long fee(long cartPaise) { long f = 0; for (DeliveryFeeRule r : rules) f = r.apply(f, cartPaise); return f; }
    }
    record PaymentRouter(Map<String, PaymentProcessor> byName) {                                       // (2)!
        String pay(String mode, long paise) {
            PaymentProcessor p = byName.get(mode);
            if (p == null) throw new IllegalArgumentException("unsupported payment mode '" + mode + "', supported " + byName.keySet());
            return p.pay(paise);
        }
    }

    @Configuration
    static class Config {
        @Bean RainSurcharge rain() { return new RainSurcharge(); }                                       // (3)!
        @Bean BaseFee base() { return new BaseFee(); }
        @Bean FreeAbove499 freeAbove499() { return new FreeAbove499(); }
        @Bean FeeCalculator feeCalculator(List<DeliveryFeeRule> rules) { return new FeeCalculator(rules); }

        @Bean PaymentProcessor upi() { return paise -> "UPI collect request for Rs " + paise / 100; }
        @Bean PaymentProcessor card() { return paise -> "card charge Rs " + paise / 100; }
        @Bean PaymentRouter paymentRouter(Map<String, PaymentProcessor> processors) { return new PaymentRouter(processors); }
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            FeeCalculator fees = ctx.getBean(FeeCalculator.class);
            System.out.println("rule order: " + fees.rules().stream().map(DeliveryFeeRule::name).toList());
            System.out.println("fee for Rs 300 cart: " + fees.fee(30_000) + ", for Rs 650 cart: " + fees.fee(65_000));
            PaymentRouter router = ctx.getBean(PaymentRouter.class);
            System.out.println(router.pay("upi", 64_900) + " | " + router.pay("card", 64_900));
            try { router.pay("cod", 100); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
        }
    }
}
