# Caching Patterns

L4 🟠 Senior / Lead · ⏱ 30 min read · Prerequisites: [Proxy](../l2-practitioner/proxy.md), [Distributed Data Patterns](distributed-data-patterns.md)

> 📌 **TL;DR**  
> A cache trades freshness and memory for speed and load reduction. **Cache-aside** (the app loads on a miss and deletes on write) is the default. **Read-through / write-through** put the cache in front of the store so callers only talk to the cache. **Write-behind** acknowledges writes from the cache and flushes later: fast, but unflushed data can be lost. **Invalidation** is the hard part: combine TTLs (bounded staleness), explicit deletes on write, broadcast invalidation for local caches and versioned keys. Protect hot keys from **stampedes** with single-flight loading and TTL jitter. In Java, **Caffeine** is the in-process cache and **Redis** the usual shared cache; many systems use both as L1 and L2.

## 1. What It Is

**Plain English.** FreshCart's app asks for the same product prices and menus thousands of times a minute, and they change rarely. Keeping copies close to where they're needed (in memory, or in a fast shared store) answers most requests without touching the database. These patterns decide who fills the copy, when it's updated, and how to stop serving it once it's wrong.

**Definition.**

| Pattern | Who loads on a miss | Who writes the store |
|---------|---------------------|----------------------|
| Cache-aside | Application | Application (then invalidates cache) |
| Read-through | Cache (via loader) | — |
| Write-through | — | Cache, synchronously |
| Write-behind | — | Cache, asynchronously in batches |
| Refresh-ahead | Cache, before expiry | — |

## 2. Why It Exists

Databases and remote services are orders of magnitude slower than memory and have limited capacity. Read-heavy workloads (catalogs, configs, sessions, prices) repeat the same queries; caching them cuts latency from milliseconds to microseconds and protects the database during spikes. The cost is consistency: every cache is a second copy of data that can go stale. As Phil Karlton put it, cache invalidation is one of the two hard things in computer science.

## 3. Mental Model / Analogy

**Analogy.** A shop counter. The shopkeeper keeps the most-asked items on the counter (cache) instead of walking to the storeroom (database). If an item isn't on the counter, they fetch it and leave one there (cache-aside). Price stickers have a "check again after" date (TTL). When the supplier changes a price, the shopkeeper removes the old sticker (invalidation). If 50 customers ask for the same missing item at once, one person goes to the storeroom and the rest wait (single-flight).

**Technical model.** A key-value map with a bounded size, an eviction policy (LRU, LFU, W-TinyLFU), expiry (TTL, TTI) and a loading strategy, either in-process (per instance) or shared over the network.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Cache-aside

**Description.** The application owns the logic: read the cache; on a miss, load from the store and put the value in the cache; on a write, update the store and **delete** the cache entry (rather than updating it, which races with concurrent writers). Only requested data is cached.

**Advantages / features.** Simple; works with any cache and store; the cache can fail without breaking reads (fall back to the store); memory holds only what's used.

**CacheAsideDemo.java**

```java
import java.util.HashMap;
import java.util.Map;

/**
 * Cache-aside (lazy loading): the application checks the cache, loads from the database on a miss and stores the
 * result. On writes it updates the database and deletes the cached entry, so the next read reloads it.
 */
public class CacheAsideDemo {

    static final Map<String, Integer> database = new HashMap<>(Map.of("MILK-1L", 68, "CURD-500G", 45));
    static final Map<String, Integer> cache = new HashMap<>();
    static int dbReads;

    static int price(String sku) {
        Integer cached = cache.get(sku);
        if (cached != null) return cached;
        dbReads++;
        int fromDb = database.get(sku);
        cache.put(sku, fromDb);
        return fromDb;
    }

    static void updatePrice(String sku, int newPrice) {
        database.put(sku, newPrice);
        cache.remove(sku);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) price("MILK-1L");
        System.out.println("5 reads of MILK-1L -> " + dbReads + " database read");
        updatePrice("MILK-1L", 70);
        System.out.println("after update: Rs " + price("MILK-1L") + ", database reads " + dbReads);
        System.out.println("CURD-500G not cached until asked: " + cache.containsKey("CURD-500G"));
    }
}
```

1. Check the cache first.
2. On a miss, load from the database and remember it.
3. On a write, update the database and delete the cached copy.

**Run it:** `java CacheAsideDemo.java`

```text
5 reads of MILK-1L -> 1 database read
after update: Rs 70, database reads 2
CURD-500G not cached until asked: false
```

| Write strategy in cache-aside | Risk |
|-------------------------------|------|
| Update DB, then delete cache | Small race window (see invalidation); recommended |
| Update DB, then set cache | Concurrent writers can leave the older value |
| Delete cache, then update DB | Reader can reload the old value in between |
| Set cache only | Lost data if cache evicts |

**Use** as the default for read-heavy data. **Avoid** forgetting the delete in any write path (admin tools, batch jobs, SQL fixes).
**Real-world Java:** Spring `@Cacheable` / `@CacheEvict`, `RedisTemplate` or Lettuce, Caffeine `Cache.get(key, loader)`.
**Best practice:** always set a TTL as a safety net for missed invalidations. **Gotcha:** the first request after a deploy or flush pays the full load; warm hot keys if needed.

### Read-through and write-through

