# Resilience Patterns

L4 🟠 Senior / Lead · ⏱ 35 min read · Prerequisites: [Microservice Patterns](microservice-patterns.md), [Error Handling Patterns](../l3-professional/error-handling-patterns.md)

> 📌 **TL;DR**  
> Every remote dependency will be slow or down at some point; resilience patterns stop that from taking your service with it. A **timeout** bounds how long you wait. A **retry with exponential backoff and jitter** recovers from transient failures without a thundering herd, and only for idempotent operations. A **circuit breaker** stops calling a failing dependency and fails fast until it recovers. A **bulkhead** caps concurrency per dependency so one slow service can't use all your threads. A **rate limiter** keeps you within a quota or protects you from overload. A **fallback** returns something useful (cached, default, degraded) instead of an error. **Resilience4j** provides all of them as composable decorators; the order you compose them in matters.

## 1. What It Is

**Plain English.** When FreshCart calls PayLink, TextBlast or its own Inventory service, things go wrong: slow responses, errors, outages, quota limits. These patterns are small, well-known pieces of client-side logic that decide how long to wait, whether to try again, when to stop trying, how much to send at once, and what to return when the answer can't be had.

**Definition.**

| Pattern | Question it answers |
|---------|---------------------|
| Timeout | How long will I wait? |
| Retry with backoff and jitter | Should I try again, and when? |
| Circuit breaker | Should I even call it right now? |
| Bulkhead | How many concurrent calls may this dependency hold? |
| Rate limiter | How many calls per second may I make (or accept)? |
| Fallback | What do I return when the call fails? |

## 2. Why It Exists

In a distributed system, failure is partial and constant. Without timeouts, a hung dependency holds threads until the caller is also hung; without limits, retries from thousands of clients turn a blip into an outage; without isolation, one slow feature takes down unrelated ones. Michael Nygard's *Release It!* (2007) catalogued these "stability patterns" from real outages; Netflix's Hystrix (2012) made them a library, and Resilience4j is its modern, functional successor for Java.

## 3. Mental Model / Analogy

**Analogy.** A house's electrical system. A timeout is a kitchen timer: stop waiting after five minutes. Retry with jitter is redialling a busy number after a random pause, not all at the same second. A circuit breaker trips when there's a fault and is reset after a while. Bulkheads are separate circuits: a fault in the garage doesn't cut the fridge. A rate limiter is a water meter's flow cap. A fallback is the candle in the drawer.

**Technical model.** Each pattern wraps a call (`Supplier<T>`) and returns a decorated call; patterns are composed like decorators, with state (failure counts, permits, tokens) shared per dependency.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

The second diagram is Resilience4j's default aspect order: each retry attempt passes through the breaker (so failures are counted and an open breaker stops retries), and each attempt has its own timeout.

## 5. Subtopics

### Timeout

**Description.** Put an upper bound on every remote call: connect timeout, read timeout and an overall deadline per request. When it expires, the caller stops waiting and fails or falls back. Timeouts should be derived from the dependency's latency distribution (for example a little above its p99) and from the caller's own deadline.

**Advantages / features.** Bounded latency; threads and connections are returned; failures become visible instead of silent hangs.

**TimeoutDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Timeout: never wait forever on a remote call. A hung dependency without a timeout holds a thread per request
 * until the pool is exhausted; with a timeout the caller gets control back and can fail or fall back.
 */
public class TimeoutDemo {

    static String hungStockService() {
        try { Thread.sleep(2_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "12 in stock";
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        long start = System.nanoTime();

        CompletableFuture<String> noTimeout = CompletableFuture.supplyAsync(TimeoutDemo::hungStockService, pool);
        System.out.println("without timeout, done after 300 ms? " + waitBriefly(noTimeout));

        List<String> results = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            CompletableFuture<String> call = CompletableFuture.supplyAsync(TimeoutDemo::hungStockService, pool)
                    .orTimeout(200, TimeUnit.MILLISECONDS)
                    .exceptionally(e -> e instanceof TimeoutException ? "stock unknown (timed out)" : "error");
            results.add(call.get());
        }
        System.out.println("with 200 ms timeout: " + results);
        System.out.println("pool after timeouts: active=" + pool.getActiveCount() + " queued=" + pool.getQueue().size()
                + " (orTimeout frees the caller, not the thread)");
        System.err.printf("elapsed %d ms%n", (System.nanoTime() - start) / 1_000_000);
        pool.shutdownNow();
    }

    static boolean waitBriefly(CompletableFuture<String> f) {
        try { f.get(300, TimeUnit.MILLISECONDS); return true; } catch (Exception e) { return false; }
    }
}
```

1. A dependency that hangs for two seconds.
2. `orTimeout` completes the future exceptionally after 200 ms.
3. The timeout is turned into a useful answer for the caller.

**Run it:** `java TimeoutDemo.java`

```text
without timeout, done after 300 ms? false
with 200 ms timeout: [stock unknown (timed out), stock unknown (timed out)]
pool after timeouts: active=2 queued=1 (orTimeout frees the caller, not the thread)
```

| Timeout | Where | Typical value |
|---------|-------|---------------|
| Connect | HTTP client | 1–2 s, or lower in-cluster |
| Read / response | HTTP client, JDBC `queryTimeout` | Slightly above dependency p99 |
| Request deadline | Gateway / caller | User-facing budget (e.g. 1–2 s) |
| Pool acquisition | Connection pool | Short: don't queue forever |

**Use** timeouts on every network call, including database and cache calls. **Avoid** library defaults: many HTTP clients wait forever by default.
**Real-world Java:** `HttpClient.newBuilder().connectTimeout(...)` and `HttpRequest.timeout(...)`, `CompletableFuture.orTimeout`, Resilience4j `TimeLimiter`, HikariCP `connectionTimeout`.
**Best practice:** propagate a deadline (remaining budget) to downstream calls. **Gotcha:** the last output line shows that `orTimeout` frees the caller but not the worker thread; the abandoned work still runs, so use clients with real I/O timeouts or cancel the task.

### Retry with backoff and jitter

**Description.** Retry transient failures (timeouts, 503, connection resets) a small number of times, waiting longer each time (exponential backoff) with randomness (jitter) so clients don't retry in sync. Never retry non-transient errors (400, 402) and only retry operations that are idempotent, or made idempotent with an idempotency key.

**Advantages / features.** Absorbs blips; spreads load during recovery; bounded total attempts.

**RetryBackoffJitterDemo.java**

```java
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Retry with exponential backoff and jitter. Without jitter, every client that failed together retries together
 * (a thundering herd). Full jitter spreads retries out. Only retry transient failures of idempotent operations.
 */
public class RetryBackoffJitterDemo {

