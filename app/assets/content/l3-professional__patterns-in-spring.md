# Patterns in Spring

L3 🟡 Professional · ⏱ 40 min read · Prerequisites: [Dependency Injection](../l2-practitioner/dependency-injection.md), [Proxy](../l2-practitioner/proxy.md), [Template Method](../l1-foundations/template-method.md), [Observer](../l1-foundations/observer.md)

> 📌 **TL;DR**  
> Spring is a set of design patterns you configure. The **IoC container** is a Factory plus Dependency Injection with Singleton and Prototype scopes. **AOP** features (`@Transactional`, `@Cacheable`, `@Async`, method security) are **Proxies** around your beans, which is why calling an annotated method on `this` silently skips them. **`JdbcTemplate`**, `TransactionTemplate` and `RestTemplate` are **Template Method with callbacks**. **Application events** are **Observer**. **`FactoryBean`** is Factory. Injecting `List<Strategy>` or `Map<String, Strategy>` gives you **Strategy** registries for free. Knowing the pattern behind each feature explains most Spring surprises.

> ℹ️ **These examples use real Spring**  
> Unlike the single-file examples elsewhere, this page's code is a small Maven project using Spring Framework 6.2 and an in-memory H2 database: [`examples/l3-professional/patterns-in-spring`](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/claude/add-playbook-master-prompt/examples/l3-professional/patterns-in-spring). Run one example with `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.<Name>`, or all of them with `mvn verify`; a test checks every output shown here.

## 1. What It Is

**Plain English.** You write plain classes (services, repositories, listeners). Spring creates them, connects them, and wraps some of them with extra behaviour (transactions, caching, security). Each of those jobs is a known pattern, so when Spring does something surprising, the pattern usually explains it.

**Definition.** The main pattern-to-feature map:

| Spring feature | Pattern(s) |
|----------------|------------|
| `ApplicationContext`, `@Bean`, `@Component` | Factory, Dependency Injection, Registry |
| Singleton and prototype scopes | Singleton (per container), Prototype |
| `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize` | Proxy (dynamic or CGLIB), Decorator, Chain of Responsibility (interceptor chain) |
| `JdbcTemplate`, `TransactionTemplate`, `RestTemplate`, `JmsTemplate` | Template Method (with callbacks) |
| `ApplicationEventPublisher`, `@EventListener` | Observer, Mediator |
| `FactoryBean`, `@Bean` methods | Factory Method, Abstract Factory |
| `List<T>` / `Map<String, T>` injection, `@Order` | Strategy, Chain of Responsibility |
| `BeanPostProcessor` | Decorator / hook into object creation |
| `HandlerAdapter`, `HttpMessageConverter` | Adapter, Strategy |
| `DispatcherServlet` | Front Controller |

## 2. Why It Exists

Before Spring, Java EE applications wired themselves through JNDI lookups (Service Locator), wrote transaction and resource-handling code by hand, and were hard to test outside a server. Spring applied DI, Proxy and Template Method consistently so business classes stay plain Java, cross-cutting concerns are declared, and infrastructure boilerplate disappears.

## 3. Mental Model / Analogy

**Analogy.** Spring is a stage crew. You (the actors) only learn your lines. The crew builds the set (creates beans), hands you props on cue (injects dependencies), stands invisibly between you and the audience to handle lights and sound (proxies), and announces scene changes to everyone who cares (events).

**Technical model.** At startup Spring reads bean definitions, instantiates them in dependency order, runs `BeanPostProcessor`s (which may replace a bean with a proxy), and stores singletons in a registry. Your code receives references to whatever ended up in the registry: often a proxy, not your object.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### IoC container and DI

**Description.** The `ApplicationContext` is a factory and registry. `@Configuration` classes declare beans with `@Bean` methods (or classes are found with `@Component` scanning); Spring calls constructors with the dependencies they ask for. Beans are **singletons per container** by default; **prototype** scope creates a new instance per lookup. When several beans match a type, `@Primary` or `@Qualifier` chooses.

**Advantages / features.** Classes don't create or look up their collaborators; configuration is in one place; tests can build the same classes without Spring.

**IocContainerDemo.java**

```java
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

    record OrderNotifier(SmsSender primary, SmsSender fallback) {
        String orderPlaced(String phone) { return primary.send(phone, "placed"); }
    }

    @Configuration
    static class AppConfig {
        @Bean @Primary SmsSender textBlast() { return new TextBlastSender(); }
        @Bean SmsSender backup() { return new BackupSender(); }
        @Bean OrderNotifier notifier(SmsSender primary, @Qualifier("backup") SmsSender fallback) { return new OrderNotifier(primary, fallback); }
        @Bean @Scope("prototype") CartSession cartSession() { return new CartSession(); }
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
```