**Description.** The cache is configured with a loader (read-through) and a writer (write-through), and callers use only the cache API. On a miss, the cache calls the loader. On a write, the cache writes the store synchronously and then updates itself, so the cache is never older than the store for writes that go through it.

**Advantages / features.** Simpler callers; loading logic in one place; consistent cache after writes; natural fit for in-process caches and data grids.

**ReadWriteThroughDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Read-through and write-through: the cache sits in front of the store. Callers only talk to the cache; it loads
 * missing entries itself and writes changes through to the store before acknowledging.
 */
public class ReadWriteThroughDemo {

    interface Store { String load(String key); void save(String key, String value); }

    static final class ThroughCache {
        private final Map<String, String> entries = new HashMap<>();
        private final Store store;
        ThroughCache(Store store) { this.store = store; }
        String get(String key) { return entries.computeIfAbsent(key, store::load); }
        void put(String key, String value) {
            store.save(key, value);
            entries.put(key, value);
        }
    }

    public static void main(String[] args) {
        Map<String, String> table = new HashMap<>(Map.of("C-1", "Koramangala, BLR"));
        List<String> storeCalls = new ArrayList<>();
        Store addresses = new Store() {
            public String load(String k) { storeCalls.add("load " + k); return table.get(k); }
            public void save(String k, String v) { storeCalls.add("save " + k); table.put(k, v); }
        };
        ThroughCache cache = new ThroughCache(addresses);
        System.out.println(cache.get("C-1"));
        System.out.println(cache.get("C-1"));
        cache.put("C-1", "Indiranagar, BLR");
        System.out.println(cache.get("C-1"));
        System.out.println("store calls: " + storeCalls);
    }
}
```

1. Read-through: the cache loads missing entries itself.
2. Write-through: the store is written first...
3. ...then the cache, so the next read is a fresh hit.

**Run it:** `java ReadWriteThroughDemo.java`

```text
Koramangala, BLR
Koramangala, BLR
Indiranagar, BLR
store calls: [load C-1, save C-1]
```

| | Cache-aside | Read/write-through |
|-|-------------|--------------------|
| Who knows the store | Application | Cache |
| Write latency | Store write + delete | Store write + cache write |
| Cache outage | Reads fall back to store | Reads fail unless handled |
| Cached data | Requested only | Requested + written |

**Use** when one component should own loading and writing (data grids, repositories with built-in caching). **Avoid** write-through for data that's written often but rarely read (it fills the cache with cold entries).
**Real-world Java:** Caffeine `LoadingCache`, JCache (JSR 107) `CacheLoader`/`CacheWriter`, Hazelcast `MapStore`, Infinispan.
**Best practice:** keep loaders idempotent and fast; set timeouts on them. **Gotcha:** writes that bypass the cache (other services, SQL scripts) still make it stale.

### Write-behind

**Description.** Writes update the cache and return immediately; the cache flushes dirty entries to the store later, in batches, coalescing repeated writes to the same key. The store is eventually consistent with the cache.

**Advantages / features.** Very fast writes; far fewer store writes for hot counters; smooths write spikes.

**WriteBehindDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Write-behind (write-back): writes go to the cache and are acknowledged immediately; the store is updated later in
 * batches. Fast and coalescing, but anything not yet flushed is lost if the cache node dies.
 */
public class WriteBehindDemo {

    static final class WriteBehindCache {
        final Map<String, Integer> entries = new LinkedHashMap<>();
        private final Map<String, Integer> dirty = new LinkedHashMap<>();
        final List<String> storeWrites = new ArrayList<>();
        void put(String key, int value) { entries.put(key, value); dirty.put(key, value); }
        void flush() {
            if (dirty.isEmpty()) return;
            storeWrites.add("batch " + dirty);
            dirty.clear();
        }
        int unflushed() { return dirty.size(); }
    }

    public static void main(String[] args) {
        WriteBehindCache viewCounts = new WriteBehindCache();
        for (int i = 1; i <= 100; i++) viewCounts.put("views:MILK-1L", i);
        for (int i = 1; i <= 40; i++) viewCounts.put("views:CURD-500G", i);
        viewCounts.flush();
        System.out.println("140 writes became " + viewCounts.storeWrites.size() + " store write: " + viewCounts.storeWrites);
        viewCounts.put("views:MILK-1L", 101);
        viewCounts.put("views:EGGS-12", 1);
        System.out.println("node crashes with " + viewCounts.unflushed() + " unflushed entries: those updates are lost");
    }
}
```

1. Dirty entries are tracked separately; the latest value per key wins.
2. A flush writes all dirty entries in one batch.
3. Anything not yet flushed is lost if the node crashes.

**Run it:** `java WriteBehindDemo.java`

```text
140 writes became 1 store write: [batch {views:MILK-1L=100, views:CURD-500G=40}]
node crashes with 2 unflushed entries: those updates are lost
```

| Data | Write-behind? |
|------|---------------|
| View counts, analytics counters | Yes |
| Rate-limit counters, session activity | Yes |
| Orders, payments, stock | No |
| Anything with legal or financial meaning | No |

**Use** for high-volume, loss-tolerant data. **Avoid** for anything the business can't afford to lose.
**Real-world Java:** Hazelcast `MapStore` with `write-delay-seconds`, Redis counters flushed by a scheduled job, Ehcache write-behind.
**Best practice:** bound the flush interval and dirty-queue size; flush on graceful shutdown. **Gotcha:** the store doesn't see recent writes, so reports and other services read old values.

