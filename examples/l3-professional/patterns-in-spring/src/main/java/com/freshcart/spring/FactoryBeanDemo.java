package com.freshcart.spring;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** FactoryBean: a bean whose job is to build another bean, for objects with complex or conditional construction. */
public class FactoryBeanDemo {

    public interface SmsClient { String send(String to); }
    record HttpSmsClient(String baseUrl, int timeoutMs) implements SmsClient { public String send(String to) { return "POST " + baseUrl + " to " + to + " (timeout " + timeoutMs + " ms)"; } }
    record LoggingSmsClient() implements SmsClient { public String send(String to) { return "LOG ONLY: sms to " + to; } }

    public static class SmsClientFactoryBean implements FactoryBean<SmsClient> {                         // (1)!
        private final String environment;
        int created;
        public SmsClientFactoryBean(String environment) { this.environment = environment; }
        @Override public SmsClient getObject() {
            created++;
            return environment.equals("prod") ? new HttpSmsClient("https://api.textblast.example/v2/sms", 2_000) : new LoggingSmsClient();
        }
        @Override public Class<?> getObjectType() { return SmsClient.class; }
    }

    @Configuration
    static class Config {
        @Bean SmsClientFactoryBean smsClient() { return new SmsClientFactoryBean(System.getProperty("env", "dev")); }   // (2)!
    }

    public static void main(String[] args) {
        for (String env : new String[] {"dev", "prod"}) {
            System.setProperty("env", env);
            try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
                SmsClient client = ctx.getBean(SmsClient.class);
                ctx.getBean(SmsClient.class);
                SmsClientFactoryBean factory = (SmsClientFactoryBean) ctx.getBean("&smsClient");          // (3)!
                System.out.println(env + ": " + client.send("98450") + " | factory called " + factory.created + "x");
            }
        }
    }
}