1. A plain record with constructor injection: no Spring imports needed.
2. The composition root: bean methods say what to create and how.
3. Prototype scope: a new object for every request to the container.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.IocContainerDemo`

```text
textblast -> 98450 (fallback is BackupSender)
singleton: same notifier each time? true
prototype: new cart session each time? true
@Configuration class is itself proxied: true
```

| Scope | Instances | Typical use |
|-------|-----------|-------------|
| `singleton` (default) | One per container | Stateless services, repositories |
| `prototype` | One per lookup | Stateful helpers, builders |
| `request` / `session` (web) | One per HTTP request / session | Per-user state (use sparingly) |
| Custom (e.g. thread, tenant) | Defined by a `Scope` | Multi-tenant resources |

**Use** constructor injection and `@Configuration` classes. **Avoid** field injection and calling `ctx.getBean` from business code (that's Service Locator).
**Real-world Java:** Spring Boot auto-configuration is many `@Configuration` classes with `@ConditionalOn...` guards; Micronaut and Quarkus do the same wiring at build time.
**Best practice:** keep beans stateless; put state in the database or in request-scoped objects. **Gotcha:** injecting a prototype into a singleton gives the singleton one instance forever; use `ObjectProvider<T>` to get a fresh one.

### Proxies and AOP

**Description.** Spring adds behaviour such as transactions by wrapping your bean in a **proxy**: a JDK dynamic proxy for interfaces or a CGLIB subclass for classes. The proxy runs an interceptor chain (begin transaction, call your method, commit or roll back). Only calls that go **through the proxy** get the behaviour.

**Advantages / features.** Declarative cross-cutting concerns; business code stays free of transaction and caching code.

**ProxiesAopDemo.java**

```java
package com.freshcart.spring;

import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

/** Spring's cross-cutting features (transactions, caching, security) are Proxies wrapped around your beans. */
public class ProxiesAopDemo {

    public interface StockService { int available(String sku); }

    // ---------- a proxy you build by hand with ProxyFactory ----------
    static StockService timed(StockService target, StringBuilder log) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice((MethodInterceptor) invocation -> {
            log.append("before ").append(invocation.getMethod().getName()).append("; ");
            try { return invocation.proceed(); } finally { log.append("after; "); }
        });
        return (StockService) factory.getProxy();
    }

    // ---------- the proxy Spring builds for @Transactional ----------
    public static class InventoryService {
        private final JdbcTemplate jdbc;
        public InventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        public void reserveAll(String... skus) {
            for (String sku : skus) {
                int updated = jdbc.update("UPDATE stock SET qty = qty - 1 WHERE sku = ? AND qty > 0", sku);
                if (updated == 0) throw new IllegalStateException(sku + " is out of stock");
            }
        }

        public void reserveAllViaSelfCall(String... skus) { reserveAll(skus); }

        public int qty(String sku) { return jdbc.queryForObject("SELECT qty FROM stock WHERE sku = ?", Integer.class, sku); }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return Db.h2("classpath:/stock.sql");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager txManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean InventoryService inventoryService(JdbcTemplate jdbc) { return new InventoryService(jdbc); }
    }

    public static void main(String[] args) {
        StringBuilder log = new StringBuilder();
        StockService proxied = timed(sku -> 42, log);
        System.out.println("hand-made proxy: " + proxied.available("MILK") + " | " + log);

        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            InventoryService inventory = ctx.getBean(InventoryService.class);
            System.out.println("bean class is a CGLIB subclass: " + inventory.getClass().getSimpleName().contains("SpringCGLIB"));
            try { inventory.reserveAll("MILK", "PANEER"); } catch (IllegalStateException e) { System.out.println("through the proxy: " + e.getMessage()); }
            System.out.println("  MILK after rollback: " + inventory.qty("MILK"));
            try { inventory.reserveAllViaSelfCall("MILK", "PANEER"); } catch (IllegalStateException e) { System.out.println("self-invocation: " + e.getMessage()); }
            System.out.println("  MILK after self-call: " + inventory.qty("MILK") + "   <- no transaction, partial update kept");
        }
    }
}
```

1. Building a proxy by hand with `ProxyFactory` and a `MethodInterceptor`: the same machinery `@Transactional` uses.
2. `@Transactional` asks Spring to put a transaction proxy around this bean.
3. A call to `reserveAll` from inside the class goes to `this`, not the proxy.
4. Through the proxy, the failed reservation rolled back; through `this`, the first update stayed.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.ProxiesAopDemo`

```text
hand-made proxy: 42 | before available; after; 
bean class is a CGLIB subclass: true
through the proxy: PANEER is out of stock
  MILK after rollback: 10
self-invocation: PANEER is out of stock
  MILK after self-call: 9   <- no transaction, partial update kept
```

| Proxy type | When Spring uses it | Limits |
|------------|---------------------|--------|
| JDK dynamic proxy | Bean implements interfaces and `proxyTargetClass=false` | Only interface methods |
| CGLIB subclass | Classes (Spring Boot default) | Not `final` classes or methods; no `private` methods |
| AspectJ weaving | Compile/load-time weaving configured | Works for self-calls; more setup |

**Use** annotations like `@Transactional` on public methods of beans called from other beans. **Avoid** relying on them for self-calls, `private` or `final` methods.
**Real-world Java:** `@Transactional`, `@Cacheable`, `@Async`, `@Retryable`, `@PreAuthorize`, Spring Data repository interfaces (pure proxies with no class of yours behind them).
**Best practice:** put the transactional boundary on the service method that's called from outside (controller, listener). **Gotcha:** self-invocation is the most common Spring bug; move the method to another bean or call through an injected self-reference.