### Cache invalidation strategies

**Description.** Ways to stop serving stale data: **TTL** (expire after a time; bounded staleness), **explicit invalidation** (delete on write), **event-driven invalidation** (broadcast deletes to all instances, or CDC-driven), and **versioned keys** (change the key so old entries are never read). Combine them: delete on write for freshness, TTL as a safety net.

**Advantages / features.** Each strategy bounds a different failure: TTL bounds missed deletes, deletes give freshness, versions avoid races and schema mismatches after deploys.

**CacheInvalidationDemo.java**

```java
import java.util.HashMap;
import java.util.Map;

/**
 * Cache invalidation: TTLs bound staleness, explicit deletes react to writes, versioned keys make old entries
 * unreachable. The classic race: a slow reader puts a stale value back after a writer's delete.
 */
public class CacheInvalidationDemo {

    record Entry(int value, long expiresAt) {}
    static final Map<String, Integer> db = new HashMap<>(Map.of("MILK-1L", 68));
    static final Map<String, Entry> cache = new HashMap<>();

    public static void main(String[] args) {
        // The race, step by step (two app instances, one cache).
        int readerSaw = db.get("MILK-1L");                     // reader: cache miss, reads 68 from the DB ... then pauses (GC)
        db.put("MILK-1L", 70); cache.remove("MILK-1L");         // writer: updates DB to 70, deletes the cache key
        cache.put("MILK-1L", new Entry(readerSaw, Long.MAX_VALUE));
        System.out.println("delete-on-write, no TTL: cache=" + cache.get("MILK-1L").value() + " db=" + db.get("MILK-1L") + " (stale until next write)");

        // Same race with a TTL: staleness is bounded.
        cache.put("MILK-1L", new Entry(68, 300));
        long now = 301;
        Entry e = cache.get("MILK-1L");
        System.out.println("with 300 s TTL at t=301: " + (e.expiresAt() <= now ? "expired, reload " + db.get("MILK-1L") : "still " + e.value()));

        // Versioned keys: readers put under the version they read; the writer bumps the version.
        Map<String, Integer> version = new HashMap<>(Map.of("MILK-1L", 7));
        int v = version.get("MILK-1L");                       // reader reads version 7 and price 68 ... pauses
        version.put("MILK-1L", 8); db.put("MILK-1L", 72);       // writer: new price, version 8
        cache.put("MILK-1L:v" + v, new Entry(68, Long.MAX_VALUE));
        String current = "MILK-1L:v" + version.get("MILK-1L");
        System.out.println("versioned keys: stale entry parked under MILK-1L:v7; readers look up " + current + " -> "
                + (cache.containsKey(current) ? "hit" : "miss, load " + db.get("MILK-1L")));
    }
}
```

1. The slow reader puts the value it read before the update back into the cache, after the writer's delete.
2. With a TTL, that stale value disappears on its own.
3. With versioned keys, the stale value lands under an old key nobody reads.

**Run it:** `java CacheInvalidationDemo.java`

```text
delete-on-write, no TTL: cache=68 db=70 (stale until next write)
with 300 s TTL at t=301: expired, reload 70
versioned keys: stale entry parked under MILK-1L:v7; readers look up MILK-1L:v8 -> miss, load 72
```

| Strategy | Staleness | Cost |
|----------|-----------|------|
| TTL only | Up to TTL | None; simplest |
| Delete on write | Small race window | Every write path must do it |
| Broadcast to L1 caches | Milliseconds | Pub/sub channel; missed messages need TTL |
| CDC-driven | Seconds | Infrastructure; catches all writers |
| Versioned keys | None for reads via version | Version lookup; old entries expire via TTL |
| Delayed double delete | Smaller race window | Extra delete after a delay |

**Use** delete-on-write plus TTL by default; add broadcast for local caches. **Avoid** caches without any TTL.
**Real-world Java:** Redis pub/sub or keyspace notifications, Debezium to invalidate caches, Spring `@CacheEvict`.
**Best practice:** document the acceptable staleness for each cached item with the business. **Gotcha:** caching inside a database transaction that later rolls back caches data that never existed.

### Caffeine and Redis

**Description.** **Caffeine** is a high-performance in-process cache for Java (W-TinyLFU eviction, size and weight bounds, expiry, refresh-ahead, async loading, statistics). **Redis** is a shared, networked in-memory store used as a distributed cache (strings, hashes, TTLs, pub/sub). Caffeine is microseconds and per instance; Redis is about a millisecond and shared. Combined, Caffeine is L1 and Redis is L2.

**Advantages / features.** Caffeine: near-optimal hit rates, no network, refresh-ahead; Redis: one copy for all instances, survives app restarts, rich data types.

This example is a small Maven project using the real library: [`examples/l4-senior/caching-patterns/caffeine`](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/claude/add-playbook-master-prompt/examples/l4-senior/caching-patterns/caffeine).

**CaffeineDemo.java**

