# Object Pool

L2 🔵 Practitioner · ⏱ 35 min read · Prerequisites: [Singleton](../l1-foundations/singleton.md), [Factory Method](../l1-foundations/factory-method.md)

> 📌 **TL;DR**  
> An object pool keeps a limited set of expensive objects (database connections, threads, heavyweight clients) and lends them out instead of creating new ones each time. Pools also *limit* how many are in use, which protects scarce resources. On today's JVM, don't pool cheap objects, and with virtual threads, don't pool threads: limit access to the resource instead.

## 1. What It Is

**Plain English.** A FreshCart store has two label printers. Packers don't buy a printer for every order; they take one from the shelf, print, and put it back. If both are in use, the next packer waits a moment. That shelf is an object pool: a fixed set of reusable things, lent out and returned.

**Definition.** Object Pool isn't one of the original GoF patterns, but it's a standard creational pattern: "Manage a set of initialised, reusable objects; clients borrow an object, use it, and return it, instead of creating and destroying it." Parts:

- **Pool:** creates objects (often lazily) up to a maximum, lends and receives them.
- **Pooled object:** expensive to create, reusable after a reset.
- **Lease** (optional): a handle that returns the object when closed.
- **Policies:** maximum size, wait timeout, validation, eviction, leak detection.

## 2. Why It Exists

Some objects are expensive to create (a database connection needs a TCP and TLS handshake and authentication; a renderer loads fonts), and some resources are limited (the database allows 100 connections). Creating one per request is slow and can overload the resource. A pool pays the creation cost a few times, reuses the objects, and caps concurrent use, turning overload into a short wait or a clear error.

## 3. Mental Model / Analogy

**Analogy.** Shopping trolleys at a supermarket entrance. There are 50; you take one, use it, return it. When they're all out, you wait for someone to bring one back. Broken trolleys are taken out of service, and very old ones are replaced. If people take trolleys home (leaks), the store runs out.

**Technical model.** A thread-safe collection of idle objects plus a counter or semaphore for the maximum. `borrow()` takes an idle object (or creates one if under the maximum, or waits); `return()` resets and puts it back.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Pool structure and lifecycle

**Description.** A pool's lifecycle per object: **create** (lazily, up to the maximum) → **borrow** (validate first) → **use** → **reset** → **return**, and eventually **evict** (broken or too old). When the pool is exhausted, `borrow` waits up to a timeout and then fails clearly.

**Advantages / features.** Reuse of expensive objects; a hard cap on concurrent use; broken objects removed automatically.

**PoolLifecycleDemo.java**

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The object pool lifecycle: create lazily up to a max, borrow (validate), use, reset, return; wait or fail when exhausted. */
public class PoolLifecycleDemo {

    static final class Pool<T> {
        private final Deque<T> idle = new ArrayDeque<>();
        private final Semaphore permits;
        private final Supplier<T> factory; private final Predicate<T> valid; private final Consumer<T> reset;
        int created;

        Pool(int max, Supplier<T> factory, Predicate<T> valid, Consumer<T> reset) {
            this.permits = new Semaphore(max, true); this.factory = factory; this.valid = valid; this.reset = reset;
        }

        T borrow(long timeoutMs) throws InterruptedException {
            if (!permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS))
                throw new IllegalStateException("pool exhausted after " + timeoutMs + "ms");
            synchronized (this) {
                while (!idle.isEmpty()) {
                    T t = idle.pop();
                    if (valid.test(t)) return t;
                }
                created++;
                return factory.get();
            }
        }