### Template classes

**Description.** `JdbcTemplate`, `TransactionTemplate`, `RestTemplate` and `JmsTemplate` implement the fixed part of an algorithm (get a connection, run, map results, translate exceptions, release resources) and take the varying part as a **callback** (`RowMapper`, `TransactionCallback`). This is [Template Method](../l1-foundations/template-method.md) by composition rather than inheritance.

**Advantages / features.** No resource leaks; database errors become a consistent `DataAccessException` hierarchy; transaction handling in a few lines.

**Before (raw JDBC) and after (JdbcTemplate):**

**TemplateClassesDemo.java**

```java
package com.freshcart.spring;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring's *Template classes: the fixed algorithm (open, run, map, translate errors, close) is theirs; your callback is the varying step. */
public class TemplateClassesDemo {

    record OrderRow(String id, String city, long totalPaise) { }

    // ---------- before: raw JDBC; the useful line is buried in resource handling ----------
    static List<OrderRow> deliveredBefore(DataSource ds, String city) throws SQLException {
        List<OrderRow> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, city, total_paise FROM orders WHERE status = 'DELIVERED' AND city = ? ORDER BY id")) {
            ps.setString(1, city);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new OrderRow(rs.getString("id"), rs.getString("city"), rs.getLong("total_paise")));
            }
        }
        return out;
    }

    // ---------- after: JdbcTemplate (Template Method with a callback) ----------
    static List<OrderRow> delivered(JdbcTemplate jdbc, String city) {
        return jdbc.query("SELECT id, city, total_paise FROM orders WHERE status = 'DELIVERED' AND city = ? ORDER BY id",
                (rs, rowNum) -> new OrderRow(rs.getString("id"), rs.getString("city"), rs.getLong("total_paise")), city);
    }

    public static void main(String[] args) throws SQLException {
        DataSource ds = Db.h2("classpath:/orders.sql");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        System.out.println("raw JDBC:     " + deliveredBefore(ds, "Pune"));
        System.out.println("JdbcTemplate: " + delivered(jdbc, "Pune"));

        try { jdbc.update("INSERT INTO orders VALUES ('FC-1', 'Pune', 1, 'NEW')"); }
        catch (DataAccessException e) { System.out.println("SQL error translated to: " + e.getClass().getSimpleName()); }

        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE orders SET status = 'REFUNDED' WHERE id = 'FC-2'");
                throw new IllegalStateException("payment gateway refused refund");
            });
        } catch (IllegalStateException e) { System.out.println("TransactionTemplate rolled back: " + e.getMessage()); }
        System.out.println("FC-2 status: " + jdbc.queryForObject("SELECT status FROM orders WHERE id = 'FC-2'", String.class));
    }
}
```