    static long backoffMs(int attempt, long baseMs, long capMs) {
        return Math.min(capMs, baseMs * (1L << (attempt - 1)));
    }

    static long fullJitterMs(int attempt, long baseMs, long capMs, Random random) {
        return (long) (random.nextDouble() * backoffMs(attempt, baseMs, capMs));
    }

    static final Set<String> RETRYABLE = Set.of("503", "timeout", "connection-reset");
    static boolean shouldRetry(String failure, boolean idempotent, int attempt, int maxAttempts) {
        return idempotent && RETRYABLE.contains(failure) && attempt < maxAttempts;
    }

    public static void main(String[] args) {
        System.out.println("backoff schedule (base 100 ms, cap 2 s): "
                + List.of(1, 2, 3, 4, 5, 6).stream().map(a -> backoffMs(a, 100, 2000)).toList());

        int clients = 1000;
        Random random = new Random(42);
        TreeMap<Long, Integer> plain = new TreeMap<>(), jittered = new TreeMap<>();
        for (int c = 0; c < clients; c++) {
            plain.merge(backoffMs(3, 100, 2000) / 50, 1, Integer::sum);
            jittered.merge(fullJitterMs(3, 100, 2000, random) / 50, 1, Integer::sum);
        }
        System.out.println("third retry, busiest 50 ms window: no jitter " + plain.values().stream().max(Integer::compare).get()
                + " clients, full jitter " + jittered.values().stream().max(Integer::compare).get() + " clients");

        System.out.println("GET /stock 503, attempt 1 -> retry? " + shouldRetry("503", true, 1, 3));
        System.out.println("POST /payments timeout -> retry? " + shouldRetry("timeout", false, 1, 3));
        System.out.println("GET /stock 400 -> retry? " + shouldRetry("400", true, 1, 3));
        System.out.println("GET /stock 503, attempt 3 of 3 -> retry? " + shouldRetry("503", true, 3, 3));
    }
}
```

1. Exponential backoff with a cap: 100, 200, 400 ms and so on.
2. Full jitter: wait a random time between 0 and the backoff.
3. Retry only transient failures of idempotent operations, within the attempt limit.

**Run it:** `java RetryBackoffJitterDemo.java`

```text
backoff schedule (base 100 ms, cap 2 s): [100, 200, 400, 800, 1600, 2000]
third retry, busiest 50 ms window: no jitter 1000 clients, full jitter 147 clients
GET /stock 503, attempt 1 -> retry? true
POST /payments timeout -> retry? false
GET /stock 400 -> retry? false
GET /stock 503, attempt 3 of 3 -> retry? false
```

| Jitter strategy | Formula | Notes |
|-----------------|---------|-------|
| None | base x 2^n | Synchronised retries |
| Full | random(0, base x 2^n) | Best spread (AWS recommendation) |
| Equal | half + random(0, half) | Guarantees a minimum wait |
| Decorrelated | random(base, prev x 3) | Smooth growth |

**Use** for idempotent reads and keyed writes. **Avoid** retrying at several layers (app, client library, mesh) at once.
**Real-world Java:** Resilience4j `Retry` with `IntervalFunction.ofExponentialRandomBackoff`, Spring Retry `@Retryable`, AWS SDK retry policies.
**Best practice:** limit retries with a budget (for example at most 10–20% extra traffic). **Gotcha:** 3 retries at each of 3 layers is 4 x 4 x 4 = 64 calls to the bottom service per user request.

### Circuit breaker

**Description.** Track recent call outcomes. When the failure (or slow-call) rate exceeds a threshold, the breaker **opens** and rejects calls immediately for a wait period. Then it goes **half-open**, letting a few trial calls through; success closes it, failure opens it again.

**Advantages / features.** Fail fast instead of waiting on a dead service; gives the dependency room to recover; makes outages visible as state changes.

**CircuitBreakerDemo.java**

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Circuit breaker: after too many recent failures, stop calling the dependency (OPEN) and fail fast. After a wait,
 * let a few trial calls through (HALF_OPEN); close again if they succeed. Uses a virtual clock for stable output.
 */
public class CircuitBreakerDemo {

    enum State { CLOSED, OPEN, HALF_OPEN }

    static final class CircuitBreaker {
        private final int windowSize; private final double failureRateThreshold; private final long openMs; private final int trialCalls;
        private final Deque<Boolean> window = new ArrayDeque<>();
        private State state = State.CLOSED; private long openedAt; private int trialsLeft, trialFailures;
        CircuitBreaker(int windowSize, double failureRateThreshold, long openMs, int trialCalls) {
            this.windowSize = windowSize; this.failureRateThreshold = failureRateThreshold; this.openMs = openMs; this.trialCalls = trialCalls;
        }

        String call(Supplier<String> remote, long now) {
            if (state == State.OPEN) {
                if (now - openedAt < openMs) return "rejected fast (OPEN)";
                state = State.HALF_OPEN; trialsLeft = trialCalls; trialFailures = 0;
            }
            boolean ok;
            String result;
            try { result = remote.get(); ok = true; } catch (RuntimeException e) { result = "failed: " + e.getMessage(); ok = false; }
            if (state == State.HALF_OPEN) {
                if (!ok) trialFailures++;
                if (--trialsLeft == 0 || trialFailures > 0) {
                    if (trialFailures > 0) open(now); else { state = State.CLOSED; window.clear(); }
                }
                return result + " [" + state + "]";
            }
            window.addLast(ok);
            if (window.size() > windowSize) window.removeFirst();
            long failures = window.stream().filter(b -> !b).count();
            if (window.size() == windowSize && (double) failures / windowSize >= failureRateThreshold) open(now);
            return result + " [" + state + "]";
        }
        private void open(long now) { state = State.OPEN; openedAt = now; }
    }

    public static void main(String[] args) {
        CircuitBreaker breaker = new CircuitBreaker(4, 0.5, 30_000, 2);
        boolean[] paylinkUp = {true};
        int[] remoteCalls = {0};
        Supplier<String> paylink = () -> {
            remoteCalls[0]++;
            if (!paylinkUp[0]) throw new IllegalStateException("PayLink 503");
            return "paid";
        };
        long t = 0;
        System.out.println("t=" + t + " " + breaker.call(paylink, t));
        paylinkUp[0] = false;
        for (int i = 0; i < 3; i++) { t += 1000; System.out.println("t=" + t + " " + breaker.call(paylink, t)); }
        for (int i = 0; i < 3; i++) { t += 1000; System.out.println("t=" + t + " " + breaker.call(paylink, t)); }
        t = 40_000; System.out.println("t=" + t + " " + breaker.call(paylink, t));
        paylinkUp[0] = true;
        t = 75_000; System.out.println("t=" + t + " " + breaker.call(paylink, t));
        t = 76_000; System.out.println("t=" + t + " " + breaker.call(paylink, t));
        System.out.println("remote calls made: " + remoteCalls[0] + " of 10 requests");
    }
}
```

1. While open, calls are rejected without touching the network.
2. Half-open: trial calls decide whether to close or reopen.
3. The breaker opens when the failure rate over a full window reaches the threshold.

**Run it:** `java CircuitBreakerDemo.java`

```text
t=0 paid [CLOSED]
t=1000 failed: PayLink 503 [CLOSED]
t=2000 failed: PayLink 503 [CLOSED]
t=3000 failed: PayLink 503 [OPEN]
t=4000 rejected fast (OPEN)
t=5000 rejected fast (OPEN)
t=6000 rejected fast (OPEN)
t=40000 failed: PayLink 503 [OPEN]
t=75000 paid [HALF_OPEN]
t=76000 paid [CLOSED]
remote calls made: 7 of 10 requests
```

| Setting | Effect |
|---------|--------|
| Window (count or time based) | How much history decides |
| Minimum calls | Avoids opening on 1 failure out of 1 |
| Failure-rate threshold | How bad before opening |
| Slow-call threshold | Treat slow successes as failures |
| Wait in open state | Recovery time given to the dependency |
| Half-open permitted calls | How many trials before deciding |

**Use** around every remote dependency that can fail independently. **Avoid** counting business errors (declined card, 404) as failures.
**Real-world Java:** Resilience4j `CircuitBreaker`, Spring Cloud CircuitBreaker, Envoy outlier detection.
**Best practice:** one breaker per dependency (or per endpoint), never one global breaker. **Gotcha:** with a tiny window, a few unlucky calls open the breaker at low traffic; set a minimum number of calls.

### Bulkhead

**Description.** Limit concurrent calls per dependency (a semaphore or a dedicated thread pool). When the limit is reached, extra calls are rejected or wait briefly. A slow dependency then only exhausts its own compartment.

**Advantages / features.** Failure isolation between features; predictable resource use; protection for the dependency too.

**BulkheadDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulkhead: give each dependency its own limited pool of concurrent calls, so one slow dependency can't use up
 * all the capacity. Simulated: each request holds a slot for its dependency's latency.
 */
public class BulkheadDemo {