```java
package com.freshcart.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Real Caffeine: a bounded, expiring, refreshing price cache with statistics. A manual ticker controls time. */
public class CaffeineDemo {

    public static void main(String[] args) {
        AtomicLong nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        Map<String, Integer> pricingDb = new HashMap<>(Map.of("MILK-1L", 68, "CURD-500G", 45, "EGGS-12", 84));
        Map<String, Integer> loads = new HashMap<>();
        List<Runnable> background = new ArrayList<>();          // background work runs when we say so

        LoadingCache<String, Integer> prices = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .refreshAfterWrite(Duration.ofMinutes(1))
                .executor(background::add)
                .ticker(ticker)
                .recordStats()
                .build(sku -> {
                    loads.merge(sku, 1, Integer::sum);
                    return pricingDb.get(sku);
                });

        for (int i = 0; i < 4; i++) prices.get("MILK-1L");
        prices.get("CURD-500G");
        System.out.println("after 5 reads: loads=" + new java.util.TreeMap<>(loads));

        pricingDb.put("MILK-1L", 70);
        nanos.addAndGet(Duration.ofSeconds(90).toNanos());
        System.out.println("t=90s read returns " + prices.get("MILK-1L") + " (old value served, refresh triggered)");
        while (!background.isEmpty()) background.remove(0).run();
        System.out.println("after the background refresh: " + prices.get("MILK-1L"));

        nanos.addAndGet(Duration.ofMinutes(11).toNanos());
        prices.cleanUp();
        System.out.println("t=12.5min entries left: " + prices.estimatedSize());

        CacheStats stats = prices.stats();
        System.out.printf("hits=%d misses=%d hitRate=%.2f loads=%d%n", stats.hitCount(), stats.missCount(), stats.hitRate(), stats.loadCount());
    }
}
```

1. Bounded size, a hard expiry and a softer refresh interval, with statistics on.
2. The loader makes this a read-through cache.
3. After the refresh interval, the old value is served while a reload runs in the background.
4. After the hard expiry, entries are gone.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.caching.CaffeineDemo` (or `mvn verify`, which checks this output)

```text
after 5 reads: loads={CURD-500G=1, MILK-1L=1}
t=90s read returns 68 (old value served, refresh triggered)
after the background refresh: 70
t=12.5min entries left: 0
hits=5 misses=2 hitRate=0.71 loads=3
```

| | Caffeine (L1) | Redis (L2) |
|-|---------------|------------|
| Latency | ~microseconds | ~0.5–1 ms in-region |
| Scope | One JVM | All instances |
| Consistency across instances | Needs broadcast invalidation | One copy |
| Capacity | Heap-limited | Memory of the Redis cluster |
| Survives app restart | No | Yes |
| Failure mode | GC pressure | Network/cluster outage |

**Use** Caffeine for hot, small, read-mostly data; Redis for shared state and larger datasets; both for very hot keys. **Avoid** Guava `CacheBuilder` in new code (Caffeine is its successor).
**Real-world Java:** Spring Boot `spring.cache.type=caffeine` or `redis`, Spring `CaffeineCacheManager`, Lettuce/Jedis/Redisson clients.
**Best practice:** export Caffeine stats and Redis hit ratio to Micrometer. **Gotcha:** Redis `maxmemory-policy` defaults to `noeviction`; a full cache then rejects writes instead of evicting.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** the FreshCart app loads its category menu on every launch; the menu changes a few times a day.

**CategoryMenuCacheDemo.java**

```java
import java.util.List;

/** Minimal: the app's category menu changes a few times a day, so one cached copy with a 5-minute TTL is plenty. */
public class CategoryMenuCacheDemo {

    static List<String> cachedMenu; static long cachedAt = Long.MIN_VALUE; static int dbQueries;
    static final long TTL_SECONDS = 300;

    static List<String> menu(long nowSeconds) {
        if (cachedMenu == null || nowSeconds - cachedAt >= TTL_SECONDS) {
            dbQueries++;
            cachedMenu = List.of("Dairy", "Fruits", "Vegetables", "Bakery");
            cachedAt = nowSeconds;
        }
        return cachedMenu;
    }