1. Before: correct, but most lines manage resources rather than express the query.
2. After: the callback maps one row; the template does the rest.
3. Vendor-specific SQL errors are translated into meaningful exceptions such as `DuplicateKeyException`.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.TemplateClassesDemo`

```text
raw JDBC:     [OrderRow[id=FC-1, city=Pune, totalPaise=64900]]
JdbcTemplate: [OrderRow[id=FC-1, city=Pune, totalPaise=64900]]
SQL error translated to: DuplicateKeyException
TransactionTemplate rolled back: payment gateway refused refund
FC-2 status: DELIVERED
```

| Template | Callback | Replaced by (newer) |
|----------|----------|---------------------|
| `JdbcTemplate` | `RowMapper`, `PreparedStatementSetter` | `JdbcClient` (6.1, fluent) |
| `TransactionTemplate` | `TransactionCallback` | Still current; `@Transactional` for declarative use |
| `RestTemplate` | `RequestCallback`, `ResponseExtractor` | `RestClient` (6.1) |
| `JmsTemplate` | `MessageCreator` | Still current |

**Use** `JdbcTemplate`/`JdbcClient` for SQL you want to write yourself. **Avoid** raw JDBC in application code.
**Real-world Java:** `NamedParameterJdbcTemplate`, `MongoTemplate`, `KafkaTemplate`, `RedisTemplate`.
**Best practice:** use `TransactionTemplate` when the transaction boundary must be inside a method (e.g., per batch item). **Gotcha:** `queryForObject` throws `EmptyResultDataAccessException` when no row matches; use `query(...).stream().findFirst()` for optional results.

### Application events as Observer

**Description.** A bean publishes an event object through `ApplicationEventPublisher`; any bean with an `@EventListener` method for that type receives it. Publishers don't know their listeners. By default delivery is **synchronous**, in the publisher's thread and transaction. `@Order` sets listener order; `condition` filters with SpEL; `@TransactionalEventListener` delays delivery to a transaction phase.

**Advantages / features.** Decouples the core flow from follow-up work (loyalty, notifications, analytics); new reactions are new listeners.

**ApplicationEventsDemo.java**

```java
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

    public record OrderPlaced(String orderId, long totalPaise) { }

    public static class CheckoutService {
        private final ApplicationEventPublisher events;
        public CheckoutService(ApplicationEventPublisher events) { this.events = events; }
        public void placeOrder(String id, long paise) {
            System.out.println("checkout: order " + id + " saved");
            events.publishEvent(new OrderPlaced(id, paise));
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
        @EventListener(condition = "#e.totalPaise >= 50000") @Order(2)
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
```

1. Events are plain records; no Spring base class needed since 4.2.
2. Publishing doesn't name any listener.
3. A conditional listener with an explicit order.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.ApplicationEventsDemo`

```text
checkout: order FC-1 saved
  loyalty listener: +6 points for FC-1
  sms listener: thanks for your big order FC-1
checkout: publishEvent returned
checkout: order FC-2 saved
  loyalty listener: +1 points for FC-2
checkout: publishEvent returned
listeners ran 3 times, synchronously, inside publishEvent
```

| Listener kind | Runs | Use for |
|---------------|------|---------|
| `@EventListener` | Synchronously, inside `publishEvent` | In-process reactions that may share the transaction |
| `@EventListener` + `@Async` | On an executor | Slow work that mustn't block the caller |
| `@TransactionalEventListener(AFTER_COMMIT)` | After commit | Emails, SMS, messages to other systems |
| Spring Modulith / outbox | Persisted, retried | Reliable cross-module or cross-service events |

**Use** events to decouple modules inside one application. **Avoid** using them as a hidden control flow that the core logic depends on.
**Real-world Java:** Spring Boot's `ApplicationReadyEvent`, `ContextRefreshedEvent`; Spring Security's authentication events; Spring Modulith's event publication registry.
**Best practice:** make events immutable records with only the data listeners need. **Gotcha:** an exception in a synchronous listener propagates to the publisher and can roll back its transaction.

### Factory beans

**Description.** A `FactoryBean<T>` is a bean whose job is to create another bean. The container calls `getObject()` and registers the product under the factory's name; `&name` returns the factory itself. `@Bean` methods are the everyday form of the same idea; `FactoryBean` suits reusable, configurable construction logic in libraries.

**Advantages / features.** Complex or conditional creation is encapsulated; consumers just inject the product type.

**FactoryBeanDemo.java**

```java
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

    public static class SmsClientFactoryBean implements FactoryBean<SmsClient> {
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
        @Bean SmsClientFactoryBean smsClient() { return new SmsClientFactoryBean(System.getProperty("env", "dev")); }
    }

    public static void main(String[] args) {
        for (String env : new String[] {"dev", "prod"}) {
            System.setProperty("env", env);
            try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
                SmsClient client = ctx.getBean(SmsClient.class);
                ctx.getBean(SmsClient.class);
                SmsClientFactoryBean factory = (SmsClientFactoryBean) ctx.getBean("&smsClient");
                System.out.println(env + ": " + client.send("98450") + " | factory called " + factory.created + "x");
            }
        }
    }
}
```

1. The factory decides which implementation to build from the environment.
2. Registering the factory registers its product, `SmsClient`.
3. The `&` prefix asks for the factory instead of the product.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.FactoryBeanDemo`

```text
dev: LOG ONLY: sms to 98450 | factory called 1x
prod: POST https://api.textblast.example/v2/sms to 98450 (timeout 2000 ms) | factory called 1x
```

| Option | Best for |
|--------|----------|
| `@Bean` method | Application code; most cases |
| `FactoryBean<T>` | Libraries with reusable, configurable creation (e.g. `LocalSessionFactoryBean`) |
| `@Conditional...` beans | Choose implementations by environment or classpath |
| `ObjectProvider<T>` | Lazy or optional lookup |

**Use** `@Bean` methods first, `FactoryBean` when creation logic needs to be a reusable component. **Avoid** environment checks scattered in services; choose implementations at wiring time.
**Real-world Java:** `LocalContainerEntityManagerFactoryBean`, `SqlSessionFactoryBean` (MyBatis), `ProxyFactoryBean`, Spring Data's repository factory beans.
**Best practice:** implement `getObjectType()` accurately so type-based injection works before the product exists. **Gotcha:** `isSingleton()` defaults to `true`, so `getObject()` is cached; override it for a new product per lookup.

### Strategy beans with List/Map injection

**Description.** Declare each strategy as a bean implementing a shared interface. Inject `List<Strategy>` to get all of them in `@Order`/`Ordered` order (a chain or pipeline), or `Map<String, Strategy>` to get them keyed by bean name (a registry). Adding a strategy is adding a bean.

**Advantages / features.** No hand-written registry; open for extension; ordering and selection are declarative.

**StrategyBeansDemo.java**

```java
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

    record FeeCalculator(List<DeliveryFeeRule> rules) {
        long fee(long cartPaise) { long f = 0; for (DeliveryFeeRule r : rules) f = r.apply(f, cartPaise); return f; }
    }
    record PaymentRouter(Map<String, PaymentProcessor> byName) {
        String pay(String mode, long paise) {
            PaymentProcessor p = byName.get(mode);
            if (p == null) throw new IllegalArgumentException("unsupported payment mode '" + mode + "', supported " + byName.keySet());
            return p.pay(paise);
        }
    }

    @Configuration
    static class Config {
        @Bean RainSurcharge rain() { return new RainSurcharge(); }
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
```

1. A chain of fee rules injected as an ordered list.
2. A router keyed by bean name.
3. Declaration order doesn't matter; `@Order` on the classes decides list order.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.StrategyBeansDemo`

```text
rule order: [base, free>=499, rain]
fee for Rs 300 cart: 3500, for Rs 650 cart: 1000
UPI collect request for Rs 649 | card charge Rs 649
unsupported payment mode 'cod', supported [upi, card]
```

| Injection | You get | Use for |
|-----------|---------|---------|
| `List<T>` | All beans of type `T`, ordered | Chains, pipelines, validators |
| `Map<String, T>` | Bean name → bean | Lookup by key (payment mode, channel) |
| `ObjectProvider<T>` | Lazy/optional access, `orderedStream()` | Optional strategies |
| `T` with `@Qualifier` | One chosen bean | A fixed choice |

**Use** list and map injection for open sets of strategies. **Avoid** keying business logic on bean names that refactoring can change; prefer a `key()` method on the strategy and build the map yourself, as in Example 3.
**Real-world Java:** Spring MVC's `HandlerMethodArgumentResolver` and `HttpMessageConverter` lists; Spring Security's filter chain; Boot's `HealthIndicator` map.
**Best practice:** validate at startup that every required key has a strategy. **Gotcha:** if no bean of the type exists, injecting `List<T>` fails unless it's optional (`ObjectProvider` or `@Autowired(required = false)`).

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop's ETA service needs a traffic client. The classes must work without Spring, and Spring should only wire them.

**SpringWiringDemo.java**

```java
package com.freshcart.spring;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Minimal: QuickDrop's ETA service wired by Spring. The classes are plain Java; Spring only builds and connects them. */
public class SpringWiringDemo {

    record TrafficClient(double slowdown) { }
    record EtaService(TrafficClient traffic) {
        int minutes(double km) { return (int) Math.ceil(km * 3 * traffic.slowdown()); }
    }

    @Configuration
    static class Config {
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
```

1. Plain records with constructor injection.
2. The configuration is the only Spring-specific code.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.SpringWiringDemo`

**Output:**

```text
4 km -> 18 min
without Spring, same result: 18 min
```

**What to notice:**

- The same objects work with and without the container, so unit tests don't need Spring.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** checkout charges the order through the payment mode the customer picked, records the payment, marks the order paid, and triggers a receipt. A failed charge must leave no payment row and no status change.

**CheckoutFlowDemo.java**

```java
package com.freshcart.spring;

import java.util.Map;
import javax.sql.DataSource;
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

/**
 * Realistic: FreshCart checkout as Spring wires it in production. Payment strategies are beans in a Map,
 * the checkout is a transactional proxy around JdbcTemplate calls, and follow-up work listens for an event.
 */
public class CheckoutFlowDemo {

    public interface PaymentProcessor { String charge(String orderId, long paise); }
    public record OrderPaid(String orderId, String mode) { }

    public static class CheckoutService {
        private final JdbcTemplate jdbc; private final Map<String, PaymentProcessor> processors; private final ApplicationEventPublisher events;
        public CheckoutService(JdbcTemplate jdbc, Map<String, PaymentProcessor> processors, ApplicationEventPublisher events) {
            this.jdbc = jdbc; this.processors = processors; this.events = events;
        }

        @Transactional
        public String pay(String orderId, String mode) {
            long total = jdbc.queryForObject("SELECT total_paise FROM orders WHERE id = ?", Long.class, orderId);
            PaymentProcessor processor = processors.get(mode);
            if (processor == null) throw new IllegalArgumentException("unsupported mode " + mode);
            String ref = processor.charge(orderId, total);
            jdbc.update("INSERT INTO payments VALUES (?, ?, ?)", orderId, mode, ref);
            jdbc.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId);
            events.publishEvent(new OrderPaid(orderId, mode));
            return ref;
        }
    }

    public static class ReceiptListener {
        @EventListener public void onPaid(OrderPaid e) { System.out.println("  receipt queued for " + e.orderId() + " (" + e.mode() + ")"); }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return Db.h2("classpath:/orders.sql"); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean PaymentProcessor upi() { return (id, p) -> "UPI-" + id; }
        @Bean PaymentProcessor card() { return (id, p) -> { if (p > 50_000) throw new IllegalStateException("card limit exceeded"); return "CARD-" + id; }; }
        @Bean CheckoutService checkout(JdbcTemplate jdbc, Map<String, PaymentProcessor> ps, ApplicationEventPublisher ev) { return new CheckoutService(jdbc, ps, ev); }
        @Bean ReceiptListener receipts() { return new ReceiptListener(); }
    }

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            CheckoutService checkout = ctx.getBean(CheckoutService.class);
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            System.out.println("FC-2 by upi: " + checkout.pay("FC-2", "upi"));
            try { checkout.pay("FC-1", "card"); } catch (IllegalStateException e) { System.out.println("FC-1 by card failed: " + e.getMessage()); }
            System.out.println("payments rows: " + jdbc.queryForList("SELECT order_id FROM payments", String.class)
                    + ", FC-1 status: " + jdbc.queryForObject("SELECT status FROM orders WHERE id = 'FC-1'", String.class));
        }
    }
}
```

1. One transaction around the whole payment, provided by a proxy.
2. Payment strategies come from a `Map` injected by Spring.
3. Follow-up work listens for an event instead of being called directly.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.CheckoutFlowDemo`

