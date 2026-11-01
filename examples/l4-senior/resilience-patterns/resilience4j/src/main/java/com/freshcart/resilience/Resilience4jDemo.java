package com.freshcart.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Real Resilience4j: a PayLink call decorated with Bulkhead, CircuitBreaker and Retry, plus a fallback. */
public class Resilience4jDemo {

    static final class PayLinkUnavailable extends RuntimeException {
        PayLinkUnavailable() { super("PayLink 503"); }
    }

    public static void main(String[] args) {
        CircuitBreaker breaker = CircuitBreaker.of("paylink", CircuitBreakerConfig.custom()      // (1)!
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        Retry retry = Retry.of("paylink", RetryConfig.custom()                                     // (2)!
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(10), 2.0, 0.5))
                .retryExceptions(PayLinkUnavailable.class)
                .ignoreExceptions(CallNotPermittedException.class)
                .build());
        Bulkhead bulkhead = Bulkhead.of("paylink", BulkheadConfig.custom().maxConcurrentCalls(10).maxWaitDuration(Duration.ZERO).build());

        List<String> events = new ArrayList<>();
        breaker.getEventPublisher().onStateTransition(e -> events.add("breaker " + e.getStateTransition()));
        retry.getEventPublisher().onRetry(e -> events.add("retry attempt " + e.getNumberOfRetryAttempts()));

        int[] remoteCalls = {0};
        boolean[] up = {true};
        Supplier<String> paylink = () -> {
            remoteCalls[0]++;
            if (!up[0]) throw new PayLinkUnavailable();
            return "paid";
        };
        Supplier<String> decorated = Retry.decorateSupplier(retry,                                 // (3)!
                CircuitBreaker.decorateSupplier(breaker, Bulkhead.decorateSupplier(bulkhead, paylink)));

        System.out.println("FC-1 " + withFallback(decorated));
        up[0] = false;
        for (int i = 2; i <= 4; i++) System.out.println("FC-" + i + " " + withFallback(decorated));
        events.forEach(e -> System.out.println("  " + e));
        System.out.println("remote calls: " + remoteCalls[0] + ", breaker state: " + breaker.getState());
        CircuitBreaker.Metrics m = breaker.getMetrics();
        System.out.println("failure rate: " + m.getFailureRate() + "%, not permitted: " + m.getNumberOfNotPermittedCalls());
    }

    static String withFallback(Supplier<String> call) {                                           // (4)!
        try {
            return call.get();
        } catch (CallNotPermittedException e) {
            return "pending (breaker open, no call made)";
        } catch (PayLinkUnavailable e) {
            return "pending (PayLink unavailable after retries)";
        }
    }
}
