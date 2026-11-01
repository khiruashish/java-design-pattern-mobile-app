package com.freshcart.spring;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/** Application events are Observer: publishers don't know their listeners; any bean can listen with @EventListener. */
public class ApplicationEventsDemo {

    public record OrderPlaced(String orderId, long totalPaise) { }                                       // (1)!

    public static class CheckoutService {
        private final ApplicationEventPublisher events;
        public CheckoutService(ApplicationEventPublisher events) { this.events = events; }
        public void placeOrder(String id, long paise) {
            System.out.println("checkout: order " + id + " saved");
            events.publishEvent(new OrderPlaced(id, paise));                                             // (2)!
            System.out.println("checkout: publishEvent returned");
        }
    }

    public static class LoyaltyListener {
        final List<String> log;
        LoyaltyListener(List<String> log) { this.log = log; }
        @EventListener @Order(1) public void award(OrderPlaced e) { record("  loyalty listener: +" + e.totalPaise() / 10_000 + " points for " + e.orderId()); }
        void record(String line) { log.add(line); System.out.println(line); }
    }
    public static class SmsListener {
        final List<String> log;
        SmsListener(List<String> log) { this.log = log; }
        @EventListener(condition = "#e.totalPaise >= 50000") @Order(2)                                     // (3)!
        public void bigOrder(OrderPlaced e) { String line = "  sms listener: thanks for your big order " + e.orderId(); log.add(line); System.out.println(line); }
    }

    @Configuration
    static class Config {
        @Bean List<String> eventLog() { return new ArrayList<>(); }
        @Bean CheckoutService checkout(ApplicationEventPublisher p) { return new CheckoutService(p); }
        @Bean LoyaltyListener loyalty(List<String> eventLog) { return new LoyaltyListener(eventLog); }
        @Bean SmsListener sms(List<String> eventLog) { return new SmsListener(eventLog); }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            CheckoutService checkout = ctx.getBean(CheckoutService.class);
            checkout.placeOrder("FC-1", 64_900);
            checkout.placeOrder("FC-2", 12_000);
            System.out.println("listeners ran " + ((List<String>) ctx.getBean("eventLog")).size() + " times, synchronously, inside publishEvent");
        }
    }
}