        void giveBack(T t) {
            reset.accept(t);
            synchronized (this) { idle.push(t); }
            permits.release();
        }
    }

    static final class Scanner { boolean healthy = true; String lastSku; }

    public static void main(String[] args) throws InterruptedException {
        Pool<Scanner> scanners = new Pool<>(2, Scanner::new, s -> s.healthy, s -> s.lastSku = null);
        Scanner a = scanners.borrow(10); a.lastSku = "MILK";
        Scanner b = scanners.borrow(10);
        try { scanners.borrow(50); } catch (IllegalStateException e) { System.out.println("third borrow: " + e.getMessage()); }
        scanners.giveBack(a);
        Scanner again = scanners.borrow(10);
        System.out.println("reused the returned scanner: " + (again == a) + ", state reset: " + (again.lastSku == null));
        again.healthy = false;
        scanners.giveBack(b); scanners.giveBack(again);                                   // broken one is on top of the idle stack
        Scanner next = scanners.borrow(10);
        System.out.println("broken scanner skipped, got healthy one: " + next.healthy + ", total created: " + scanners.created);
    }
}
```

1. A fair semaphore enforces the maximum and serves waiting borrowers in order.
2. Exhaustion is a timed wait, then a clear error, never an endless hang.
3. Validate on borrow; broken objects are dropped.
4. Reset on return so the next borrower doesn't see the previous user's state.

**Run it:** `java PoolLifecycleDemo.java`

```text
third borrow: pool exhausted after 50ms
reused the returned scanner: true, state reset: true
broken scanner skipped, got healthy one: true, total created: 2
```

| Pros | Cons |
|------|------|
| Creation cost paid rarely | Pool code is concurrency code: easy to get wrong |
| Caps concurrent use of a scarce resource | Objects must be reset correctly |
| Broken objects detected on borrow | Borrowers can leak objects (never return them) |

**Use it** for expensive objects or limited resources. **Avoid** writing your own pool when a mature one exists (HikariCP, Apache Commons Pool).
**Real-world Java:** HikariCP, Apache Commons Pool 2, `ThreadPoolExecutor`, Netty's `PooledByteBufAllocator`.
**Best practice:** always borrow with a timeout and return in `finally` (or a lease with try-with-resources). **Gotcha:** forgetting the reset step leaks state between users (see "When pooling hurts").

### Connection pools (HikariCP)

**Description.** The most important pool in Java back ends. A JDBC connection pool keeps open connections; `getConnection()` borrows one and `close()` returns it. Key settings in HikariCP: `maximumPoolSize`, `connectionTimeout` (how long to wait), `maxLifetime` (retire connections before the database or network does), `idleTimeout`, and `leakDetectionThreshold`.

**Advantages / features.** Requests skip connection setup; the database is protected from connection storms; slow or leaked usage becomes visible.

**ConnectionPoolDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The ideas behind a JDBC connection pool like HikariCP, in miniature:
 * maximumPoolSize, connectionTimeout (wait for a free connection), and why holding a connection
 * during slow, non-database work starves everyone else.
 */
public class ConnectionPoolDemo {

    record Connection(int id) { String query(String sql) { return "conn" + id + ": " + sql; } }

    static final class MiniPool {
        private final BlockingQueue<Connection> free;
        MiniPool(int maximumPoolSize) {
            free = new ArrayBlockingQueue<>(maximumPoolSize);
            for (int i = 1; i <= maximumPoolSize; i++) free.add(new Connection(i));
        }
        Connection getConnection(long connectionTimeoutMs) throws InterruptedException {
            Connection c = free.poll(connectionTimeoutMs, TimeUnit.MILLISECONDS);
            if (c == null) throw new IllegalStateException("Connection is not available, request timed out after " + connectionTimeoutMs + "ms");
            return c;
        }
        void close(Connection c) { free.add(c); }
        int idle() { return free.size(); }
    }

    public static void main(String[] args) throws Exception {
        MiniPool pool = new MiniPool(2);

        // BAD: two requests hold connections while calling a slow payment API
        Connection a = pool.getConnection(100);
        Connection b = pool.getConnection(100);
        List<String> results = new ArrayList<>();
        try { pool.getConnection(100); } catch (IllegalStateException e) { results.add("third request: " + e.getMessage()); }
        pool.close(a); pool.close(b);

        // GOOD: borrow, query, return quickly; do slow work without a connection
        for (String order : List.of("FC-1", "FC-2", "FC-3", "FC-4")) {
            Connection c = pool.getConnection(100);
            try { results.add(c.query("SELECT total FROM orders WHERE id='" + order + "'")); }
            finally { pool.close(c); }
        }
        results.forEach(System.out::println);
        System.out.println("idle connections at the end: " + pool.idle());
    }
}
```

1. A fixed set of connections, created up front (HikariCP keeps a minimum idle number ready).
2. Wait up to `connectionTimeout` for a free connection.
3. Holding connections while doing slow non-database work starves other requests.
4. Borrow, query, return: always in `finally` (or try-with-resources on a real `Connection`).

**Run it:** `java ConnectionPoolDemo.java`

```text
third request: Connection is not available, request timed out after 100ms
conn1: SELECT total FROM orders WHERE id='FC-1'
conn2: SELECT total FROM orders WHERE id='FC-2'
conn1: SELECT total FROM orders WHERE id='FC-3'
conn2: SELECT total FROM orders WHERE id='FC-4'
idle connections at the end: 2
```

| HikariCP setting | Default | Meaning |
|------------------|---------|---------|
| `maximumPoolSize` | 10 | Most connections open at once |
| `connectionTimeout` | 30 s | Wait for a connection before failing |
| `maxLifetime` | 30 min | Retire connections before infrastructure kills them |
| `idleTimeout` | 10 min | Close extra idle connections (when min idle < max) |
| `leakDetectionThreshold` | 0 (off) | Log a stack trace if a connection is held longer |

**Use** a connection pool for every JDBC application; Spring Boot configures HikariCP by default. **Avoid** huge pools; more connections than the database can serve make everything slower.
**Real-world Java:** HikariCP (Spring Boot default), Tomcat JDBC pool, Agroal (Quarkus).
**Best practice:** keep transactions short and never call remote APIs while holding a connection. **Gotcha:** a pool of 10 per instance × 50 instances = 500 database connections; size pools against the database limit, not per service.

> ⚠️ **Verify against the official docs**  
> Default values above are from HikariCP's documentation at the time of writing; check the version you use.

### Thread pools as object pools

**Description.** `ThreadPoolExecutor` pools threads: a core and maximum number of workers, a work queue, and a rejection policy when both are full. It's an object pool where the "objects" are threads and the "borrowers" are tasks.

**Advantages / features.** Thread creation cost avoided; concurrency capped; back-pressure via a bounded queue and rejection policy.