    public static void main(String[] args) {
        for (long t = 0; t < 900; t += 1) menu(t);       // one app open per second for 15 minutes
        System.out.println("900 menu requests -> " + dbQueries + " database queries");
        System.out.println(menu(900));
    }
}
```

1. Reload only when there's no copy or the copy is older than the TTL.
2. Remember when the copy was made.

**Run it:** `java CategoryMenuCacheDemo.java`

**Output:**

```text
900 menu requests -> 3 database queries
[Dairy, Fruits, Vegetables, Bakery]
```

**What to notice:**

- A five-minute TTL turned 900 queries into 3; a menu change shows up within five minutes, which the business accepted.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** during a sale, 50 hot SKUs receive 20 price requests per second each. Their cache entries were all filled at the same moment, so they all expire together.

**PriceStampedeDemo.java**

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Realistic: FreshCart caches prices in a shared cache. When a hot key expires during a sale, hundreds of requests
 * miss at once and all hit the pricing database (a stampede). Single-flight loading and TTL jitter prevent it.
 */
public class PriceStampedeDemo {

    record Entry(int price, long availableAt, long expiresAt) {}

    static final class PriceCache {
        final Map<String, Entry> entries = new HashMap<>();
        private final Map<String, Long> loadingUntil = new HashMap<>();
        private final boolean singleFlight; private final Random jitter;
        int dbLoads;
        PriceCache(boolean singleFlight, Random jitter) { this.singleFlight = singleFlight; this.jitter = jitter; }

        /** A request at time now (ms). A load takes 200 ms; requests during a load wait for it if single-flight is on. */
        String get(String sku, long now) {
            Entry e = entries.get(sku);
            if (e != null && now >= e.availableAt() && now < e.expiresAt()) return "hit";
            if (singleFlight && loadingUntil.getOrDefault(sku, -1L) > now) return "joined in-flight load";
            dbLoads++;
            loadingUntil.put(sku, now + 200);
            long ttl = 60_000 + (jitter == null ? 0 : jitter.nextInt(12_000));
            long ready = e != null && e.availableAt() > now ? e.availableAt() : now + 200;    // usable once the first load finishes
            entries.put(sku, new Entry(68, ready, ready + ttl));
            return "loaded";
        }
    }

    public static void main(String[] args) {
        for (boolean protectedCache : new boolean[] {false, true}) {
            PriceCache cache = new PriceCache(protectedCache, protectedCache ? new Random(3) : null);
            Map<String, Integer> outcomes = new TreeMap<>();
            for (int i = 0; i < 50; i++) cache.get("SKU-" + i, 0);          // warm 50 hot SKUs at the same moment
            cache.dbLoads = 0;
            int maxLoadsPerSecond = 0;
            for (long second = 60; second < 75; second++) {                // a sale: 20 requests per SKU per second
                int before = cache.dbLoads;
                for (int r = 0; r < 20; r++)
                    for (int i = 0; i < 50; i++) outcomes.merge(cache.get("SKU-" + i, second * 1000 + r * 50), 1, Integer::sum);
                maxLoadsPerSecond = Math.max(maxLoadsPerSecond, cache.dbLoads - before);
            }
            System.out.println((protectedCache ? "single-flight + jitter: " : "plain TTL:              ")
                    + "db loads=" + cache.dbLoads + ", worst second=" + maxLoadsPerSecond + " loads, " + outcomes);
        }
    }
}
```

1. Single-flight: requests arriving during a load wait for it instead of starting their own.
2. TTL jitter: entries filled together expire at different times.
3. The worst second shows the peak load on the pricing database.

**Run it:** `java PriceStampedeDemo.java`

**Output:**

```text
plain TTL:              db loads=200, worst second=200 loads, {hit=14800, loaded=200}
single-flight + jitter: db loads=50, worst second=8 loads, {hit=14800, joined in-flight load=150, loaded=50}
```

**What to notice:**

- Without protection, the pricing database got 200 queries in one second (4 per SKU while each load was in flight).
- With protection, 50 loads were spread out, at most 8 in a second; 150 requests joined an in-flight load.
- Caffeine's `get(key, loader)` is single-flight per key within one JVM; across instances, use a short Redis lock or refresh-ahead.

### Example 3: Production-grade

**Scenario:** FreshCart's catalog service runs many instances. Each keeps a small L1 cache; all share an L2 cache. Price updates must show up on every instance immediately, lookups for nonexistent SKUs (bots, old app versions) must not hammer the database, and a deploy that changes the cached format must not read old entries.

**TwoTierCatalogCacheDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Production-grade: catalog lookups go through a small in-process cache (L1, per app instance) and a shared cache
 * (L2, a Redis stand-in) before the database. Writes delete the L2 entry and broadcast an invalidation so every
 * instance drops its L1 copy. Missing SKUs are cached briefly too, and keys carry a schema version.
 */
public class TwoTierCatalogCacheDemo {

    record Product(String sku, String name, int price) {}
    record Entry(Optional<Product> value, long expiresAt) {}

    static final class SharedCache {
        final Map<String, Entry> entries = new HashMap<>();
        final List<AppInstance> subscribers = new ArrayList<>();
        void publishInvalidation(String key) { subscribers.forEach(s -> s.onInvalidate(key)); }
    }

    static final class Database {
        final Map<String, Product> rows = new HashMap<>();
        int reads;
        Optional<Product> find(String sku) { reads++; return Optional.ofNullable(rows.get(sku)); }
    }