    /** Concurrency per dependency, or one shared pool when perDependency is null. */
    static Map<String, Integer> simulate(int sharedThreads, Map<String, Integer> perDependency) {
        Map<String, Integer> latencyMs = Map.of("offers", 2000, "payments", 50);
        Map<String, List<Integer>> busyUntil = new LinkedHashMap<>();
        Map<String, Integer> rejected = new LinkedHashMap<>(Map.of("offers", 0, "payments", 0));
        List<Integer> shared = new ArrayList<>();
        for (int t = 0; t < 1000; t += 10) {                                                     // a request every 10 ms per dependency
            for (String dep : List.of("offers", "payments")) {
                final int now = t;
                List<Integer> pool = perDependency == null ? shared : busyUntil.computeIfAbsent(dep, k -> new ArrayList<>());
                int limit = perDependency == null ? sharedThreads : perDependency.get(dep);
                pool.removeIf(until -> until <= now);
                if (pool.size() >= limit) rejected.merge(dep, 1, Integer::sum);
                else pool.add(now + latencyMs.get(dep));
            }
        }
        return rejected;
    }

    public static void main(String[] args) {
        System.out.println("one shared pool of 40:      rejected " + simulate(40, null));
        System.out.println("bulkheads offers 20, payments 20: rejected "
                + simulate(0, Map.of("offers", 20, "payments", 20)));
    }
}
```

1. Each slot is busy until its call's latency has passed.
2. A full pool rejects the call instead of queueing it.
3. With bulkheads, slow Offers only rejects Offers calls; Payments is untouched.

**Run it:** `java BulkheadDemo.java`

```text
one shared pool of 40:      rejected {offers=60, payments=65}
bulkheads offers 20, payments 20: rejected {offers=80, payments=0}
```

| Bulkhead type | Mechanism | Trade-off |
|---------------|-----------|-----------|
| Semaphore | Limits concurrent callers | Cheap; no extra threads; timeout must be separate |
| Thread pool | Separate executor per dependency | Isolates blocking; costs threads |
| Connection pool | Max connections per host | Usually needed anyway |
| Deployment | Separate instances per client/tenant | Strongest isolation, highest cost |

**Use** for dependencies with different criticality or latency. **Avoid** a single shared pool for everything (the default in many apps).
**Real-world Java:** Resilience4j `Bulkhead` and `ThreadPoolBulkhead`, HikariCP pools per database, Tomcat connector limits.
**Best practice:** size bulkheads with Little's Law: concurrency = throughput x latency, plus headroom. **Gotcha:** virtual threads remove thread-pool limits; you still need a semaphore bulkhead to protect the dependency.

### Rate limiter

**Description.** Limit the number of calls per time window. A **token bucket** refills at a steady rate and allows bursts up to its capacity. Client-side, it keeps you inside a provider's quota; server-side, it protects your service from abusive or runaway clients.

**Advantages / features.** Predictable load; avoids 429s and bans from providers; fairness between clients.

**RateLimiterDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Rate limiter: a token bucket allows short bursts up to its capacity and a steady rate after that. */
public class RateLimiterDemo {

    static final class TokenBucket {
        private final double capacity, refillPerMs; private double tokens; private long last;
        TokenBucket(int capacity, int perSecond) { this.capacity = capacity; this.refillPerMs = perSecond / 1000.0; this.tokens = capacity; }
        boolean tryAcquire(long nowMs) {
            tokens = Math.min(capacity, tokens + (nowMs - last) * refillPerMs);
            last = nowMs;
            if (tokens >= 1) { tokens -= 1; return true; }
            return false;
        }
    }

    public static void main(String[] args) {
        TokenBucket textBlast = new TokenBucket(5, 2);     // TextBlast SMS API allows 2/s with bursts of 5
        List<String> burst = new ArrayList<>();
        for (int i = 0; i < 8; i++) burst.add(textBlast.tryAcquire(0) ? "send" : "wait");
        System.out.println("8 OTPs at t=0:      " + burst);
        List<String> later = new ArrayList<>();
        for (long t = 500; t <= 2000; t += 250) later.add("t=" + t + ":" + (textBlast.tryAcquire(t) ? "send" : "wait"));
        System.out.println("one every 250 ms:   " + later);
        System.out.println("after 10 s idle:    burst of " + countBurst(textBlast, 12_000) + " allowed");
    }

    static int countBurst(TokenBucket b, long now) { int n = 0; while (b.tryAcquire(now)) n++; return n; }
}
```

1. The bucket starts full: capacity is the allowed burst.
2. Tokens refill continuously at the steady rate, up to capacity.
3. No token: the caller waits, queues or gets a 429.

**Run it:** `java RateLimiterDemo.java`

```text
8 OTPs at t=0:      [send, send, send, send, send, wait, wait, wait]
one every 250 ms:   [t=500:send, t=750:wait, t=1000:send, t=1250:wait, t=1500:send, t=1750:wait, t=2000:send]
after 10 s idle:    burst of 5 allowed
```

| Algorithm | Behaviour |
|-----------|-----------|
| Token bucket | Bursts up to capacity, then steady rate |
| Leaky bucket | Smooth constant output |
| Fixed window | Simple; 2x burst at window edges |
| Sliding window | Accurate; more state |
| Distributed (Redis) | Shared limit across instances |

**Use** client-side for third-party quotas; server-side per client at the edge. **Avoid** per-instance limits when the quota is global (10 instances x limit = 10x the quota).
**Real-world Java:** Resilience4j `RateLimiter`, Bucket4j (with Redis), Guava `RateLimiter`, Spring Cloud Gateway `RequestRateLimiter`.
**Best practice:** return `429` with `Retry-After` when you limit others; honour it when others limit you. **Gotcha:** Resilience4j's rate limiter blocks by default for up to `timeoutDuration`; set it to zero for fail-fast.

### Fallback

**Description.** When a call fails (after retries, or because the breaker is open), return a substitute: a cached last-known value marked as stale, a safe default, a degraded feature, or "accepted, will complete later". Choose fallbacks with the business, because each one is a product decision.

**Advantages / features.** Users keep a working experience; failures become graceful degradation.

**FallbackDemo.java**

```java
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fallback: when a dependency fails, answer with something useful: a cached value (marked stale), a safe default,
 * or a degraded feature. Never fall back on business errors, and never on writes that must not be faked.
 */
public class FallbackDemo {

    record Eta(int minutes, String source) {}
    static final class EtaNotAvailable extends RuntimeException { EtaNotAvailable(String m) { super(m); } }
    static final class UnknownOrder extends RuntimeException { UnknownOrder(String m) { super(m); } }

    static final Map<String, Eta> lastKnown = new HashMap<>();

    static Eta etaFor(String orderId, Supplier<Integer> routingService) {
        try {
            Eta live = new Eta(routingService.get(), "live");
            lastKnown.put(orderId, live);
            return live;
        } catch (UnknownOrder e) {
            throw e;
        } catch (RuntimeException e) {
            Eta cached = lastKnown.get(orderId);
            if (cached != null) return new Eta(cached.minutes(), "cached, may be stale");
            return new Eta(45, "default slot estimate");
        }
    }

    public static void main(String[] args) {
        System.out.println("FC-1 " + etaFor("FC-1", () -> 18));
        System.out.println("FC-1 " + etaFor("FC-1", () -> { throw new EtaNotAvailable("routing 503"); }));
        System.out.println("FC-2 " + etaFor("FC-2", () -> { throw new EtaNotAvailable("routing 503"); }));
        try { etaFor("FC-9", () -> { throw new UnknownOrder("no order FC-9"); }); }
        catch (UnknownOrder e) { System.out.println("FC-9 -> 404: " + e.getMessage()); }
    }
}
```

1. Business errors are not failures to hide; they propagate (here, a 404).
2. First choice: the last known value, labelled as possibly stale.
3. Last resort: a safe default the business agreed on.

**Run it:** `java FallbackDemo.java`

```text
FC-1 Eta[minutes=18, source=live]
FC-1 Eta[minutes=18, source=cached, may be stale]
FC-2 Eta[minutes=45, source=default slot estimate]
FC-9 -> 404: no order FC-9
```

| Fallback | Example | Risk |
|----------|---------|------|
| Cached value | Last ETA, yesterday's prices | Stale data shown as fresh |
| Default | "45 minutes", empty recommendations | Misleading if not labelled |
| Degrade feature | Hide offers banner | Hidden failures if unmonitored |
| Queue for later | Payment "pending" | Must actually be processed |
| Fail fast | Clear error message | User frustration, but honest |

**Use** for reads and non-critical features. **Avoid** faking writes (never pretend a payment succeeded).
**Real-world Java:** Resilience4j `Decorators.ofSupplier(...).withFallback(...)`, Spring Cloud CircuitBreaker fallbacks, Caffeine as last-known-value cache.
**Best practice:** count every fallback served as a metric and alert on it. **Gotcha:** a fallback that calls another remote service needs its own resilience; it can fail too.

### Resilience4j

**Description.** Resilience4j is a lightweight library with one module per pattern (`CircuitBreaker`, `Retry`, `Bulkhead`, `ThreadPoolBulkhead`, `RateLimiter`, `TimeLimiter`, `Cache`), each a functional decorator around `Supplier`, `Callable`, `CompletionStage` or reactive types. Spring Boot integration adds annotations, YAML configuration, Actuator health and Micrometer metrics.

**Advantages / features.** Small, modular, no thread pools unless you ask; event publishers and metrics for every component; Spring Boot and Reactor/RxJava support.

This example is a small Maven project using the real library: [`examples/l4-senior/resilience-patterns/resilience4j`](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/claude/add-playbook-master-prompt/examples/l4-senior/resilience-patterns/resilience4j).

**Resilience4jDemo.java**

```java
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
        CircuitBreaker breaker = CircuitBreaker.of("paylink", CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        Retry retry = Retry.of("paylink", RetryConfig.custom()
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
        Supplier<String> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(breaker, Bulkhead.decorateSupplier(bulkhead, paylink)));

        System.out.println("FC-1 " + withFallback(decorated));
        up[0] = false;
        for (int i = 2; i <= 4; i++) System.out.println("FC-" + i + " " + withFallback(decorated));
        events.forEach(e -> System.out.println("  " + e));
        System.out.println("remote calls: " + remoteCalls[0] + ", breaker state: " + breaker.getState());
        CircuitBreaker.Metrics m = breaker.getMetrics();
        System.out.println("failure rate: " + m.getFailureRate() + "%, not permitted: " + m.getNumberOfNotPermittedCalls());
    }

    static String withFallback(Supplier<String> call) {
        try {
            return call.get();
        } catch (CallNotPermittedException e) {
            return "pending (breaker open, no call made)";
        } catch (PayLinkUnavailable e) {
            return "pending (PayLink unavailable after retries)";
        }
    }
}
```

1. Count-based window of four calls; open at 50% failures; stay open for 30 s.
2. Retry only PayLink's transient error with exponential random backoff, and never retry a call the breaker rejected.
3. Retry wraps the breaker, which wraps the bulkhead: each attempt is counted by the breaker.
4. Fallbacks distinguish "we didn't call" from "we called and it failed".

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.resilience.Resilience4jDemo` (or `mvn verify`, which checks this output)

```text
FC-1 paid
FC-2 pending (PayLink unavailable after retries)
FC-3 pending (breaker open, no call made)
FC-4 pending (breaker open, no call made)
  retry attempt 1
  retry attempt 2
  breaker State transition from CLOSED to OPEN
remote calls: 4, breaker state: OPEN
failure rate: 75.0%, not permitted: 2
```

| Spring Boot property (`resilience4j.circuitbreaker.instances.paylink.*`) | Meaning |
|-------------------------------------------------------------------------|---------|
| `sliding-window-size`, `minimum-number-of-calls` | Window and minimum history |
| `failure-rate-threshold`, `slow-call-rate-threshold` | When to open |
| `wait-duration-in-open-state` | Time before half-open |
| `permitted-number-of-calls-in-half-open-state` | Trial calls |
| `ignore-exceptions` / `record-exceptions` | What counts as failure |

**Use** Resilience4j (directly or via Spring Cloud CircuitBreaker) for JVM services. **Avoid** Hystrix: it has been in maintenance mode since 2018.
**Real-world Java:** `@CircuitBreaker(name = "paylink", fallbackMethod = "pending")` with `resilience4j-spring-boot3`.
**Best practice:** configure per dependency in YAML and expose metrics via Micrometer. **Gotcha:** annotation-based decorators are Spring AOP proxies: self-invocation (`this.charge()`) bypasses them.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's add-to-cart button checks stock. If Inventory is slow, customers should still be able to add the item.

**StockCheckDemo.java**

```java
import java.util.function.IntSupplier;

/** Minimal: a stock check with a time budget and a safe fallback, so checkout never hangs on Inventory. */
public class StockCheckDemo {

