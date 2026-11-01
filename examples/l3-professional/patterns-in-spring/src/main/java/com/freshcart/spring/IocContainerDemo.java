package com.freshcart.spring;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

/** The IoC container: you declare objects and their dependencies; Spring creates and wires them (Factory + DI). */
public class IocContainerDemo {

    interface SmsSender { String send(String to, String text); }
    record TextBlastSender() implements SmsSender { public String send(String to, String text) { return "textblast -> " + to; } }
    record BackupSender() implements SmsSender { public String send(String to, String text) { return "backup -> " + to; } }

    static final class CartSession { final java.util.List<String> items = new java.util.ArrayList<>(); }

    record OrderNotifier(SmsSender primary, SmsSender fallback) {                                       // (1)!
        String orderPlaced(String phone) { return primary.send(phone, "placed"); }
    }

    @Configuration
    static class AppConfig {                                                                          // (2)!
        @Bean @Primary SmsSender textBlast() { return new TextBlastSender(); }
        @Bean SmsSender backup() { return new BackupSender(); }
        @Bean OrderNotifier notifier(SmsSender primary, @Qualifier("backup") SmsSender fallback) { return new OrderNotifier(primary, fallback); }
        @Bean @Scope("prototype") CartSession cartSession() { return new CartSession(); }             // (3)!
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderNotifier notifier = ctx.getBean(OrderNotifier.class);
            System.out.println(notifier.orderPlaced("98450") + " (fallback is " + notifier.fallback().getClass().getSimpleName() + ")");
            System.out.println("singleton: same notifier each time? " + (notifier == ctx.getBean(OrderNotifier.class)));
            System.out.println("prototype: new cart session each time? " + (ctx.getBean(CartSession.class) != ctx.getBean(CartSession.class)));
            System.out.println("@Configuration class is itself proxied: " + ctx.getBean(AppConfig.class).getClass().getSimpleName().contains("SpringCGLIB"));
        }
    }
}