**Output:**

```text
  receipt queued for FC-2 (upi)
FC-2 by upi: UPI-FC-2
FC-1 by card failed: card limit exceeded
payments rows: [FC-2], FC-1 status: DELIVERED
```

**What to notice:**

- The receipt line printed before `pay` returned: plain `@EventListener` runs inside the transaction. Example 3 shows why that matters.
- The failed card payment left FC-1 untouched and wrote no payment row.

### Example 3: Production-grade

**Scenario:** PayLink places orders and charges them. After incidents, the team requires: every payment mode has a processor or the service won't start; a declined payment (a **checked** exception) must roll back the order; the "order confirmed" SMS must never go out for a rolled-back order; every call to the order service is audited without adding code to it.

**OrderPlacementProductionDemo.java**

```java
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
        public PaymentRouter(List<PaymentProcessor> processors) {
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

        @Transactional(rollbackFor = Exception.class)
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
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void sms(OrderConfirmed e) { sent.add(e.orderId()); }
        @EventListener
        public void naiveSms(OrderConfirmed e) { naive.add(e.orderId()); }
    }

    static BeanPostProcessor auditing(List<String> auditLog) {
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
```

1. The router builds its own map from each strategy's `mode()` and fails startup if any mode is missing.
2. By default Spring rolls back only on unchecked exceptions; `rollbackFor = Exception.class` includes checked ones like `PaymentDeclined`.
3. The SMS listener runs only after a successful commit.
4. A plain `@EventListener` shown for contrast: it fires immediately, even for orders that are later rolled back.
5. A `BeanPostProcessor` that wraps `@Audited` beans in a proxy: how Spring's own AOP features are added.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.spring.OrderPlacementProductionDemo`