    record Reply(int latencyMs, int units) {}

    static String canAddToCart(String sku, java.util.function.Supplier<Reply> inventory, int budgetMs) {
        Reply r = inventory.get();
        if (r.latencyMs() > budgetMs)
            return sku + ": allowed, confirm at packing (inventory slow)";
        return sku + (r.units() > 0 ? ": allowed (" + r.units() + " left)" : ": out of stock");
    }

    public static void main(String[] args) {
        System.out.println(canAddToCart("MILK-1L", () -> new Reply(40, 12), 150));
        System.out.println(canAddToCart("PANEER-200G", () -> new Reply(30, 0), 150));
        System.out.println(canAddToCart("CURD-500G", () -> new Reply(3000, 5), 150));
    }
}
```

1. The call has a time budget.
2. Over budget, the business-approved fallback lets the customer continue.

**Run it:** `java StockCheckDemo.java`

**Output:**

```text
MILK-1L: allowed (12 left)
PANEER-200G: out of stock
CURD-500G: allowed, confirm at packing (inventory slow)
```

**What to notice:**

- The fallback is a product decision (confirm at packing), not a technical default.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** login OTPs go through TextBlast. When FreshCart exceeds its quota, TextBlast answers 429 with `Retry-After`; when it's struggling, 503. An OTP is useless after a few seconds, so there's an overall deadline and a backup provider.

**OtpSenderDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Realistic: FreshCart sends login OTPs through TextBlast. TextBlast returns 429 with Retry-After when we exceed
 * our quota and 503 when it's struggling. The sender honours Retry-After, backs off with jitter on 503, gives up
 * after an overall deadline (an OTP that arrives in 2 minutes is useless) and fails over to a backup provider.
 */
public class OtpSenderDemo {

    record Reply(int status, long retryAfterMs) {}
    interface SmsProvider { String name(); Reply send(String phone, String otp, long nowMs); }

    static final class OtpSender {
        private final List<SmsProvider> providers; private final long deadlineMs; private final Random random;
        final List<String> log = new ArrayList<>();
        OtpSender(List<SmsProvider> providers, long deadlineMs, Random random) { this.providers = providers; this.deadlineMs = deadlineMs; this.random = random; }

        boolean send(String phone, String otp) {
            long now = 0;
            for (SmsProvider p : providers) {
                for (int attempt = 1; attempt <= 4 && now < deadlineMs; attempt++) {
                    Reply r = p.send(phone, otp, now);
                    log.add("t=" + now + " " + p.name() + " -> " + r.status());
                    if (r.status() == 200) return true;
                    if (r.status() == 400) return false;                                         // bad number: no retry, no failover
                    long wait = r.status() == 429 ? r.retryAfterMs()
                            : (long) (random.nextDouble() * 200 * (1L << (attempt - 1)));
                    now += wait;
                }
                log.add("failing over from " + p.name());
            }
            return false;
        }
    }

    public static void main(String[] args) {
        SmsProvider textBlast = new SmsProvider() {
            public String name() { return "TextBlast"; }
            public Reply send(String phone, String otp, long now) {
                if (now < 1000) return new Reply(429, 1000);
                return new Reply(503, 0);
            }
        };
        SmsProvider backup = new SmsProvider() {
            public String name() { return "BackupSms"; }
            public Reply send(String phone, String otp, long now) { return new Reply(200, 0); }
        };
        OtpSender sender = new OtpSender(List.of(textBlast, backup), 5000, new Random(7));
        System.out.println("delivered: " + sender.send("+91-98xxxx1234", "482913"));
        sender.log.forEach(l -> System.out.println("  " + l));
    }
}
```

1. On 429, wait exactly as long as the provider asks.
2. On 503, back off exponentially with full jitter.
3. When attempts or the deadline run out, fail over to the next provider.

**Run it:** `java OtpSenderDemo.java`

**Output:**

```text
delivered: true
  t=0 TextBlast -> 429
  t=1000 TextBlast -> 503
  t=1292 TextBlast -> 503
  t=1891 TextBlast -> 503
  failing over from TextBlast
  t=2448 BackupSms -> 200
```

**What to notice:**

- The waits after the 503s (292 ms, 599 ms, 557 ms) are random within growing ranges.
- A 400 (invalid number) returns immediately: retrying or failing over can't fix it.

### Example 3: Production-grade

**Scenario:** FreshCart's PayLink client must never double-charge, must not hammer PayLink during an outage, and must leave orders in a state that can be completed later.

**PaymentClientDemo.java**

```java
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Production-grade: FreshCart's PayLink client composes a circuit breaker, a per-attempt timeout, retries with an
 * idempotency key, a retry budget and a fallback, in a deliberate order. A virtual clock and a scripted PayLink
 * make the output stable.
 */
public class PaymentClientDemo {

    record Attempt(String idempotencyKey, int number) {}
    record Reply(int status, int latencyMs) {}
    record Outcome(String result, int remoteCalls) {}

    static final class PaymentClient {
        static final int TIMEOUT_MS = 800, MAX_ATTEMPTS = 3, WINDOW = 4;
        static final long OPEN_MS = 30_000;
        final Map<String, Integer> metrics = new TreeMap<>();
        private final Function<Attempt, Reply> paylink;
        private int windowCalls, windowFailures; private boolean open; private long openedAt;
        private int retryTenths = 10;                 // retry budget in tenths of a retry: +0.2 per request, 1 per retry
        long now;
        PaymentClient(Function<Attempt, Reply> paylink) { this.paylink = paylink; }

        Outcome charge(String orderId) {
            retryTenths = Math.min(50, retryTenths + 2);
            if (open && now - openedAt < OPEN_MS) return done("rejected-open", "pending: queued for later", 0);
            String key = "pay-" + orderId;
            int calls = 0;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                if (attempt > 1) {
                    if (open || retryTenths < 10) { metrics.merge("retry-skipped", 1, Integer::sum); break; }
                    retryTenths -= 10;
                }
                calls++;
                Reply r = paylink.apply(new Attempt(key, attempt));
                boolean timedOut = r.latencyMs() > TIMEOUT_MS;
                now += Math.min(r.latencyMs(), TIMEOUT_MS);
                if (!timedOut && r.status() == 200) { record(true); return done("ok", "paid", calls); }
                if (!timedOut && r.status() == 402) { record(true); return done("declined", "declined: card refused", calls); }
                record(false);
            }
            return done("fallback", "pending: queued for later", calls);
        }

        private void record(boolean success) {
            if (open) { open = !success; if (open) openedAt = now; windowCalls = 0; windowFailures = 0; return; }   // half-open trial
            windowCalls++; if (!success) windowFailures++;
            if (windowCalls == WINDOW) {
                if (windowFailures * 2 >= WINDOW) { open = true; openedAt = now; }
                windowCalls = 0; windowFailures = 0;
            }
        }
        String state() { return open ? (now - openedAt < OPEN_MS ? "OPEN" : "HALF_OPEN") : "CLOSED"; }
        private Outcome done(String metric, String result, int calls) { metrics.merge(metric, 1, Integer::sum); return new Outcome(result, calls); }
    }

    public static void main(String[] args) {
        Set<String> charged = new HashSet<>();
        int[] chargesMade = {0};
        String[] mode = {"healthy"};
        Function<Attempt, Reply> paylink = a -> {
            if (mode[0].equals("outage")) return new Reply(503, 60);
            if (a.idempotencyKey().equals("pay-FC-2")) return new Reply(402, 90);
            if (charged.add(a.idempotencyKey())) chargesMade[0]++;      // PayLink dedupes by idempotency key
            boolean slowFirstTry = a.idempotencyKey().equals("pay-FC-3") && a.number() == 1;
            return new Reply(200, slowFirstTry ? 2500 : 120);
        };
        PaymentClient client = new PaymentClient(paylink);
        for (String id : new String[] {"FC-1", "FC-2", "FC-3"}) System.out.println(id + " " + client.charge(id) + " " + client.state());
        System.out.println("PayLink charges made: " + chargesMade[0] + " (FC-1 and FC-3; FC-3 timed out, was retried with the same key, charged once)");

        mode[0] = "outage";
        for (int i = 4; i <= 8; i++) System.out.println("FC-" + i + " " + client.charge("FC-" + i) + " " + client.state());
        client.now += 31_000;
        mode[0] = "healthy";
        System.out.println("after 31 s: " + client.state());
        System.out.println("FC-9 " + client.charge("FC-9") + " " + client.state());
        System.out.println("metrics: " + client.metrics);
    }
}
```

1. An open breaker rejects without calling PayLink; the order is queued for later.
2. One idempotency key per order: every retry of the same charge carries it.
3. A retry budget: retries are skipped when the budget is spent or the breaker opened.
4. Each attempt has its own timeout; a timeout counts as a failure.
5. When all else fails, the fallback is "pending", never a fake success.

**Run it:** `java PaymentClientDemo.java`

**Output:**

```text
FC-1 Outcome[result=paid, remoteCalls=1] CLOSED
FC-2 Outcome[result=declined: card refused, remoteCalls=1] CLOSED
FC-3 Outcome[result=paid, remoteCalls=2] CLOSED
PayLink charges made: 2 (FC-1 and FC-3; FC-3 timed out, was retried with the same key, charged once)
FC-4 Outcome[result=pending: queued for later, remoteCalls=1] CLOSED
FC-5 Outcome[result=pending: queued for later, remoteCalls=2] CLOSED
FC-6 Outcome[result=pending: queued for later, remoteCalls=1] OPEN
FC-7 Outcome[result=pending: queued for later, remoteCalls=0] OPEN
FC-8 Outcome[result=pending: queued for later, remoteCalls=0] OPEN
after 31 s: HALF_OPEN
FC-9 Outcome[result=paid, remoteCalls=1] CLOSED
metrics: {declined=1, fallback=3, ok=3, rejected-open=2, retry-skipped=3}
```

**What to notice:**

- FC-3's first attempt timed out after PayLink had already charged; the retry with the same key didn't charge again.
- FC-2's decline wasn't retried and didn't count toward opening the breaker.
- During the outage, FC-4 got no retry (budget spent), and FC-7 and FC-8 made no remote calls at all.
- After 31 seconds a single trial call (FC-9) closed the breaker.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Timeout | Bounded waits | Too short: false failures; too long: no protection |
| Retry | Hides transient failures | Amplifies load; duplicates without idempotency |
| Circuit breaker | Fail fast, recovery room | Tuning; can reject during partial outages |
| Bulkhead | Isolation | Capacity planning; rejected calls |
| Rate limiter | Predictable load, quota safety | Rejections; distributed limits are harder |
| Fallback | Graceful degradation | Can hide failures; stale data |

## 8. When to Use / When NOT to Use

**Use them when:**

- calling anything over a network (services, databases, caches, third parties);
- one dependency's failure shouldn't affect unrelated features;
- a provider enforces quotas.

**Don't when:**

- the call is in-process and can't fail independently;
- retrying non-idempotent writes without keys;
- adding patterns without metrics to tell whether they fire.

## 9. Best Practices

1. **Every remote call has a timeout.** *(Consensus.)*
2. **Retry only transient errors on idempotent operations, with backoff, jitter and a budget.** *(Consensus.)*
3. **One breaker and one bulkhead per dependency.** *(Consensus.)*
4. **Retry in exactly one layer.** *(Consensus.)*
5. **Fallbacks are product decisions and are always measured.** *(Consensus.)*
6. **Test failure modes deliberately (fault injection, chaos experiments).** *(Consensus.)*
7. **Prefer Resilience4j over hand-rolled logic in production.** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Retry storm after a blip (production failure mode)**  
> **Symptom:** PayLink had a 20-second hiccup; FreshCart's traffic to it went up 9x and kept it down for 15 minutes.
> **Root cause:** three retries in the app plus mesh retries, no jitter, no budget, no breaker.
> **Fix:** retries in one layer, full jitter, a retry budget, a circuit breaker, and honouring `Retry-After`.

> 🚨 **No timeout on a database or HTTP call**  
> **Symptom:** all request threads stuck; health checks time out; pods restarted in a loop.
> **Root cause:** default infinite read timeout on a client.
> **Fix:** explicit connect/read timeouts and a request deadline.

> 🚨 **Retrying a non-idempotent write**  
> **Symptom:** customers charged twice after a timeout.
> **Root cause:** a POST retried without an idempotency key; the first attempt had succeeded.
> **Fix:** idempotency keys on every write that may be retried (Example 3).

> 🚨 **Silent fallbacks**  
> **Symptom:** recommendations were empty for two weeks; nobody noticed.
> **Root cause:** fallback to an empty list with no metric or alert.
> **Fix:** count fallbacks; alert when the rate exceeds a baseline.

## 11. Configuration & Tuning

| Setting (Resilience4j) | Default | What it does | When to change it | Risk of changing |
|------------------------|---------|--------------|-------------------|------------------|
| `circuitbreaker.slidingWindowSize` | 100 | Calls in the window | Low-traffic dependencies | Too small: flapping |
| `circuitbreaker.failureRateThreshold` | 50 | % failures to open | Critical vs optional deps | Too low: false opens |
| `circuitbreaker.waitDurationInOpenState` | 60 s | Time open before half-open | Faster recovery checks | Too short: hammering |
| `retry.maxAttempts` | 3 | Total attempts | Rarely higher | Load amplification |
| `bulkhead.maxConcurrentCalls` | 25 | Concurrent permits | Size by Little's Law | Too low: rejections |
| `ratelimiter.timeoutDuration` | 5 s | Wait for a permit | 0 for fail-fast | Blocked threads |
| `timelimiter.timeoutDuration` | 1 s | Async call timeout | Per dependency p99 | False timeouts |

## 12. Security Considerations

- Rate limiting at the edge is a first defence against credential stuffing and scraping; limit per account and per IP.
- Fallbacks must not bypass security checks (no "allow if the auth service is down" for sensitive actions; fail closed).
- Retries of authenticated calls must not log tokens.
- Circuit breakers around fraud checks need an explicit policy: fail closed for high-value orders.

## 13. Performance & Scalability

The patterns are cheap: a breaker or semaphore check costs nanoseconds. The real effects are systemic: timeouts free capacity, retries add load (budget them), breakers shed load from struggling dependencies, and bulkheads cap the blast radius. Size concurrency with Little's Law; for example 200 requests/s at 100 ms latency needs about 20 concurrent permits. With virtual threads (Java 21+), thread pools no longer limit concurrency, so explicit bulkheads matter more.

## 14. Testing It

Test resilience logic with a scripted dependency and a controllable clock:

**PaymentClientTest.java**

```java
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PaymentClientTest {

    static PaymentClientDemo.Reply ok() { return new PaymentClientDemo.Reply(200, 100); }
    static PaymentClientDemo.Reply unavailable() { return new PaymentClientDemo.Reply(503, 50); }

    @Test
    void retriesUseTheSameIdempotencyKey() {
        List<PaymentClientDemo.Attempt> attempts = new ArrayList<>();
        var client = new PaymentClientDemo.PaymentClient(a -> {
            attempts.add(a);
            return a.number() == 1 ? new PaymentClientDemo.Reply(200, 5000) : ok();
        });
        assertEquals("paid", client.charge("FC-1").result());
        assertEquals(2, attempts.size());
        assertEquals(attempts.get(0).idempotencyKey(), attempts.get(1).idempotencyKey());
    }

    @Test
    void declinedCardIsNotRetriedAndNotAFailure() {
        int[] calls = {0};
        var client = new PaymentClientDemo.PaymentClient(a -> { calls[0]++; return new PaymentClientDemo.Reply(402, 50); });
        for (int i = 0; i < 8; i++) assertEquals("declined: card refused", client.charge("FC-" + i).result());
        assertEquals(8, calls[0]);
        assertEquals("CLOSED", client.state());
    }

    @Test
    void breakerOpensAndRejectsWithoutCallingPayLink() {
        int[] calls = {0};
        var client = new PaymentClientDemo.PaymentClient(a -> { calls[0]++; return unavailable(); });
        for (int i = 0; i < 4; i++) client.charge("FC-" + i);
        assertEquals("OPEN", client.state());
        int before = calls[0];
        var outcome = client.charge("FC-99");
        assertEquals(0, outcome.remoteCalls());
        assertEquals(before, calls[0]);
        assertTrue(outcome.result().startsWith("pending"));
    }

    @Test
    void halfOpenTrialClosesOnSuccessAndReopensOnFailure() {
        boolean[] up = {false};
        Function<PaymentClientDemo.Attempt, PaymentClientDemo.Reply> paylink = a -> up[0] ? ok() : unavailable();
        var client = new PaymentClientDemo.PaymentClient(paylink);
        for (int i = 0; i < 4; i++) client.charge("FC-" + i);
        client.now += 31_000;
        assertEquals("HALF_OPEN", client.state());
        client.charge("FC-trial-1");
        assertEquals("OPEN", client.state());
        client.now += 31_000;
        up[0] = true;
        assertEquals("paid", client.charge("FC-trial-2").result());
        assertEquals("CLOSED", client.state());
    }

    @Test
    void retryBudgetCapsRetriesDuringAnOutage() {
        int[] calls = {0};
        var client = new PaymentClientDemo.PaymentClient(a -> { calls[0]++; return new PaymentClientDemo.Reply(200, 5000); });
        for (int i = 0; i < 3; i++) client.charge("FC-" + i);    // timeouts: 3 requests, but not 9 calls
        assertTrue(calls[0] < 6, "calls were " + calls[0]);
        assertTrue(client.metrics.get("retry-skipped") >= 1);
    }
}
```

For integration tests, inject faults with Toxiproxy or WireMock (delays, resets, 503s), and run game days in staging.

## 15. Observability & Operations

Export breaker state, failure and slow-call rates, not-permitted calls, retry counts, bulkhead available permits, rate-limiter waiting threads and fallback counts (Resilience4j has Micrometer binders). Alert on breakers open for more than a few minutes and on fallback rates. Show breaker state on the service dashboard; on-call engineers need to see "PayLink breaker OPEN" before they read logs.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2007 | Nygard's *Release It!* names stability patterns |
| 2012 | Netflix Hystrix |
| 2015 | AWS "Exponential Backoff and Jitter" article |
| 2016 | Resilience4j 0.x |
| 2018 | Hystrix enters maintenance mode |
| 2022 | Resilience4j 2.0 (Java 17 baseline) |
| 2023 | Java 21 virtual threads: thread pools stop acting as implicit bulkheads |

## 17. Expert Deep Dive

- **Adaptive concurrency limits.** Netflix's concurrency-limits library adjusts limits from observed latency (TCP-congestion-style) instead of fixed bulkhead sizes.
- **Load shedding and priorities.** Under overload, servers drop low-priority work first (Retry-After, 503) to keep critical paths alive.
- **Hedged requests.** Send a second request after the p95 latency and take the first reply; cuts tail latency at a small extra cost (idempotent reads only).
- **Deadline propagation.** gRPC propagates deadlines automatically; with HTTP, pass a remaining-budget header so downstream services don't work on requests the caller has abandoned.
- **Metastable failures.** Systems can stay down after the trigger is gone because retries keep load above capacity; budgets and breakers are what break the loop.

> 📖 **War story: 20 seconds that became 15 minutes**  
> PayLink had a 20-second network blip during FreshCart's evening peak. The checkout service retried three times, the HTTP client retried twice, and the mesh retried twice, all without jitter. When PayLink came back, it received about 9x normal traffic in synchronised waves and fell over again, repeatedly, for 15 minutes. **Fixes:** retries only in the application, with full jitter and a 10% retry budget; mesh retries disabled for PayLink; a circuit breaker with "payment pending" fallback and an async completion job; idempotency keys (already present, which is why nobody was double-charged). **Lesson:** retries are load; they need a budget like any other load.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Why set timeouts?** So a **slow dependency can't hang the caller**.
2. **What's exponential backoff?** **Waiting longer after each failed attempt**.
3. **What's a circuit breaker?** **Stops calling a failing service** for a while.
4. **What's a fallback?** A **substitute answer** when the call fails.
5. **What does a rate limiter do?** **Caps calls per time unit**.

**▸ 🟡 L3**

1. **Why jitter?** To **avoid synchronised retries** (thundering herd).
2. **Which errors should be retried?** **Transient ones** (timeouts, 503, resets) on **idempotent** operations.
3. **Circuit breaker states?** **Closed, open, half-open.**
4. **Semaphore vs thread-pool bulkhead?** **Limit callers** vs **separate executor**.
5. **Token bucket vs fixed window?** **Bursts with steady refill** vs **simple counters with edge bursts**.

**▸ 🔴 L4–L5**

1. **Order of Resilience4j decorators?** **Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(call)))))**, so each attempt is counted and limited.
2. **How do you prevent retry storms?** **One layer, jitter, budget, breaker, Retry-After.**
3. **How do you make payment retries safe?** **Idempotency keys** deduplicated by the provider or your service.
4. **Bulkheads with virtual threads?** **Still needed**: semaphores protect dependencies when threads are unlimited.
5. **What's a metastable failure?** A system **stuck in overload after the trigger is gone**, sustained by retries.