**ThreadPoolDemo.java**

```java
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A thread pool is an object pool of threads: fixed workers, a BOUNDED queue, and a policy for when both are full. */
public class ThreadPoolDemo {

    public static void main(String[] args) throws Exception {
        CountDownLatch release = new CountDownLatch(1), started = new CountDownLatch(2);
        AtomicInteger rejected = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3),
                (task, executor) -> rejected.incrementAndGet());

        for (int i = 1; i <= 8; i++) {
            pool.execute(() -> { started.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        }
        started.await();
        System.out.println("active=" + pool.getActiveCount() + " queued=" + pool.getQueue().size() + " rejected=" + rejected.get());
        release.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("completed=" + pool.getCompletedTaskCount() + ", threads ever created=" + pool.getLargestPoolSize());
        System.out.println("built-in policies: " + List.of("AbortPolicy (throw)", "CallerRunsPolicy (back-pressure)", "DiscardPolicy", "DiscardOldestPolicy"));
    }
}
```

1. Two worker threads, never more.
2. A *bounded* queue: at most three waiting tasks.
3. A rejection handler decides what happens when workers and queue are full (here: count it).
4. Only two threads were ever created for five completed tasks.

**Run it:** `java ThreadPoolDemo.java`

```text
active=2 queued=3 rejected=3
completed=5, threads ever created=2
built-in policies: [AbortPolicy (throw), CallerRunsPolicy (back-pressure), DiscardPolicy, DiscardOldestPolicy]
```

| Choice | Effect |
|--------|--------|
| Unbounded queue (`Executors.newFixedThreadPool`) | Never rejects; memory grows without limit under overload |
| Bounded queue + `AbortPolicy` | Fails fast with `RejectedExecutionException` |
| Bounded queue + `CallerRunsPolicy` | Slows the submitter down (natural back-pressure) |
| Separate pools per dependency (bulkheads) | One slow dependency can't use every thread |

**Use** bounded queues and an explicit rejection policy in servers. **Avoid** `Executors.newCachedThreadPool()` for unbounded work; it can create thousands of platform threads.
**Real-world Java:** Tomcat's request thread pool, `ForkJoinPool.commonPool()`, Spring's `ThreadPoolTaskExecutor`.
**Best practice:** name threads (via a `ThreadFactory`) and export pool metrics. **Gotcha:** `newFixedThreadPool` uses an unbounded `LinkedBlockingQueue`.

### When pooling hurts on the modern JVM

**Description.** Pooling was common in early Java because allocation and garbage collection were slow. Modern JVMs allocate small objects in nanoseconds (thread-local allocation buffers) and collect short-lived objects very cheaply. Pooling cheap objects now usually *hurts*: pooled objects live long, get promoted to the old generation, need synchronisation, and carry state from one user to the next.

**Advantages of knowing it.** You avoid adding complexity and bugs for no gain.

**WhenPoolingHurtsDemo.java**

```java
import java.util.ArrayDeque;
import java.util.Deque;

/** Pooling cheap objects buys nothing on a modern JVM and adds a classic bug: state from the previous user leaks. */
public class WhenPoolingHurtsDemo {

    static final class ReceiptBuffer { final StringBuilder sb = new StringBuilder(); String customer; }

    static final Deque<ReceiptBuffer> POOL = new ArrayDeque<>();
    static ReceiptBuffer borrow() { return POOL.isEmpty() ? new ReceiptBuffer() : POOL.pop(); }
    static void giveBack(ReceiptBuffer b) { POOL.push(b); }

    static String receipt(String customer, String items) {
        ReceiptBuffer b = borrow();
        if (b.customer == null) b.customer = customer;
        b.sb.append(items);
        String out = "Receipt for " + b.customer + ": " + b.sb;
        giveBack(b);
        return out;
    }

    static String receiptWithoutPool(String customer, String items) {
        return "Receipt for " + customer + ": " + new StringBuilder().append(items);
    }

    public static void main(String[] args) {
        System.out.println(receipt("asha", "MILK,BREAD"));
        System.out.println(receipt("ravi", "EGGS"));
        System.out.println("without pool: " + receiptWithoutPool("ravi", "EGGS"));
    }
}
```

1. A pool of small buffers, with no reset on return.
2. The second customer gets the first customer's name and items.
3. Just allocating a new `StringBuilder` is simpler, faster to write, and correct.

**Run it:** `java WhenPoolingHurtsDemo.java`

```text
Receipt for asha: MILK,BREAD
Receipt for asha: MILK,BREADEGGS
without pool: Receipt for ravi: EGGS
```

| Pool it | Don't pool it |
|---------|---------------|
| Database and network connections | `StringBuilder`, small DTOs, collections |
| Threads (platform threads) | Virtual threads |
| Objects wrapping native resources | Objects that are only memory |
| Objects with measured, high setup cost | Anything without a profiler showing a problem |

**Use** pooling only for resource-holding or measurably expensive objects. **Avoid** pooling to "help the GC" without measurements.
**Real-world Java:** Netty pools *direct* byte buffers (off-heap, expensive to allocate) but not ordinary objects.
**Best practice:** profile allocation with JFR before pooling anything. **Gotcha:** pooled objects referencing request data keep it alive and can leak it to the next request.