**Output:**

```text
FC-10 paid: UPI-FC-10
FC-11: card declined: limit
orders in DB: [FC-10]
SMS after commit: [FC-10] | naive listener would have sent: [FC-10, FC-11]
audit: [orders.place ok, orders.place failed: card declined: limit]
startup refused: no PaymentProcessor bean for [CARD]
```

**What to notice:**

- FC-11 was rolled back: it isn't in the database, and the after-commit SMS skipped it, while the naive listener would have texted the customer.
- The audit proxy recorded both the success and the failure without any code in `OrderService`.
- The broken configuration (no CARD processor) failed at startup with a clear message.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Productivity | Wiring, transactions and resources handled for you | "Magic" hides control flow |
| Testability | Plain classes, constructor injection | Integration tests need a context (slower) |
| Cross-cutting concerns | Declarative via proxies | Self-invocation and `final` methods bypass proxies |
| Extensibility | Strategies and listeners added as beans | Implicit ordering and conditions can surprise |
| Startup | Validation possible at startup | Large contexts start slowly (reflection, scanning) |

## 8. When to Use / When NOT to Use

**Use these Spring patterns when:**

- you're building a Spring application: lean on DI, proxies, templates and events instead of re-inventing them;
- cross-cutting concerns (transactions, caching, security) apply across many classes;
- strategies or listeners are added over time.

**Don't use them when:**

- a small library or tool would need a whole container for a few objects (wire by hand);
- behaviour must be explicit and visible (a direct call is clearer than an event);
- hot code paths can't afford proxy overhead (rare; measure first).

## 9. Best Practices