    static final class AppInstance {
        static final String KEY_PREFIX = "catalog:v2:";
        static final long L1_TTL = 30_000, L2_TTL = 600_000, NEGATIVE_TTL = 60_000;
        final String name;
        private final SharedCache l2; private final Database db;
        private final Map<String, Entry> l1 = new LinkedHashMap<>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) { return size() > 3; }
        };
        AppInstance(String name, SharedCache l2, Database db) { this.name = name; this.l2 = l2; this.db = db; l2.subscribers.add(this); }

        String get(String sku, long now) {
            String key = KEY_PREFIX + sku;
            Entry e = l1.get(key);
            if (e != null && now < e.expiresAt()) return describe(sku, e, "L1");
            e = l2.entries.get(key);
            if (e != null && now < e.expiresAt()) { l1.put(key, new Entry(e.value(), Math.min(e.expiresAt(), now + L1_TTL))); return describe(sku, e, "L2"); }
            Optional<Product> loaded = db.find(sku);
            Entry fresh = new Entry(loaded, now + (loaded.isPresent() ? L2_TTL : NEGATIVE_TTL));
            l2.entries.put(key, fresh);
            l1.put(key, new Entry(loaded, Math.min(fresh.expiresAt(), now + L1_TTL)));
            return describe(sku, fresh, "DB");
        }

        void updatePrice(String sku, int price) {
            Product p = db.rows.get(sku);
            db.rows.put(sku, new Product(sku, p.name(), price));
            l2.entries.remove(KEY_PREFIX + sku);
            l2.publishInvalidation(KEY_PREFIX + sku);
        }

        void onInvalidate(String key) { l1.remove(key); }

        private String describe(String sku, Entry e, String source) {
            return name + " " + sku + " from " + source + ": " + e.value().map(p -> p.name() + " Rs " + p.price()).orElse("not found");
        }
    }

    public static void main(String[] args) {
        Database db = new Database();
        db.rows.put("MILK-1L", new Product("MILK-1L", "DairyDay Toned Milk 1L", 68));
        db.rows.put("CURD-500G", new Product("CURD-500G", "DairyDay Curd 500g", 45));
        SharedCache redis = new SharedCache();
        AppInstance a = new AppInstance("app-a", redis, db), b = new AppInstance("app-b", redis, db);

        System.out.println(a.get("MILK-1L", 0));
        System.out.println(a.get("MILK-1L", 1_000));
        System.out.println(b.get("MILK-1L", 2_000));
        System.out.println(a.get("NOSUCH-1", 3_000));
        System.out.println(b.get("NOSUCH-1", 4_000));
        a.updatePrice("MILK-1L", 70);
        System.out.println(b.get("MILK-1L", 5_000));
        System.out.println(a.get("MILK-1L", 6_000));
        System.out.println(b.get("MILK-1L", 40_000));
        System.out.println("database reads: " + db.reads + " for 8 lookups");
    }
}
```

1. The shared cache with a pub/sub channel for invalidations (Redis in production).
2. Keys carry a schema version; a format change bumps it.
3. The local cache is small, LRU-bounded and short-lived.
4. "Not found" is cached too, with a shorter TTL (negative caching).
5. Writes delete the shared entry and broadcast an invalidation to every instance's L1.

**Run it:** `java TwoTierCatalogCacheDemo.java`

**Output:**

```text
app-a MILK-1L from DB: DairyDay Toned Milk 1L Rs 68
app-a MILK-1L from L1: DairyDay Toned Milk 1L Rs 68
app-b MILK-1L from L2: DairyDay Toned Milk 1L Rs 68
app-a NOSUCH-1 from DB: not found
app-b NOSUCH-1 from L2: not found
app-b MILK-1L from DB: DairyDay Toned Milk 1L Rs 70
app-a MILK-1L from L2: DairyDay Toned Milk 1L Rs 70
app-b MILK-1L from L2: DairyDay Toned Milk 1L Rs 70
database reads: 3 for 8 lookups
```

**What to notice:**

- Eight lookups needed only three database reads, including one for a SKU that doesn't exist.
- After app-a's price update, app-b saw Rs 70 straight away because its L1 copy was invalidated.
- The L1 TTL is a safety net for missed pub/sub messages (Redis pub/sub is fire-and-forget).

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Cache-aside | Simple, resilient to cache outage | Invalidation in every write path; first-hit latency |
| Read-through | Simple callers, central loading | Cache becomes a required dependency |
| Write-through | Fresh cache after writes | Higher write latency; caches cold data |
| Write-behind | Fast writes, coalescing | Possible data loss; store lags |
| TTL | Bounded staleness, no coordination | Stale until expiry |
| Two-tier | Microsecond hits, shared state | Two invalidation paths to get right |

## 8. When to Use / When NOT to Use

**Cache when:**

- data is read far more than written;
- the same keys are requested repeatedly (skewed access);
- some staleness is acceptable and agreed.

**Don't when:**

- every read must see the latest write (balances, stock at checkout);
- access is uniformly random over a huge key space (low hit rate);
- the underlying query is already fast enough; caches add failure modes.

## 9. Best Practices

1. **Every cache entry has a TTL.** *(Consensus.)*
2. **Delete on write rather than set on write.** *(Consensus.)*
3. **Bound cache size; never use an unbounded `HashMap` as a cache.** *(Consensus.)*
4. **Protect hot keys from stampedes (single-flight, jitter, refresh-ahead).** *(Consensus.)*
5. **Version keys when the cached format changes.** *(Consensus.)*
6. **Measure hit rate, latency and evictions.** *(Consensus.)*
7. **Agree acceptable staleness per data type with the business.** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Cache stampede on a hot key (production failure mode)**  
> **Symptom:** every hour on the hour, the pricing database spiked to 100% CPU and checkout latency tripled.
> **Root cause:** a nightly job warmed all price keys at once with the same 1-hour TTL; they expired together under peak traffic.
> **Fix:** TTL jitter, single-flight loading, refresh-ahead for hot keys (Example 2).

> 🚨 **Unbounded in-memory cache**  
> **Symptom:** heap grew steadily; full GCs; `OutOfMemoryError` after a few days.
> **Root cause:** a static `ConcurrentHashMap` used as a cache with no size limit or expiry.
> **Fix:** Caffeine with `maximumSize` and `expireAfterWrite`.

> 🚨 **Forgotten invalidation path**  
> **Symptom:** prices fixed by the ops team via SQL stayed wrong in the app for a day.
> **Root cause:** only the API's write path deleted cache keys; TTL was 24 hours.
> **Fix:** shorter TTL, CDC-driven invalidation, and an admin "evict" tool.

> 🚨 **Caching personal data in shared keys**  
> **Symptom:** a customer saw another customer's cart.
> **Root cause:** a cache key built without the customer ID on a personalised endpoint.
> **Fix:** include every input that changes the response in the key; review cache keys for personalised data.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Caffeine `maximumSize` | unbounded | Entry limit | Always set | Too small: low hit rate |
| Caffeine `expireAfterWrite` | none | Hard TTL | Always set | Too long: staleness |
| Caffeine `refreshAfterWrite` | none | Background refresh | Hot keys | Extra loads |
| Redis `maxmemory-policy` | noeviction | Behaviour when full | `allkeys-lru` or `allkeys-lfu` for caches | Wrong policy evicts sessions |
| Redis `maxmemory` | 0 (no limit, 64-bit) | Memory cap | Always set for caches | OOM kills |
| Spring `spring.cache.redis.time-to-live` | none | Default TTL for `@Cacheable` | Always set | Staleness |

## 12. Security Considerations

- Cache keys for personalised data must include the user or tenant; review them like access-control code.
- Don't cache secrets or tokens longer than their validity; encrypt sensitive values in shared caches.
- Redis must not be reachable from the internet; enable AUTH/ACLs and TLS.
- Unbounded keys from user input (search terms) can be used to flush or bloat a cache; bound and normalise them.

## 13. Performance & Scalability

Hit rate drives everything: at a 95% hit rate, the database sees 5% of reads; at 50%, the cache may cost more than it saves. W-TinyLFU (Caffeine) gives near-optimal hit rates for skewed workloads. Serialisation dominates Redis cost for large values; prefer compact formats and small values. Hot keys in Redis concentrate on one shard; add an L1 or replicate the key. Watch GC impact of large on-heap caches; use weights for variable-size values.

## 14. Testing It

Test hit/miss paths, invalidation across instances, negative caching, expiry and bounds with a controllable clock:

**TwoTierCatalogCacheTest.java**

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwoTierCatalogCacheTest {

    TwoTierCatalogCacheDemo.Database db;
    TwoTierCatalogCacheDemo.SharedCache l2;
    TwoTierCatalogCacheDemo.AppInstance a, b;

    @BeforeEach
    void setUp() {
        db = new TwoTierCatalogCacheDemo.Database();
        db.rows.put("MILK-1L", new TwoTierCatalogCacheDemo.Product("MILK-1L", "Milk", 68));
        l2 = new TwoTierCatalogCacheDemo.SharedCache();
        a = new TwoTierCatalogCacheDemo.AppInstance("a", l2, db);
        b = new TwoTierCatalogCacheDemo.AppInstance("b", l2, db);
    }

    @Test
    void secondInstanceIsServedFromSharedCache() {
        a.get("MILK-1L", 0);
        assertTrue(b.get("MILK-1L", 1).contains("from L2"));
        assertEquals(1, db.reads);
    }

    @Test
    void updateIsVisibleOnEveryInstanceImmediately() {
        a.get("MILK-1L", 0);
        b.get("MILK-1L", 0);
        a.updatePrice("MILK-1L", 70);
        assertTrue(a.get("MILK-1L", 1).endsWith("Rs 70"));
        assertTrue(b.get("MILK-1L", 1).endsWith("Rs 70"));
    }

    @Test
    void missingSkuIsCachedButOnlyBriefly() {
        a.get("NOPE", 0);
        a.get("NOPE", 10_000);
        b.get("NOPE", 20_000);
        assertEquals(1, db.reads);
        b.get("NOPE", TwoTierCatalogCacheDemo.AppInstance.NEGATIVE_TTL + 1);
        assertEquals(2, db.reads);
    }

    @Test
    void localCopyExpiresBeforeSharedCopy() {
        a.get("MILK-1L", 0);
        assertTrue(a.get("MILK-1L", TwoTierCatalogCacheDemo.AppInstance.L1_TTL + 1).contains("from L2"));
    }

    @Test
    void localCacheIsBoundedLeastRecentlyUsed() {
        for (String s : new String[] {"S1", "S2", "S3", "S4"}) db.rows.put(s, new TwoTierCatalogCacheDemo.Product(s, s, 1));
        a.get("S1", 0); a.get("S2", 0); a.get("S3", 0);
        a.get("S1", 1);                 // S1 recently used, S2 is now eldest
        a.get("S4", 2);                 // evicts S2
        assertTrue(a.get("S1", 3).contains("from L1"));
        assertTrue(a.get("S2", 3).contains("from L2"));
    }

    @Test
    void keysCarryTheSchemaVersion() {
        a.get("MILK-1L", 0);
        assertTrue(l2.entries.containsKey("catalog:v2:MILK-1L"));
    }
}
```