### Virtual threads and pooling

**Description.** Java 21 virtual threads are cheap enough to create one per task. Pooling them defeats their purpose. What still needs limiting is the scarce resource behind them (database connections, a partner's rate limit), which you protect with a semaphore or the resource's own pool.

**Advantages / features.** Simple thread-per-request code with high concurrency; resource limits enforced where they matter.

**VirtualThreadsPoolingDemo.java**

```java
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** With virtual threads, don't pool THREADS (they're cheap); limit access to the scarce RESOURCE instead. */
public class VirtualThreadsPoolingDemo {

    static final Semaphore DB_CONNECTIONS = new Semaphore(10);
    static final AtomicInteger inUse = new AtomicInteger(), maxSeen = new AtomicInteger();

    static int fetchStock(int i) throws InterruptedException {
        DB_CONNECTIONS.acquire();
        try {
            int now = inUse.incrementAndGet();
            maxSeen.accumulateAndGet(now, Math::max);
            Thread.sleep(5);                                                               // simulated query
            return i % 7;
        } finally {
            inUse.decrementAndGet();
            DB_CONNECTIONS.release();
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger total = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 2_000; i++) {
                int n = i;
                executor.submit(() -> { total.addAndGet(fetchStock(n)); return null; });
            }
        }                                                                                  // waits for all tasks
        System.out.println("2000 requests on 2000 virtual threads, total stock " + total.get());
        System.out.println("max concurrent DB calls within the limit of 10: " + (maxSeen.get() <= 10));
    }
}
```

1. The scarce resource is limited, not the threads.
2. Each task waits for a permit only around the database call.
3. One virtual thread per task; closing the executor waits for all of them.

**Run it:** `java VirtualThreadsPoolingDemo.java`

```text
2000 requests on 2000 virtual threads, total stock 5995
max concurrent DB calls within the limit of 10: true
```

| | Platform threads | Virtual threads |
|---|---|---|
| Creation cost | High (OS thread, MB stack) | Low (heap object) |
| Pool them? | Yes | No |
| Limit concurrency with | Pool size | Semaphore or resource pool |
| Blocking I/O | Ties up an OS thread | Unmounts from the carrier thread |

**Use** `Executors.newVirtualThreadPerTaskExecutor()` for I/O-bound work and keep connection pools. **Avoid** pooling virtual threads or using them for CPU-bound work.
**Real-world Java:** Spring Boot 3.2+ (`spring.threads.virtual.enabled=true`), Helidon Níma, Tomcat with virtual threads.
**Best practice:** keep connection pool sizes based on the database's capacity, not on thread counts. **Gotcha:** on Java 21, blocking inside `synchronized` can pin the carrier thread; prefer `ReentrantLock` in hot paths (improved in later JDKs).

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** A store has two label printers shared by packers.

**LabelPrinterPoolDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** Minimal Object Pool: a store has 2 label printers; packers borrow one, print, and always give it back. */
public class LabelPrinterPoolDemo {

    record Printer(String id) { String print(String orderId) { return id + " printed label for " + orderId; } }

    static final class PrinterPool {
        private final BlockingQueue<Printer> free = new ArrayBlockingQueue<>(2, true);
        PrinterPool() { free.add(new Printer("printer-A")); free.add(new Printer("printer-B")); }

        /** A lease returns the printer when closed, so try-with-resources can't forget. */
        final class Lease implements AutoCloseable {
            final Printer printer;
            Lease(Printer p) { printer = p; }
            @Override public void close() { free.add(printer); }
        }

        Lease borrow() throws InterruptedException { return new Lease(free.take()); }
    }

    public static void main(String[] args) throws InterruptedException {
        PrinterPool pool = new PrinterPool();
        List<String> out = new ArrayList<>();
        for (String order : List.of("FC-2601", "FC-2602", "FC-2603")) {
            try (PrinterPool.Lease lease = pool.borrow()) {
                out.add(lease.printer.print(order));
            }
        }
        out.forEach(System.out::println);
    }
}
```

1. A lease returns the printer when closed.
2. `take()` waits until a printer is free.

**Run it:** `java LabelPrinterPoolDemo.java`

**Output:**

```text
printer-A printed label for FC-2601
printer-B printed label for FC-2602
printer-A printed label for FC-2603
```

**What to notice:**

- Three orders, two printers; printer A is reused.
- try-with-resources makes "always give it back" automatic.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** At month end, FreshCart renders thousands of invoice PDFs. A renderer takes noticeable time to start (fonts, templates), so a few are pooled and shared by many worker threads.

**InvoiceRendererPoolDemo.java**

```java
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Realistic Object Pool: invoice PDF renderers are expensive to start (fonts, templates); pool a few, share them. */
public class InvoiceRendererPoolDemo {

    static final AtomicInteger STARTED = new AtomicInteger();

    static final class PdfRenderer {
        PdfRenderer() { STARTED.incrementAndGet(); busyWork(); }
        String render(String orderId) { return "%PDF invoice " + orderId; }
        static void busyWork() { long x = 0; for (int i = 0; i < 2_000_000; i++) x += i; if (x == 42) System.out.print(""); }
    }

    static final class RendererPool {
        private final BlockingQueue<PdfRenderer> idle;
        RendererPool(int size) {
            idle = new ArrayBlockingQueue<>(size);
            for (int i = 0; i < size; i++) idle.add(new PdfRenderer());
        }
        String render(String orderId) throws InterruptedException {
            PdfRenderer r = idle.take();
            try { return r.render(orderId); } finally { idle.add(r); }
        }
    }

    public static void main(String[] args) throws Exception {
        RendererPool pool = new RendererPool(3);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        List<Future<String>> invoices = new java.util.ArrayList<>();
        for (int i = 1; i <= 200; i++) { String id = "FC-" + (2700 + i); invoices.add(workers.submit(() -> pool.render(id))); }
        int done = 0; for (Future<String> f : invoices) if (f.get().startsWith("%PDF")) done++;
        workers.shutdown();
        System.out.println("invoices rendered: " + done + " by 8 worker threads");
        System.out.println("renderers started: " + STARTED.get() + " (without a pool it would be 200)");
    }
}
```

1. Starting a renderer is the expensive part (simulated with busy work).
2. Three renderers are created once, up front.
3. Each render borrows and returns in `finally`.

**Run it:** `java InvoiceRendererPoolDemo.java`

**Output:**

```text
invoices rendered: 200 by 8 worker threads
renderers started: 3 (without a pool it would be 200)
```

**What to notice:**

- Eight threads shared three renderers; 200 invoices cost three start-ups.
- The pool also limits memory: at most three renderers exist.

### Example 3: Production-grade

**Scenario:** Packing stations use a heavyweight print-service client. Requirements: at most two clients; borrowers wait at most a few milliseconds and get a helpful error naming who holds the clients; broken clients are discarded; clients older than 30 minutes are retired; leases can't be used after return or returned twice; leases held over 60 seconds are reported as suspected leaks; and everything is counted.

**ProductionPoolDemo.java**

```java
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Production-grade Object Pool for expensive clients (e.g. a printer-service or legacy SOAP client):
 * max size with fair waiting and a borrow timeout; validation on borrow; max lifetime eviction;
 * leases that can't be returned twice; leak detection for leases held too long; metrics.
 */
public class ProductionPoolDemo {

    static final class Pool<T> {
        private record Entry<T>(T obj, Instant createdAt) { }

        private final Deque<Entry<T>> idle = new ArrayDeque<>();
        private final List<Lease> active = new ArrayList<>();
        private final Semaphore permits;
        private final Supplier<T> factory; private final Predicate<T> healthy;
        private final Duration maxLifetime, leakThreshold; private final Supplier<Instant> clock;
        final Map<String, Integer> metrics = new TreeMap<>();

        Pool(int maxSize, Supplier<T> factory, Predicate<T> healthy, Duration maxLifetime, Duration leakThreshold, Supplier<Instant> clock) {
            this.permits = new Semaphore(maxSize, true); this.factory = factory; this.healthy = healthy;
            this.maxLifetime = maxLifetime; this.leakThreshold = leakThreshold; this.clock = clock;
        }

        final class Lease implements AutoCloseable {
            private final Entry<T> entry; private final Instant borrowedAt; private final String owner; private boolean closed;
            private Lease(Entry<T> e, String owner) { entry = e; borrowedAt = clock.get(); this.owner = owner; }
            T get() { if (closed) throw new IllegalStateException("lease already returned"); return entry.obj(); }
            @Override public void close() {
                synchronized (Pool.this) {
                    if (closed) return;
                    closed = true;
                    active.remove(this);
                    idle.push(entry);
                }
                permits.release();
                count("returned");
            }
        }

        Lease borrow(String owner, Duration timeout) throws InterruptedException {
            if (!permits.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                count("timeouts");
                throw new IllegalStateException("no object available within " + timeout.toMillis() + "ms (active: " + activeOwners() + ")");
            }
            synchronized (this) {
                while (!idle.isEmpty()) {
                    Entry<T> e = idle.pop();
                    if (clock.get().isAfter(e.createdAt().plus(maxLifetime))) { count("evicted.lifetime"); continue; }
                    if (!healthy.test(e.obj())) { count("evicted.unhealthy"); continue; }
                    return track(new Lease(e, owner));
                }
                count("created");
                return track(new Lease(new Entry<>(factory.get(), clock.get()), owner));
            }
        }

        synchronized List<String> suspectedLeaks() {
            Instant now = clock.get();
            return active.stream().filter(l -> Duration.between(l.borrowedAt, now).compareTo(leakThreshold) > 0)
                    .map(l -> l.owner + " held " + Duration.between(l.borrowedAt, now).toSeconds() + "s").toList();
        }

        private Lease track(Lease l) { active.add(l); count("borrowed"); return l; }
        private synchronized List<String> activeOwners() { return active.stream().map(l -> l.owner).toList(); }
        private synchronized void count(String k) { metrics.merge(k, 1, Integer::sum); }
    }

    static final class PrintClient { boolean healthy = true; final int id; PrintClient(int id) { this.id = id; } }

    public static void main(String[] args) throws Exception {
        Instant[] now = {Instant.parse("2026-11-02T09:00:00Z")};
        int[] ids = {0};
        Pool<PrintClient> pool = new Pool<>(2, () -> new PrintClient(++ids[0]), c -> c.healthy,
                Duration.ofMinutes(30), Duration.ofSeconds(60), () -> now[0]);

        try (var lease = pool.borrow("packing-station-1", Duration.ofMillis(10))) { System.out.println("station 1 uses client " + lease.get().id); }

        var forgotten = pool.borrow("returns-desk", Duration.ofMillis(10));               // never closed: a leak
        var second = pool.borrow("packing-station-2", Duration.ofMillis(10));
        try { pool.borrow("packing-station-3", Duration.ofMillis(20)); } catch (IllegalStateException e) { System.out.println("station 3: " + e.getMessage()); }

        now[0] = now[0].plusSeconds(90);
        System.out.println("suspected leaks: " + pool.suspectedLeaks());

        second.get().healthy = false;
        second.close(); second.close();                                                   // double close is harmless
        try { second.get(); } catch (IllegalStateException e) { System.out.println("use after return: " + e.getMessage()); }

        try (var lease = pool.borrow("packing-station-4", Duration.ofMillis(10))) { System.out.println("station 4 uses client " + lease.get().id + " (unhealthy client 2 was discarded)"); }

        forgotten.close();
        now[0] = now[0].plus(Duration.ofMinutes(31));                                     // every idle client is now past max lifetime
        try (var lease = pool.borrow("packing-station-5", Duration.ofMillis(10))) { System.out.println("station 5 uses client " + lease.get().id + " (old clients retired)"); }
        System.out.println("metrics: " + pool.metrics);
    }
}
```

1. A returned lease can't be used again.
2. Closing twice is harmless (common with nested `finally` blocks).
3. The timeout error lists the current holders, which is gold during incidents.
4. Max-lifetime eviction (like HikariCP's `maxLifetime`), using an injected clock.
5. Leak detection: leases held longer than the threshold.

**Run it:** `java ProductionPoolDemo.java`

**Output:**

```text
station 1 uses client 1
station 3: no object available within 20ms (active: [returns-desk, packing-station-2])
suspected leaks: [returns-desk held 90s, packing-station-2 held 90s]
use after return: lease already returned
station 4 uses client 3 (unhealthy client 2 was discarded)
station 5 uses client 4 (old clients retired)
metrics: {borrowed=5, created=4, evicted.lifetime=2, evicted.unhealthy=1, returned=5, timeouts=1}
```

**What to notice:**

- Station 3's error names the returns desk, which never returned its client.
- The unhealthy client was discarded on the next borrow; old clients were retired after 30 minutes.
- Five borrows needed only four creations, and the metrics show why each eviction happened.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Performance | Avoids expensive creation | No gain (or a loss) for cheap objects |
| Scalability | Caps load on scarce resources | Pool size becomes a bottleneck if too small |
| Reliability | Validation and eviction remove bad objects | Leaks exhaust the pool and stall the service |
| Simplicity | Borrow/return API is simple | Correct pool implementation is hard |
| Operability | Metrics show saturation clearly | Many settings to tune per environment |
| Security | — | State leaking between users if reset is wrong |

## 8. When to Use / When NOT to Use

**Use it when:**

- objects hold scarce resources (connections, sockets, native handles, licences);
- creation cost is high and measured;
- you must cap concurrent use of a dependency.

**Don't use it when:**

- objects are cheap (allocation is faster than pool bookkeeping);
- you're using virtual threads (limit resources, not threads);
- a library already pools for you (HTTP clients, JDBC via HikariCP).

## 9. Best Practices

1. **Use a proven pool** (HikariCP, Commons Pool, `ThreadPoolExecutor`) instead of writing one. *(Consensus.)*
2. **Borrow with a timeout; return in `finally` or via try-with-resources.** *(Consensus.)*
3. **Validate on borrow, reset on return, retire by age.** *(Consensus.)*
4. **Turn on leak detection** in non-production and with a high threshold in production. *(Consensus.)*
5. **Size pools from downstream capacity** (database connections across all instances). *(Consensus.)*
6. **Bound queues and choose a rejection policy** for thread pools. *(Consensus.)*
7. **Don't pool cheap objects or virtual threads.** *(Consensus on modern JVMs.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Connection held during a remote call (production failure mode)**  
> **Symptom:** during a payment provider slowdown, the whole app timed out, even pages that don't use payments.
> **Root cause:** checkout opened a transaction, then called the payment API inside it, holding a connection for seconds; the pool ran dry.
> **Fix:** call remote APIs outside transactions; keep connection hold times in milliseconds.

> 🚨 **Pool leak**  
> **Symptom:** `Connection is not available, request timed out after 30000ms` hours after a deploy.
> **Root cause:** an error path returned early without closing the connection.
> **Fix:** try-with-resources everywhere; `leakDetectionThreshold` to find the culprit.

> 🚨 **Oversized pools**  
> **Symptom:** raising the pool size from 20 to 200 made the database slower.
> **Root cause:** more concurrent queries than the database has CPU cores and disks to serve.
> **Fix:** smaller pools; HikariCP's guidance starts from the database's core count.

> 🚨 **Pooling for the GC**  
> **Symptom:** a custom object pool, data leaking between requests, no measurable speed-up.
> **Root cause:** pooling cheap objects based on outdated advice.
> **Fix:** delete the pool; allocate normally; profile first.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Max pool size | 10 (HikariCP) | Concurrent connections per instance | Measured saturation and DB headroom | Too big: DB overload; too small: waits |
| Borrow timeout | 30 s (HikariCP) | How long a request waits | Lower for user-facing paths (e.g. 2–5 s) | Too low: errors under brief spikes |
| Max lifetime | 30 min | Retire before infrastructure does | Shorter than DB/firewall idle limits | Too short: constant churn |
| Leak threshold | off | Report long-held objects | On in staging; high value in prod | Too low: noisy false alarms |
| Thread pool queue | unbounded (`newFixedThreadPool`) | Waiting tasks | Always bound in servers | Rejections must be handled |

## 12. Security Considerations

- Reset pooled objects completely: session variables, user context, buffers. Leaking one customer's data to the next request is a security incident.
- Database connections should not carry per-user privileges that persist across borrowers; reset roles or use separate pools.
- Pools are a denial-of-service target: per-client rate limits stop one caller from monopolising the pool.

## 13. Performance & Scalability

Pools trade memory and bookkeeping for creation cost and a concurrency cap. The key metrics are **wait time** to borrow, **utilisation**, and **hold time**. Little's Law helps: needed pool size ≈ request rate × average hold time (100 req/s × 20 ms = 2 connections on average). If hold time grows (slow queries, remote calls in transactions), a bigger pool rarely helps; fix the hold time.

## 14. Testing It

Test reuse, exhaustion, retirement and leak detection with an injected clock:

**ProductionPoolTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionPoolTest {

    private final Instant[] now = {Instant.parse("2026-11-02T09:00:00Z")};
    private final ProductionPoolDemo.Pool<ProductionPoolDemo.PrintClient> pool = new ProductionPoolDemo.Pool<>(
            1, () -> new ProductionPoolDemo.PrintClient(1), c -> c.healthy, Duration.ofMinutes(30), Duration.ofSeconds(60), () -> now[0]);

    @Test
    void reusesReturnedObjects() throws Exception {
        ProductionPoolDemo.PrintClient first;
        try (var l = pool.borrow("a", Duration.ofMillis(10))) { first = l.get(); }
        try (var l = pool.borrow("b", Duration.ofMillis(10))) { assertSame(first, l.get()); }
        assertEquals(1, pool.metrics.get("created"));
    }

    @Test
    void timesOutWhenExhausted() throws Exception {
        var held = pool.borrow("a", Duration.ofMillis(10));
        assertThrows(IllegalStateException.class, () -> pool.borrow("b", Duration.ofMillis(20)));
        held.close();
    }

    @Test
    void retiresObjectsPastMaxLifetime() throws Exception {
        ProductionPoolDemo.PrintClient first;
        try (var l = pool.borrow("a", Duration.ofMillis(10))) { first = l.get(); }
        now[0] = now[0].plus(Duration.ofMinutes(31));
        try (var l = pool.borrow("b", Duration.ofMillis(10))) { assertNotSame(first, l.get()); }
    }

    @Test
    void reportsLeasesHeldTooLongAndIgnoresDoubleClose() throws Exception {
        var l = pool.borrow("returns-desk", Duration.ofMillis(10));
        now[0] = now[0].plusSeconds(61);
        assertEquals(List.of("returns-desk held 61s"), pool.suspectedLeaks());
        l.close(); l.close();
        assertTrue(pool.suspectedLeaks().isEmpty());
        assertEquals(1, pool.metrics.get("returned"));
    }
}
```

## 15. Observability & Operations

Export pool gauges (active, idle, pending waiters, max) and timers (borrow wait, hold time). HikariCP publishes these to Micrometer automatically in Spring Boot. Alert when pending waiters stay above zero or borrow wait rises; both precede timeouts. Log leak warnings with the stack trace of the borrower.

## 16. Version Notes

| Java | Change relevant to Object Pool |
|------|--------------------------------|
| 5 | `java.util.concurrent`: `ThreadPoolExecutor`, `Semaphore`, `BlockingQueue` |
| 7 | try-with-resources: leases closed automatically |
| 19–21 | Virtual threads (final in 21): don't pool threads for I/O-bound work |
| 21 | `Executors.newVirtualThreadPerTaskExecutor()` |
| 24 | JEP 491: `synchronized` no longer pins virtual threads in most cases |

## 17. Expert Deep Dive

- **Pool sizing is a system property, not a service setting.** Total connections = pool size × instances × services; autoscaling multiplies it. Seniors size pools against the database's capacity and use connection proxies (PgBouncer, RDS Proxy) when instance counts vary.
- **Hold time is the real lever.** Short transactions, no remote calls inside them, and fast queries let a small pool serve high traffic.
- **Bulkheads.** Separate pools for separate dependencies (and for critical vs batch work) stop one slow path from taking everything; see [Resilience Patterns](../l4-senior/resilience-patterns.md).
- **Virtual threads change the shape, not the limits.** With virtual threads, many more requests reach the pool at once; without a limit they wait in the pool queue, so set `connectionTimeout` and shed load deliberately.

> 📖 **War story: the pool that grew the outage**  
> During a sale, a grocery platform's order service started timing out on connections. The on-call engineer raised `maximumPoolSize` from 20 to 100 across 40 instances: up to 4,000 connections to a database tuned for about 1,000. The database spent its time switching between sessions, every query slowed, and the outage spread to other services. Rolling back to 20, killing a slow report query that had been holding connections, and moving the report to a replica fixed it. **Lesson:** an exhausted pool is a symptom; find what's holding connections before making the pool bigger.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is an object pool?** A set of **reusable expensive objects** lent out and returned.
2. **Give two Java examples.** **HikariCP** (connections) and **`ThreadPoolExecutor`** (threads).
3. **Why return in `finally`?** So objects are **returned even when exceptions happen**; otherwise the pool leaks.
4. **What should happen when the pool is empty?** **Wait up to a timeout**, then fail clearly.
5. **Should you pool `StringBuilder`s?** **No**; allocation is cheap and pooling adds bugs.

**▸ 🟡 L3**

1. **What is `maxLifetime` for?** Retire connections **before the database or firewall closes them**.
2. **How do you find a connection leak?** **`leakDetectionThreshold`** stack traces, pool metrics, hold-time timers.
3. **Why bounded queues in thread pools?** **Back-pressure** and bounded memory under overload.
4. **How do virtual threads change pooling?** **Don't pool threads**; limit the **resource** with semaphores or its pool.
5. **How big should a connection pool be?** Based on **DB capacity and hold time**, often small (tens), across all instances.

**▸ 🔴 L4–L5**

1. **The pool is exhausted in production. Walk through your response.** Check **hold times and slow queries**, find leaks, shed load; **don't just raise the size**.
2. **Design connection management for an autoscaling service.** **Per-instance pools sized from a global budget**, a connection proxy, timeouts, bulkheads.
3. **How do you protect a partner API with a rate limit across instances?** **Distributed rate limiter or token bucket service**, local semaphores as a first line.
4. **When would you write a custom pool?** For **native or licensed resources** with no library support, with leases, validation, eviction and metrics.
5. **Now 10x traffic with virtual threads; the DB is the bottleneck. What changes?** **Caching, read replicas, query optimisation, admission control**; not more connections.

**▸ 💻 Hands-on**

**Task:** Make `ConnectionPoolDemo`'s pool report average hold time.

> ✅ **Solution**  
> ```java
> private final Map<Connection, Long> borrowedAt = new ConcurrentHashMap<>();
> private final LongAdder totalHoldNanos = new LongAdder(), returns = new LongAdder();
>
> Connection getConnection(long timeoutMs) throws InterruptedException {
>     Connection c = free.poll(timeoutMs, TimeUnit.MILLISECONDS);
>     if (c == null) throw new IllegalStateException("timed out");
>     borrowedAt.put(c, System.nanoTime());
>     return c;
> }
> void close(Connection c) {
>     totalHoldNanos.add(System.nanoTime() - borrowedAt.remove(c));
>     returns.increment();
>     free.add(c);
> }
> double averageHoldMillis() { return returns.sum() == 0 ? 0 : totalHoldNanos.sum() / 1e6 / returns.sum(); }
> ```

## 19. Practice

- [ ] Add a `CallerRunsPolicy` to `ThreadPoolDemo` and observe which thread runs rejected tasks.
    > 💡 **Hint**  
    > Print `Thread.currentThread().getName()` inside the task.

- [ ] Add an idle timeout to `ProductionPoolDemo` that closes clients idle for more than 10 minutes.
    > 💡 **Hint**  
    > Store `lastReturnedAt` in the entry and check it on borrow.

- [ ] Compute a pool size for 300 requests/s with an average hold time of 15 ms, and add headroom.
    > 💡 **Hint**  
    > Little's Law: 300 × 0.015 = 4.5 on average; add headroom for bursts.

- [ ] Find a place in a codebase where a remote call happens inside a database transaction.
    > 💡 **Hint**  
    > Look for HTTP client calls inside `@Transactional` methods.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: reuse expensive objects and cap concurrent use.
> - Lifecycle: create lazily → validate on borrow → reset on return → retire by age or health.
> - Always: borrow with timeout, return in `finally`/lease, leak detection, metrics.
> - Connection pools: small, sized from DB capacity; short hold times; no remote calls in transactions.
> - Don't pool cheap objects or virtual threads; limit the resource with a semaphore.
>
> ```java
> try (Connection c = dataSource.getConnection();
>      PreparedStatement ps = c.prepareStatement("SELECT qty FROM stock WHERE sku = ?")) {
>     ps.setString(1, sku);
>     // ...
> }   // connection back in the pool
> ```

## 21. Further Reading

- [HikariCP: About Pool Sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- Brian Goetz et al., *Java Concurrency in Practice* (2006), chapter 8 "Applying Thread Pools".
- [java.util.concurrent.ThreadPoolExecutor API documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

---

[← Previous: Prototype](prototype.md) · [Next: Dependency Injection →](dependency-injection.md)