**▸ 💻 Hands-on**

**Task:** Add a slow-call rule to `CircuitBreakerDemo`: calls slower than 1 s count as failures.

> ✅ **Solution**  
> ```java
> record Result(String body, long latencyMs) {}
> // in call(): measure latency (or take it from the reply) and record a failure if it's too slow
> boolean ok = threw == null && result.latencyMs() <= 1_000;
> // Resilience4j equivalent: slowCallDurationThreshold(Duration.ofSeconds(1)) and slowCallRateThreshold(50)
> ```

## 19. Practice

- [ ] Find every HTTP and JDBC client in a service you know and list its timeouts.
    > 💡 **Hint**  
    > Search for client builders; missing values usually mean "infinite".

- [ ] Add `ThreadPoolBulkhead` to the Resilience4j project and observe rejections.
    > 💡 **Hint**  
    > `maxThreadPoolSize(2)`, `queueCapacity(0)`.

- [ ] Simulate 1,000 clients with equal jitter and compare the busiest window with full jitter.
    > 💡 **Hint**  
    > Reuse `RetryBackoffJitterDemo`.

- [ ] Use Toxiproxy to add 2 s latency to a local Postgres and watch your service.
    > 💡 **Hint**  
    > `toxiproxy-cli toxic add -t latency -a latency=2000 postgres`.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Timeout every remote call; propagate deadlines.
> - Retry transient errors on idempotent calls; backoff + full jitter + budget; one layer only.
> - Circuit breaker: closed, open (fail fast), half-open (trial calls).
> - Bulkhead per dependency; size with Little's Law.
> - Rate limiter: token bucket; honour Retry-After.
> - Fallbacks are product decisions; never fake writes; measure them.
>
> ```java
> Supplier<String> call = Retry.decorateSupplier(retry,
>         CircuitBreaker.decorateSupplier(breaker, Bulkhead.decorateSupplier(bulkhead, paylink)));
> ```

## 21. Further Reading

- Michael T. Nygard, *Release It!* (2nd ed., 2018), "Stability Patterns".
- Marc Brooker, [Exponential Backoff And Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/) (AWS Architecture Blog).
- [Resilience4j documentation](https://resilience4j.readme.io/).
- Google SRE book, chapters "Handling Overload" and "Addressing Cascading Failures".

---

[← Previous: Microservice Patterns](microservice-patterns.md) · [Next: Distributed Data Patterns →](distributed-data-patterns.md)