For Caffeine, inject a `Ticker` (as in `CaffeineDemo`) and a same-thread or manual executor; for Redis, use Testcontainers.

## 15. Observability & Operations

Export hit rate, miss rate, load time, evictions and size per cache (Caffeine `recordStats()` with Micrometer; Redis `INFO stats` keyspace hits/misses). Alert on sudden hit-rate drops (often a key-format bug after a deploy). Track Redis memory, evicted keys and latency. Keep a safe tool for evicting a key or prefix during incidents.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2009 | Redis released |
| 2011 | Guava `CacheBuilder` |
| 2014 | JCache (JSR 107) final |
| 2015 | Caffeine released as Guava cache successor (W-TinyLFU eviction added in 2.x) |
| 2021 | Caffeine 3.0 (Java 11 baseline) |
| 2024 | Redis licence change; Valkey fork under the Linux Foundation |
| 2025 | Redis 8 adds AGPLv3 as a licensing option |

## 17. Expert Deep Dive

- **W-TinyLFU.** Caffeine admits new entries only if their estimated frequency beats the eviction victim's, using a compact count-min sketch; this resists scans that flush LRU caches.
- **Probabilistic early expiration (XFetch).** Each reader refreshes slightly before expiry with a probability that grows near the deadline, avoiding stampedes without locks.
- **Client-side caching in Redis 6+.** Server-assisted invalidation (`CLIENT TRACKING`) tells clients when keys they cached change.
- **Cache consistency models.** Read-your-writes can be provided per session by bypassing or updating the cache for the writer.
- **Hot key detection.** Redis `--hotkeys` (with LFU policy) and client-side sampling find keys that need L1 or replication.

