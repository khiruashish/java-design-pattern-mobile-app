# Build & Tooling Configuration

> 📌 **TL;DR**  
> The settings that most often decide whether a pattern behaves as intended: compiler flags, Spring Boot switches that change how DI and proxies work, and the defaults of the resilience, caching, pooling and messaging libraries used in this playbook. Each row gives the default, what it does, and when to change it.

> ⚠️ **Check defaults against your versions**  
> Defaults below are for Java 21, Spring Boot 3.x, Resilience4j 2.x, Caffeine 3.x, HikariCP 5.x and Kafka clients 3.x. Library defaults change between major versions; confirm in the official docs before relying on one.

## Compiler and build

| Setting | Default | What it does | When to change it |
|---------|---------|--------------|-------------------|
| `javac --release 21` | Compiler's own version | Compiles against the exact API of that Java version | Always set it, so you don't use newer APIs by accident |
| `javac -parameters` | Off | Keeps parameter names in class files | On for Spring 6.1+ (needed for name-based binding; Boot's parent POM enables it) |
| `javac -Xlint:all -Werror` | Off | All warnings, fail on warnings | New projects; ratchet in old ones |
| Maven `maven.compiler.release` | Not set | Same as `--release` | Set in every POM |
| Surefire `<version>` | Old default in Maven 3 | JUnit 5 needs Surefire 2.22+; this playbook uses 3.5.2 | Pin it explicitly |
| `junit.jupiter.execution.parallel.enabled` | false | Parallel tests | Only when tests share no mutable state |
| Maven Shade `ServicesResourceTransformer` | Not configured | Merges `META-INF/services` files in fat jars | Whenever you shade jars that use ServiceLoader |

## Spring Boot switches that affect patterns

| Property | Default | What it does | When to change it |
|----------|---------|--------------|-------------------|
| `spring.main.allow-bean-definition-overriding` | false | Fails if two beans share a name | Keep false; overriding hides wiring mistakes |
| `spring.main.allow-circular-references` | false | Fails on circular dependencies | Keep false; fix the design ([Dependency Injection](../l2-practitioner/dependency-injection.md)) |
| `spring.main.lazy-initialization` | false | Creates beans on first use | Faster dev startup; hides startup errors in production |
| `spring.aop.proxy-target-class` | true | Class-based (CGLIB) proxies instead of JDK interface proxies | Rarely; see [JVM Internals](../l5-staff-principal/jvm-internals-behind-patterns.md) |
| `spring.threads.virtual.enabled` | false | Virtual threads for request handling and `@Async` | Blocking I/O-heavy services on Java 21 |
| `spring.jpa.open-in-view` | true (with a startup warning) | Keeps the persistence context open during view rendering | Set false to avoid lazy-loading queries outside services |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | Not set (no batching) | Batches inserts and updates | Bulk writes |

## Connection pools and HTTP clients

| Setting | Default | What it does | When to change it |
|---------|---------|--------------|-------------------|
| Hikari `maximumPoolSize` | 10 | Max connections | Size from database capacity, not thread count |
| Hikari `connectionTimeout` | 30000 ms | Wait for a free connection | Lower (1–5 s) so overload fails fast |
| Hikari `maxLifetime` | 1800000 ms (30 min) | Retire connections | Below the database or proxy idle timeout |
| JDK `HttpClient` connect timeout | None | Time to establish a connection | Always set (for example 2 s) |
| JDK `HttpRequest.timeout` | None | Time for the whole response | Always set per call |
| Spring `RestClient` / `RestTemplate` timeouts | From the request factory (often none) | Network timeouts | Always configure the factory |

> 🚨 **No timeout is the most common resilience bug**  
> Most Java HTTP clients wait forever by default. Every remote call needs a connect and a response timeout ([Resilience Patterns](../l4-senior/resilience-patterns.md)).

## Resilience4j

| Setting | Default | What it does | When to change it |
|---------|---------|--------------|-------------------|
| CircuitBreaker `failureRateThreshold` | 50 (%) | Failure rate that opens the circuit | Lower for critical dependencies |
| CircuitBreaker `slidingWindowSize` | 100 | Calls (or seconds) in the window | Smaller for low-traffic services |
| CircuitBreaker `minimumNumberOfCalls` | 100 | Calls before the rate is evaluated | Lower for low-traffic services, or it never opens |
| CircuitBreaker `waitDurationInOpenState` | 60 s | Time before trying half-open | Match the dependency's recovery time |
| CircuitBreaker `permittedNumberOfCallsInHalfOpenState` | 10 | Trial calls in half-open | Fewer for fragile dependencies |
| Retry `maxAttempts` | 3 | Total attempts including the first | Only for idempotent operations |
| Retry `waitDuration` | 500 ms | Fixed wait between attempts | Use exponential backoff with jitter instead |
| Bulkhead `maxConcurrentCalls` | 25 | Concurrent calls allowed | Size per dependency |
| Bulkhead `maxWaitDuration` | 0 | Wait for a permit | Keep near 0 to fail fast |
| RateLimiter `limitForPeriod` | 50 | Permits per refresh period | Match the partner's quota |
| RateLimiter `limitRefreshPeriod` | 500 ns | Period length | Almost always change (for example 1 s) |
| RateLimiter `timeoutDuration` | 5 s | Wait for a permit | Lower so callers fail fast |
| TimeLimiter `timeoutDuration` | 1 s | Timeout for async calls | Per dependency SLO |

## Caching (Caffeine)

| Setting | Default | What it does | When to change it |
|---------|---------|--------------|-------------------|
| `maximumSize` / `maximumWeight` | Unbounded | Eviction by size | Always bound caches |
| `expireAfterWrite` | None | Entries expire after a fixed time | Data with a known staleness budget |
| `expireAfterAccess` | None | Expire idle entries | Session-like data |
| `refreshAfterWrite` | None | Refresh asynchronously while serving the old value | Hot keys that must not stampede ([Caching Patterns](../l4-senior/caching-patterns.md)) |
| `recordStats()` | Off | Hit/miss/eviction stats | Always in production, exported to metrics |
| `executor` | `ForkJoinPool.commonPool()` | Runs async loads and maintenance | A dedicated executor for blocking loaders |

## Messaging (Kafka clients)

| Setting | Default | What it does | When to change it |
|---------|---------|--------------|-------------------|
| Producer `acks` | all (since 3.0) | Waits for in-sync replicas | Keep `all` for business events |
| Producer `enable.idempotence` | true (since 3.0) | No duplicates from producer retries | Keep on |
| Consumer `enable.auto.commit` | true (Spring Kafka sets false) | Commits offsets periodically | Commit after processing for at-least-once |
| Consumer `auto.offset.reset` | latest | Where a new group starts | `earliest` for new projections |
| Consumer `max.poll.records` | 500 | Records per poll | Lower when processing is slow |
| Consumer `max.poll.interval.ms` | 300000 | Max time between polls before rebalancing | Raise for slow batches, or process faster |
| Consumer `isolation.level` | read_uncommitted | Sees aborted transactional messages | `read_committed` with transactional producers |

See [Messaging Patterns](../l4-senior/messaging-patterns.md) and [Distributed Data Patterns](../l4-senior/distributed-data-patterns.md).

## JVM flags

| Flag | Default | What it does | When to change it |
|------|---------|--------------|-------------------|
| `-XX:MaxRAMPercentage` | 25 | Heap as a share of container memory | Raise to 60–75 in dedicated containers |
| `-XX:+UseG1GC` | Default collector on server-class machines | Balanced GC | ZGC (`-XX:+UseZGC`) for low-pause, large heaps |
| `-Xlog:class+load` | Off | Logs class loading | Debugging class loaders and singletons |
| `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=app.jsa` | Off | Class data sharing archive | Faster startup |
| `-XX:+HeapDumpOnOutOfMemoryError` | Off | Heap dump on OOM | Always in production |
| `-Djdk.tracePinnedThreads=full` | Off | Reports virtual-thread pinning | Debugging virtual threads before Java 24 |

## Architecture and API checks

| Setting | Default | What it does | When to change it |
|---------|---------|--------------|-------------------|
| ArchUnit `archRule.failOnEmptyShould` | true | Fails rules that match no classes | Keep on; catches broken package names |
| ArchUnit `freeze.store.default.allowStoreCreation` | false | Allows creating a baseline for frozen rules | true once, when introducing a rule to old code |
| japicmp `breakBuildOnBinaryIncompatibleModifications` | false | Fails on binary breaks | Always for published libraries |
| japicmp `breakBuildBasedOnSemanticVersioning` | false | Checks the version bump | Libraries using semantic versioning |

See [Evolutionary Architecture](../l5-staff-principal/evolutionary-architecture.md) and [Designing Frameworks and Libraries](../l5-staff-principal/framework-and-library-design.md).
