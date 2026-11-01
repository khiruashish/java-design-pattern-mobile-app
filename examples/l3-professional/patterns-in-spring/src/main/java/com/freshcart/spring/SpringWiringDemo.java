package com.freshcart.spring;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Minimal: QuickDrop's ETA service wired by Spring. The classes are plain Java; Spring only builds and connects them. */
public class SpringWiringDemo {

    record TrafficClient(double slowdown) { }
    record EtaService(TrafficClient traffic) {                                                          // (1)!
        int minutes(double km) { return (int) Math.ceil(km * 3 * traffic.slowdown()); }
    }

    @Configuration
    static class Config {                                                                              // (2)!
        @Bean TrafficClient trafficClient() { return new TrafficClient(1.5); }
        @Bean EtaService etaService(TrafficClient traffic) { return new EtaService(traffic); }
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            EtaService eta = ctx.getBean(EtaService.class);
            System.out.println("4 km -> " + eta.minutes(4) + " min");
            System.out.println("without Spring, same result: " + new EtaService(new TrafficClient(1.5)).minutes(4) + " min");
        }
    }
}