> 📖 **War story: the top of the hour**  
> FreshCart's pricing database spiked to full CPU at exactly :00 every hour during evening peak, and checkout latency tripled for about 40 seconds. A nightly warm-up job had loaded all 30,000 price keys with a fixed 1-hour TTL, so they expired together, every hour, forever. Each expiry produced hundreds of identical queries per hot SKU. **Fixes:** TTL jitter of plus or minus 10%, `refreshAfterWrite` in the L1 Caffeine cache so hot keys refreshed in the background, and a short Redis lock for loads of the same key across instances. The spike disappeared. **Lesson:** caches that are filled together expire together; jitter is cheap.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Why cache?** **Lower latency, less load** on slow stores.
2. **What's a TTL?** **Time after which an entry expires.**
3. **What's cache-aside?** The **app loads on a miss and invalidates on write**.
4. **What's a cache hit rate?** **Hits / (hits + misses).**
5. **What's eviction?** **Removing entries** when the cache is full.

**▸ 🟡 L3**

1. **Write-through vs write-behind?** **Synchronous store write** vs **asynchronous batched**.
2. **Why delete instead of update on write?** **Concurrent writers can leave the older value.**
3. **What's a cache stampede?** **Many misses loading the same key at once.**
4. **Caffeine vs Redis?** **In-process, per JVM** vs **shared over network**.
5. **What's negative caching?** **Caching "not found"** briefly.

**▸ 🔴 L4–L5**

1. **How do you keep L1 caches consistent across instances?** **Broadcast invalidation + short TTL.**
2. **How do you prevent stampedes across instances?** **Single-flight per JVM, distributed lock or refresh-ahead, jitter.**
3. **When is caching the wrong answer?** **Strong consistency needs, low hit rates, already fast queries.**
4. **What's W-TinyLFU?** **Frequency-based admission** with a sketch; resists scans.
5. **How do you roll out a change to cached formats?** **Versioned keys.**

**▸ 💻 Hands-on**

**Task:** Add refresh-ahead to `CategoryMenuCacheDemo`: after 240 seconds, return the cached menu but schedule a reload.

> ✅ **Solution**  
> ```java
> static boolean refreshScheduled;
> static List<String> menu(long now) {
>     if (cachedMenu == null || now - cachedAt >= TTL_SECONDS) return reload(now);
>     if (now - cachedAt >= 240 && !refreshScheduled) { refreshScheduled = true; executor.execute(() -> reload(now)); }
>     return cachedMenu;   // callers never wait for a reload once the menu is cached
> }
> ```

## 19. Practice

- [ ] Add Caffeine to a Spring Boot app with `@Cacheable` and expose its stats via Actuator.
    > 💡 **Hint**  
    > `spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=10m,recordStats`.

- [ ] Reproduce the cache-aside race with two threads and a latch.
    > 💡 **Hint**  
    > Pause the reader between its DB read and its cache put.

- [ ] Implement single-flight for Redis with `SET key value NX PX 2000`.
    > 💡 **Hint**  
    > Losers wait briefly and re-read the cache.

- [ ] Measure the hit rate of `TwoTierCatalogCacheDemo` with 1,000 random SKUs from a Zipf distribution.
    > 💡 **Hint**  
    > Skewed access makes small caches effective.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Cache-aside by default: read, load on miss, delete on write.
> - Read/write-through: cache owns loading and writing; write-behind risks loss.
> - TTL on everything; jitter it; delete on write; version keys.
> - Stampedes: single-flight, jitter, refresh-ahead.
> - Caffeine = L1 in-process; Redis = L2 shared; broadcast invalidations to L1.
> - Measure hit rate; bound size.
>
> ```java
> Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(10))
>         .refreshAfterWrite(Duration.ofMinutes(1)).recordStats().build(loader);
> ```

## 21. Further Reading

- [Caffeine wiki](https://github.com/ben-manes/caffeine/wiki), including "Efficiency" (W-TinyLFU).
- [Redis documentation: client-side caching and eviction policies](https://redis.io/docs/latest/).
- Martin Kleppmann, *Designing Data-Intensive Applications* (2017), chapter on derived data.
- Vattani, Chierichetti and Lowenstein, "Optimal Probabilistic Cache Stampede Prevention" (VLDB 2015).

---

[← Previous: Messaging and Integration Patterns](messaging-patterns.md) · [Next: API Design Patterns →](api-design-patterns.md)