1. **Constructor injection, no field injection.** *(Consensus; Spring team recommendation.)*
2. **Transactional boundaries on public service methods called from other beans.** *(Consensus.)*
3. **`rollbackFor = Exception.class`** when checked exceptions mean failure, or use unchecked exceptions. *(Consensus.)*
4. **`@TransactionalEventListener(AFTER_COMMIT)`** for side effects outside the database; an outbox for reliability. *(Consensus.)*
5. **Validate strategy registries at startup.** *(Opinion, widely shared.)*
6. **Prefer `@Bean` methods over `FactoryBean`** in application code. *(Consensus.)*
7. **Keep business classes free of Spring imports** where practical. *(Opinion, widely shared.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Self-invocation skips `@Transactional` (production failure mode)**  
> **Symptom:** after a refactor, failed multi-item reservations left some items reserved; stock counts drifted for days.
> **Root cause:** a public method called the class's own `@Transactional` method. The call went to `this`, not to the proxy, so there was no transaction and nothing rolled back.
> **Fix:** move the transactional method to another bean or make the caller's method the transactional boundary; add an integration test that a failure rolls back.

> 🚨 **Checked exceptions don't roll back**  
> **Symptom:** orders were saved even though payment was declined.
> **Root cause:** `PaymentDeclined` was a checked exception; `@Transactional` rolls back only on `RuntimeException` and `Error` by default, so the transaction committed.
> **Fix:** `@Transactional(rollbackFor = Exception.class)`, or make the exception unchecked.

> 🚨 **Notifications sent for rolled-back work**  
> **Symptom:** customers got "order confirmed" SMS for orders that didn't exist.
> **Root cause:** a plain `@EventListener` sent the SMS synchronously inside the transaction, before a later step failed and rolled back.
> **Fix:** `@TransactionalEventListener(phase = AFTER_COMMIT)`; for guaranteed delivery, a transactional outbox.

> 🚨 **Prototype inside a singleton**  
> **Symptom:** every user's cart session shared the same items.
> **Root cause:** a prototype-scoped `CartSession` was injected into a singleton service once at startup.
> **Fix:** inject `ObjectProvider<CartSession>` and call `getObject()` per use, or don't keep per-user state in beans.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `spring.aop.proxy-target-class` | `true` in Boot | CGLIB class proxies vs JDK interface proxies | Interface-only codebases | Behaviour differs for `final` and interface-less beans |
| `@Transactional(rollbackFor)` | Unchecked only | Which exceptions roll back | Checked domain exceptions | Unintended rollbacks |
| `@Transactional(propagation)` | `REQUIRED` | Join or create transactions | Audit logs that must survive rollbacks (`REQUIRES_NEW`) | Deadlocks, partial commits |
| `spring.main.lazy-initialization` | `false` | Create beans on first use | Faster startup in dev | Errors found late |
| `@EnableAsync` executor | Simple executor in Boot (task pool) | Runs `@Async` listeners | Size for workload | Lost events on shutdown |
| `spring.jpa.open-in-view` | `true` | Keeps session open during web rendering | Set `false` in APIs | Lazy-loading errors if code relied on it |

## 12. Security Considerations

- Method security (`@PreAuthorize`) is also a proxy: self-calls bypass authorisation checks just as they bypass transactions.
- Don't expose `ApplicationContext` or `getBean(String)` with user-controlled names (it becomes an arbitrary-object lookup).
- SpEL in annotations (`condition`, `@PreAuthorize`) is safe when written by developers; never evaluate user input as SpEL with a `StandardEvaluationContext`.
- Event listeners run with the publisher's security context synchronously, but not on `@Async` threads unless you propagate it (`DelegatingSecurityContextExecutor`).

## 13. Performance & Scalability

Proxy calls add a small interceptor-chain cost (sub-microsecond), negligible next to database and network work. Context startup is the main cost: classpath scanning, reflection and CGLIB generation. Spring Boot 3's AOT processing and GraalVM native images precompute much of it. `JdbcTemplate` adds almost nothing over raw JDBC. Synchronous events cost a method call per listener; `@Async` listeners need a bounded executor to avoid unbounded queues.

## 14. Testing It

The Maven project has two test classes. `GoldenOutputTest` runs each example and compares its output with the files shown on this page. `SpringPatternsTest` checks the behaviours the page relies on against the real framework:

**SpringPatternsTest.java**

```java
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
```

In Spring Boot apps, use `@SpringBootTest` for full contexts, slice tests (`@DataJdbcTest`, `@WebMvcTest`) for faster focused ones, and plain unit tests with constructor injection for business logic.

## 15. Observability & Operations

Boot's `/actuator/beans` and `/actuator/conditions` show which beans exist and why; `/actuator/mappings` shows web routes. Log the strategies registered at startup. Enable `logging.level.org.springframework.transaction=DEBUG` temporarily to see transaction boundaries when debugging self-invocation. Micrometer observes `@Transactional`, `JdbcTemplate` and event listener timings when instrumented.

## 16. Version Notes

| Spring | Change relevant to patterns |
|--------|-----------------------------|
| 2.5 | Annotation-driven DI (`@Autowired`, `@Component`) |
| 3.0 | Java `@Configuration` and `@Bean` |
| 4.2 | `@EventListener`, `@TransactionalEventListener`, events as any object |
| 4.3 | Implicit constructor injection with a single constructor |
| 5.0 | Functional bean registration; `@Nullable` |
| 6.0 / Boot 3 | Java 17 baseline, Jakarta EE namespace, AOT and native images |
| 6.1 | `JdbcClient`, `RestClient`; virtual threads support |
| 6.2 | Fallback beans (`@Fallback`), background bean initialisation |
| 7.0 (2025) | Java 17+ still, JSpecify null-safety, API versioning, resilience annotations |

## 17. Expert Deep Dive

- **How proxies are created.** `AbstractAutoProxyCreator` (a `BeanPostProcessor`) finds advisors that match a bean and wraps it. `@EnableTransactionManagement` registers the advisor; `@Transactional` methods are matched by a pointcut. Your own `BeanPostProcessor` in Example 3 is a miniature version.
- **`@Configuration` is a proxy too.** Bean methods in a `@Configuration` class are intercepted so `a()` called twice returns the same singleton. With `proxyBeanMethods = false` (Boot's own configs), calls are plain method calls; inject beans as parameters instead.
- **Transaction synchronisation.** `@TransactionalEventListener` registers a `TransactionSynchronization`; if no transaction is active, the event is dropped unless `fallbackExecution = true`.
- **Transactional outbox.** For events that must reach other services, write them to an outbox table in the same transaction and publish asynchronously (Spring Modulith's event publication registry, Debezium).
- **Build-time DI.** Micronaut, Quarkus and Dagger generate wiring code at compile time, trading some flexibility for startup speed; Spring's AOT engine moves in the same direction.

> 📖 **War story: the SMS for orders that never existed**  
> FreshCart's checkout published `OrderConfirmed` inside a `@Transactional` method, and an `@EventListener` sent the SMS immediately. A later step in the same method reserved a delivery slot and sometimes failed when slots ran out, rolling back the order. During a Diwali rush, 3,200 customers received "Your order is confirmed!" for orders that had been rolled back, then saw nothing in the app. Support was overwhelmed. **Fixes:** the listener became `@TransactionalEventListener(phase = AFTER_COMMIT)`; slot reservation moved before order creation; and an integration test forces a slot failure and asserts no SMS. Later they adopted an outbox so SMS survived app restarts between commit and send. **Lesson:** listeners in Spring are synchronous and inside the transaction unless you say otherwise; side effects outside the database belong after commit.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is IoC?** The **container creates and wires objects**, instead of objects doing it themselves.
2. **Default bean scope?** **Singleton** per container.
3. **Which pattern is `JdbcTemplate`?** **Template Method** with callbacks.
4. **How does `@Transactional` work?** A **proxy** starts and ends the transaction around the method.
5. **How do you get all beans of an interface?** Inject a **`List<T>`** (or `Map<String, T>`).

**▸ 🟡 L3**

1. **Why doesn't `@Transactional` work on self-calls?** The call **bypasses the proxy**.
2. **JDK proxy vs CGLIB?** **Interfaces** vs **subclass**; CGLIB can't proxy `final`.
3. **Which exceptions roll back by default?** **Unchecked** (`RuntimeException`, `Error`).
4. **What's `@TransactionalEventListener` for?** Running listeners **after commit** (or other phases).
5. **What does `&` mean in `getBean("&name")`?** Return the **FactoryBean itself**.

**▸ 🔴 L4–L5**

1. **How would you guarantee notifications for committed orders only, even across restarts?** **Transactional outbox** + async publisher; `AFTER_COMMIT` alone isn't durable.
2. **How does Spring create proxies internally?** **`BeanPostProcessor` auto-proxy creators** with advisors and pointcuts.
3. **Prototype in singleton: how to fix?** **`ObjectProvider`**, lookup method injection, or scoped proxies.
4. **How do you speed up Spring startup?** **AOT/native**, lazy init, fewer auto-configs, `proxyBeanMethods = false`.
5. **When avoid events?** When the **core flow depends on the result** or ordering must be explicit.

**▸ 💻 Hands-on**

**Task:** Fix `ProxiesAopDemo.InventoryService.reserveAllViaSelfCall` so it runs in a transaction.

> ✅ **Solution**  
> ```java
> // Option 1: make the outer method the boundary
> @Transactional
> public void reserveAllViaSelfCall(String... skus) { reserveAll(skus); }
>
> // Option 2: call through the proxy via another bean
> public static class ReservationFacade {
>     private final InventoryService inventory;   // the injected proxy
>     public ReservationFacade(InventoryService inventory) { this.inventory = inventory; }
>     public void reserve(String... skus) { inventory.reserveAll(skus); }
> }
> ```

## 19. Practice

- [ ] Add a `@Cacheable` price lookup to the Maven project and show that self-calls bypass the cache too.
    > 💡 **Hint**  
    > Add `@EnableCaching` and a `ConcurrentMapCacheManager` bean.

- [ ] Make the SMS listener in Example 3 `@Async` and observe that the calling thread no longer waits.
    > 💡 **Hint**  
    > `@EnableAsync` and print `Thread.currentThread().getName()`.

- [ ] Rewrite `TemplateClassesDemo.delivered` with `JdbcClient` (Spring 6.1).
    > 💡 **Hint**  
    > `JdbcClient.create(ds).sql(...).param(city).query(OrderRow.class).list()`.

- [ ] Find a `@Transactional` method in your codebase that throws a checked exception and check its `rollbackFor`.
    > 💡 **Hint**  
    > Search for `@Transactional` methods with `throws` clauses.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Container = Factory + DI + Singleton registry; constructor injection.
> - `@Transactional`/`@Cacheable`/`@Async` = Proxy: self-calls, `private`, `final` bypass it.
> - Rollback on unchecked by default; `rollbackFor` for checked.
> - `*Template` = Template Method with callbacks; `JdbcClient`/`RestClient` are the new fluent forms.
> - Events = Observer, synchronous by default; side effects `AFTER_COMMIT` (or outbox).
> - `List<T>`/`Map<String,T>` injection = Strategy registry; validate at startup.
>
> ```java
> @Transactional(rollbackFor = Exception.class)
> public String place(Order o) throws PaymentDeclined 
>
> @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
> public void sms(OrderConfirmed e) 
> ```

## 21. Further Reading

- [Spring Framework reference: Core Technologies](https://docs.spring.io/spring-framework/reference/core.html) (IoC container, AOP, events).
- [Spring Framework reference: Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html).
- Rod Johnson, *Expert One-on-One J2EE Design and Development* (2002): the book Spring grew out of.
- Josh Long, *Reactive Spring* and the "Spring Tips" series.
- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/) (event publication registry).

---

[← Previous: Patterns in the JDK](patterns-in-the-jdk.md) · [Next: Concurrency Patterns →](concurrency-patterns.md)
