# Flyweight

L2 🔵 Practitioner · ⏱ 30 min read · Prerequisites: [Factory Method](../l1-foundations/factory-method.md), [Immutability](../l1-foundations/oop-refresher.md)

> 📌 **TL;DR**  
> Flyweight saves memory when you have huge numbers of similar objects: split each object's data into **intrinsic** state (the same for many objects, immutable, shared) and **extrinsic** state (unique per use, kept outside or passed in). A factory hands out one shared instance per intrinsic value. The JDK uses it for `Integer.valueOf`, `Boolean.TRUE` and the String pool, which is also why `==` on boxed numbers is a classic bug. Measure first: it only pays off with many objects and repeated, heavy intrinsic data.

## 1. What It Is

**Plain English.** FreshCart's operations map shows 5,000 live orders. Each marker needs an icon and a colour, and there are only nine combinations (three statuses × three vehicle types). Loading a separate icon image for every order would waste memory; instead, nine shared marker styles are drawn at 5,000 different positions. The style is the flyweight; the position is passed in when drawing.

**Definition (GoF).** "Use sharing to support large numbers of fine-grained objects efficiently." Parts:

- **Flyweight:** an immutable object holding intrinsic (shareable) state, with operations that take extrinsic state as parameters.
- **Flyweight factory:** creates and caches flyweights by key, returning the existing instance when one exists.
- **Client:** stores or computes extrinsic state and passes it to the flyweight.
- **Intrinsic state:** independent of context, shared (font, icon, unit of measure).
- **Extrinsic state:** depends on context, not shared (position, quantity, order ID).

## 2. Why It Exists

Memory is finite, and garbage collection time grows with the live heap. When an application holds hundreds of thousands of objects that each carry a copy of the same heavy data (a decoded image, a set of strings, a lookup table), most of the heap is duplicates. Sharing one immutable copy turns O(n) duplicated data into O(distinct values), which can be the difference between fitting in a 512 MB container and running out of memory.

## 3. Mental Model / Analogy

**Analogy.** A printing press's movable type. A page has thousands of letters, but the printer owns a few copies of each letter shape. The shape (intrinsic) is shared; where it sits on the page (extrinsic) is decided when the page is set.

**Technical model.** A `Map<Key, Flyweight>` behind a factory method. Clients hold references to shared flyweights plus their own small extrinsic fields. Correctness depends on immutability: if a shared flyweight could change, every user would see the change.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Intrinsic vs. extrinsic state

**Description.** The core design decision. Intrinsic state is the same across many objects and doesn't depend on where or how the object is used; it goes into the shared flyweight. Extrinsic state differs per use; it stays in the client (a small object or array) or is passed as a method parameter.

**Advantages / features.** Duplicated heavy data disappears; flyweights are immutable and therefore thread-safe; the client objects become tiny.

**Before (every line carries a copy) and after (shared details, small lines):**

**IntrinsicExtrinsicDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Intrinsic state (shared, immutable) lives in the flyweight; extrinsic state (per use) is stored outside or passed in. */
public class IntrinsicExtrinsicDemo {

    // ===================== Before: every order line carries a full copy of the product details =====================
    record HeavyLine(String name, String category, byte[] thumbnail, int qty, String orderId) { }

    // ===================== After: product details shared; the line keeps only what is unique to it =====================
    record ProductType(String name, String category, byte[] thumbnail) { }
    record OrderLine(ProductType type, int qty, String orderId) {
        String label() { return qty + " x " + type.name() + " (" + type.category() + ") for " + orderId; }
    }

    public static void main(String[] args) {
        String[] names = {"Milk 1L", "Bread", "Eggs x6", "Banana", "Rice 5kg"};
        int lines = 10_000;

        List<HeavyLine> heavy = new ArrayList<>();
        for (int i = 0; i < lines; i++) heavy.add(new HeavyLine(names[i % 5], "Daily", new byte[2048], 1 + i % 3, "FC-" + i));

        Map<String, ProductType> types = new HashMap<>();
        List<OrderLine> light = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            ProductType t = types.computeIfAbsent(names[i % 5], n -> new ProductType(n, "Daily", new byte[2048]));
            light.add(new OrderLine(t, 1 + i % 3, "FC-" + i));
        }

        Map<byte[], Boolean> heavyThumbs = new IdentityHashMap<>(), lightThumbs = new IdentityHashMap<>();
        heavy.forEach(h -> heavyThumbs.put(h.thumbnail(), true));
        light.forEach(l -> lightThumbs.put(l.type().thumbnail(), true));
        System.out.println("before: " + heavyThumbs.size() + " thumbnails = " + heavyThumbs.size() * 2 / 1024 + " MB");
        System.out.println("after:  " + lightThumbs.size() + " thumbnails = " + lightThumbs.size() * 2 + " KB, shared by " + lines + " lines");
        System.out.println(light.get(7).label());
    }
}
```

1. Intrinsic: name, category and the thumbnail are the same for every line of the same product.
2. Extrinsic: quantity and order ID are unique per line and stay in the small record.
3. Operations combine both: shared details plus the line's own data.

**Run it:** `java IntrinsicExtrinsicDemo.java`

```text
before: 10000 thumbnails = 19 MB
after:  5 thumbnails = 10 KB, shared by 10000 lines
2 x Eggs x6 (Daily) for FC-7
```

| Pros | Cons |
|------|------|
| Duplicated data stored once | Extra indirection to reach shared data |
| Flyweights immutable and thread-safe | Splitting state can make the model less obvious |
| Smaller, faster-to-scan heaps | Extrinsic state must be supplied correctly every time |

**Use it** when many objects share heavy, immutable data. **Avoid** it when the "shared" part is small (a few fields); the reference costs about as much as the data.
**Real-world Java:** JPA entities referencing shared enum or reference-data entities, `java.awt.Font` objects shared by many text components, `Charset` instances.
**Best practice:** make the intrinsic type a `record` with no setters. **Gotcha:** a "shared" object with even one mutable field (a cached computed price) leaks changes between all users.

### Flyweight factory

**Description.** The factory owns the cache and guarantees "at most one instance per key". In concurrent code, `ConcurrentHashMap.computeIfAbsent` creates each flyweight exactly once without explicit locks. Invalid keys should fail before anything is cached.

**Advantages / features.** One place controls creation; callers can't accidentally create duplicates; thread-safe sharing with a single call.

**FlyweightFactoryDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/** A flyweight factory hands out shared instances by key, creating each one at most once, safely across threads. */
public class FlyweightFactoryDemo {

    record UnitOfMeasure(String code, String label, int decimals) { }

    static final class Units {
        private static final Map<String, UnitOfMeasure> CACHE = new ConcurrentHashMap<>();
        static final AtomicInteger created = new AtomicInteger();
        static UnitOfMeasure of(String code) {
            return CACHE.computeIfAbsent(code, c -> {
                created.incrementAndGet();
                return switch (c) {
                    case "KG" -> new UnitOfMeasure("KG", "kilogram", 3);
                    case "L" -> new UnitOfMeasure("L", "litre", 3);
                    case "PCS" -> new UnitOfMeasure("PCS", "piece", 0);
                    default -> throw new IllegalArgumentException("unknown unit " + c);
                };
            });
        }
    }

    public static void main(String[] args) {
        List<String> feed = List.of("KG", "L", "PCS");
        IntStream.range(0, 300_000).parallel().forEach(i -> Units.of(feed.get(i % 3)));
        System.out.println("300,000 lookups from many threads -> instances created: " + Units.created.get());
        System.out.println("same instance: " + (Units.of("KG") == Units.of("KG")));
        try { Units.of("BOX"); } catch (IllegalArgumentException e) { System.out.println(e.getMessage() + " (nothing cached for it)"); }
    }
}
```

1. The flyweight: a small immutable record.
2. `computeIfAbsent` runs the creation function at most once per key, even under heavy concurrency.
3. An exception inside the function leaves nothing in the cache, so bad keys don't pollute it.

**Run it:** `java FlyweightFactoryDemo.java`

```text
300,000 lookups from many threads -> instances created: 3
same instance: true
unknown unit BOX (nothing cached for it)
```

| Factory style | Pros | Cons |
|---------------|------|------|
| Enum constants | Fixed set, zero code | Only for values known at compile time |
| `EnumMap` / `Map.of` prebuilt | Simple, no locking | Fixed set |
| `ConcurrentHashMap.computeIfAbsent` | Lazy, thread-safe, open set | Unbounded unless capped |
| Caffeine / weak-value cache | Bounded, evicts unused values | Library dependency; identity may change after eviction |

**Use** enums for small fixed sets and a concurrent map for open sets with a known upper bound. **Avoid** unbounded caches keyed by user input.
**Real-world Java:** `Integer.valueOf`, `Charset.forName`, `ZoneId.of` (caches region data), Guava `Interners.newWeakInterner()`.
**Best practice:** make constructors private (or package-private) so only the factory creates flyweights. **Gotcha:** don't do slow work or call `computeIfAbsent` on the same map recursively inside the mapping function; it can block other threads or throw `IllegalStateException`.

### Integer cache and String interning

**Description.** The JDK's best-known flyweights. `Integer.valueOf` (and autoboxing) returns cached instances for -128 to 127; `Boolean.valueOf` returns `TRUE`/`FALSE`; string literals are interned in the String pool, and `String.intern()` adds runtime strings to it. Because of these caches, `==` on boxed values or strings sometimes works and sometimes doesn't, which is a classic bug.

**Advantages / features.** Less allocation for common small values and constant strings, without any code from you.

**IntegerCacheStringInternDemo.java**

```java
/** The JDK's own flyweights: the Integer cache, Boolean constants and the String pool. And the == trap they create. */
public class IntegerCacheStringInternDemo {

    public static void main(String[] args) {
        Integer a = 127, b = 127;
        Integer c = 128, d = 128;
        System.out.println("Integer 127 == 127: " + (a == b) + "   (cached, -128..127)");
        System.out.println("Integer 128 == 128: " + (c == d) + "  (new objects) -> always use equals: " + c.equals(d));

        System.out.println("Boolean.valueOf(true) == Boolean.TRUE: " + (Boolean.valueOf(true) == Boolean.TRUE));

        String s1 = "FreshCart";
        String s2 = "FreshCart";
        String s3 = new String("FreshCart");
        System.out.println("literal == literal: " + (s1 == s2) + ", literal == new String: " + (s1 == s3) + ", after intern(): " + (s1 == s3.intern()));

        String fromFeed = new StringBuilder("Dai").append("ry").toString();
        System.out.println("built at runtime == literal: " + (fromFeed == "Dairy") + ", equals: " + fromFeed.equals("Dairy"));
    }
}
```

1. Autoboxing calls `Integer.valueOf`, which returns cached instances only in -128..127.
2. `Boolean.valueOf` always returns one of two shared constants; never use `new Boolean`.
3. Compile-time string literals are interned; `new String` creates a separate copy.
4. Strings built at runtime (parsed, concatenated) are not interned: compare with `equals`.

**Run it:** `java IntegerCacheStringInternDemo.java`

```text
Integer 127 == 127: true   (cached, -128..127)
Integer 128 == 128: false  (new objects) -> always use equals: true
Boolean.valueOf(true) == Boolean.TRUE: true
literal == literal: true, literal == new String: false, after intern(): true
built at runtime == literal: false, equals: true
```

| Cache | Range / scope | Configurable | Trap |
|-------|---------------|--------------|------|
| `Integer`, `Short`, `Long`, `Byte`, `Character` | -128..127 (Character 0..127) | `Integer` upper bound via `-XX:AutoBoxCacheMax` | `==` works in tests with small IDs, fails in production |
| `Boolean` | `TRUE`, `FALSE` | No | `new Boolean` (deprecated) bypasses it |
| String pool | Literals and `intern()` | Pool table size (`-XX:StringTableSize`) | `==` works for literals only |

**Use** `equals` for all value comparisons; let the JDK caches be an invisible optimisation. **Avoid** `intern()` as a general memory fix; G1 string deduplication or your own interner is safer.
**Real-world Java:** `Integer.valueOf`, `String.intern`, G1's `-XX:+UseStringDeduplication`, JDK 9 compact strings (Latin-1 strings take 1 byte per char).
**Best practice:** enable static analysis rules for `==` on boxed types and strings (SpotBugs, Error Prone). **Gotcha:** deprecated wrapper constructors (`new Integer(5)`) are marked for removal since Java 16 and bypass the cache.

### Measuring the memory win

**Description.** Flyweight is an optimisation, so prove it pays. First estimate: number of objects × size of the duplicated part. Then measure: heap after GC with and without sharing, a heap histogram (`jcmd <pid> GC.class_histogram`), or exact object sizes with JOL (Java Object Layout). Only adopt the pattern when the saving is significant.

**Advantages / features.** Avoids complicating code for no gain; gives numbers for a design review.

**MeasuringMemoryDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measure before you optimise. Estimate first (count objects x size), then measure the real heap.
 * (Exact per-object sizes need JOL - Java Object Layout; Runtime numbers are approximate, so we compare, not print raw bytes.)
 */
public class MeasuringMemoryDemo {

    record Style(String fontName, int size, String colour, byte[] glyphCache) { }
    record Label(String text, Style style) { }

    static long usedAfterGc() {
        for (int i = 0; i < 3; i++) System.gc();
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    static List<Label> build(int n, boolean share) {
        Map<String, Style> styles = new HashMap<>();
        List<Label> labels = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String key = (i % 4 == 0) ? "promo" : "normal";
            Style s = share ? styles.computeIfAbsent(key, k -> new Style("Inter", 12, k, new byte[4096]))
                            : new Style("Inter", 12, key, new byte[4096]);
            labels.add(new Label("SKU-" + i, s));
        }
        return labels;
    }

    public static void main(String[] args) {
        int n = 20_000;
        long estimateUnshared = (long) n * 4096, estimateShared = 2L * 4096;
        System.out.println("estimate: unshared ~" + estimateUnshared / 1_000_000 + " MB of glyph caches, shared ~" + estimateShared / 1024 + " KB");

        long base = usedAfterGc();
        List<Label> unshared = build(n, false);
        long unsharedBytes = usedAfterGc() - base;
        unshared = null;

        base = usedAfterGc();
        List<Label> shared = build(n, true);
        long sharedBytes = usedAfterGc() - base;

        System.out.println("measured: shared version uses less than a quarter of the memory: " + (sharedBytes * 4 < unsharedBytes));
        System.out.println("labels still usable: " + shared.get(4).text() + " -> " + shared.get(4).style().colour());
    }
}
```

1. `System.gc()` is only a hint; calling it a few times gives a rough "live heap" reading, good enough to compare two designs.
2. Estimate first: 20,000 labels × a 4 KB glyph cache is about 80 MB if not shared.
3. Compare the two measurements rather than trusting a single raw number.

**Run it:** `java MeasuringMemoryDemo.java`

```text
estimate: unshared ~81 MB of glyph caches, shared ~8 KB
measured: shared version uses less than a quarter of the memory: true
labels still usable: SKU-4 -> promo
```

| Tool | What it tells you | When |
|------|-------------------|------|
| Back-of-envelope estimate | Order of magnitude | Before writing code |
| `Runtime` used memory after GC | Rough comparison | Quick experiments |
| `jcmd <pid> GC.class_histogram` | Instance counts and bytes per class | In a running service |
| Heap dump + Eclipse MAT | Duplicate strings, retained sizes | Investigating real memory problems |
| JOL (`GraphLayout.parseInstance`) | Exact layout and deep size | Precise micro-measurements |

**Use** measurements to decide. **Avoid** flyweights for a few thousand objects; modern heaps handle that easily.
**Real-world Java:** Eclipse MAT's "duplicate strings" report is the usual first sign that a flyweight (or interning) is worth it.
**Best practice:** record the before/after numbers in the pull request. **Gotcha:** compressed references (`-XX:+UseCompressedOops`, on by default below 32 GB) make each reference 4 bytes, so sharing tiny objects saves almost nothing.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** the product list shows a category badge on 10,000 product tiles; there are four categories.

**CategoryBadgeDemo.java**

```java
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Minimal Flyweight: 10,000 products in a list, but only one badge object per category. */
public class CategoryBadgeDemo {

    enum Category { DAIRY, BAKERY, FRUIT, VEG }
    record Badge(String text, String colour, String icon) { }

    static final Map<Category, Badge> BADGES = new EnumMap<>(Map.of(
            Category.DAIRY, new Badge("Dairy", "#2E86DE", "milk.svg"),
            Category.BAKERY, new Badge("Bakery", "#D35400", "bread.svg"),
            Category.FRUIT, new Badge("Fruit", "#27AE60", "apple.svg"),
            Category.VEG, new Badge("Veg", "#16A085", "leaf.svg")));

    record ProductTile(String name, Badge badge) { }

    public static void main(String[] args) {
        List<ProductTile> tiles = new ArrayList<>();
        Category[] cats = Category.values();
        for (int i = 0; i < 10_000; i++) tiles.add(new ProductTile("Product " + i, BADGES.get(cats[i % cats.length])));
        long distinct = tiles.stream().map(ProductTile::badge).distinct().count();
        System.out.println(tiles.size() + " tiles share " + distinct + " badge objects");
        System.out.println(tiles.get(2).name() + " shows " + tiles.get(2).badge());
    }
}
```

1. The badge is an immutable record: safe to share.
2. A prebuilt `EnumMap` is the simplest flyweight factory for a fixed set.

**Run it:** `java CategoryBadgeDemo.java`

**Output:**

```text
10000 tiles share 4 badge objects
Product 2 shows Badge[text=Fruit, colour=#27AE60, icon=apple.svg]
```

**What to notice:**

- 10,000 tiles, 4 badges. The tile keeps only its own name and a reference.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the operations dashboard draws every live order on a city map, refreshing every few seconds. Marker styles (icon bitmap and colour) depend only on status and vehicle type.

**DeliveryMapMarkersDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Realistic Flyweight: the ops map draws 5,000 live orders. Marker styles are shared; positions are passed in at draw time. */
public class DeliveryMapMarkersDemo {

    enum Status { PACKING, ON_THE_WAY, DELAYED }
    enum Vehicle { BIKE, SCOOTER, VAN }

    /** Flyweight: intrinsic, immutable, shared. In a real app it holds a decoded bitmap (tens of KB). */
    record MarkerStyle(Status status, Vehicle vehicle, String colour, String icon) {
        String draw(double lat, double lon, String orderId) {
            return String.format("%s %s at (%.3f, %.3f) %s", icon, colour, lat, lon, orderId);
        }
    }

    static final class MarkerStyles {
        private final Map<String, MarkerStyle> cache = new ConcurrentHashMap<>();
        MarkerStyle get(Status s, Vehicle v) {
            return cache.computeIfAbsent(s + "/" + v, k -> new MarkerStyle(s, v,
                    switch (s) { case PACKING -> "grey"; case ON_THE_WAY -> "green"; case DELAYED -> "red"; },
                    v.name().toLowerCase() + ".png"));
        }
        int size() { return cache.size(); }
    }

    record LiveOrder(String id, double lat, double lon, Status status, Vehicle vehicle) { }

    public static void main(String[] args) {
        List<LiveOrder> orders = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            orders.add(new LiveOrder("FC-" + (6000 + i), 12.90 + (i % 100) * 0.001, 77.55 + (i / 100) * 0.001,
                    Status.values()[i % 3], Vehicle.values()[(i / 3) % 3]));
        }
        MarkerStyles styles = new MarkerStyles();
        List<String> frame = new ArrayList<>();
        for (LiveOrder o : orders) frame.add(styles.get(o.status(), o.vehicle()).draw(o.lat(), o.lon(), o.id()));
        System.out.println("drew " + frame.size() + " markers using " + styles.size() + " shared styles");
        frame.subList(0, 3).forEach(System.out::println);
    }
}
```

1. The flyweight holds intrinsic state only; in a real app this includes a decoded bitmap.
2. Extrinsic state (position, order ID) is passed in at draw time, never stored in the style.
3. The live order keeps its own small, changing data.

**Run it:** `java DeliveryMapMarkersDemo.java`

**Output:**

```text
drew 5000 markers using 9 shared styles
bike.png grey at (12.900, 77.550) FC-6000
bike.png green at (12.901, 77.550) FC-6001
bike.png red at (12.902, 77.550) FC-6002
```

**What to notice:**

- 5,000 markers, 9 styles; if each bitmap were 30 KB, that's about 270 KB instead of about 150 MB.
- Orders move every refresh; styles never change, so they're never re-created.

### Example 3: Production-grade

**Scenario:** the nightly supplier feed creates 200,000 SKUs. Each row's parser creates new objects for brand, unit, category and tax rate, but there are only a few dozen distinct combinations. Requirements: share equal attribute sets safely across loader threads; refuse to share mutable types; protect the cache from a broken feed where every value is unique; report metrics to prove the saving.

**CatalogInternerDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Production-grade Flyweight: interning repeated attribute values while loading a 200,000-SKU supplier feed.
 * - generic, thread-safe Interner<T> for immutable values (records, Strings)
 * - a cap on distinct values: a bad feed with unique junk can't grow the cache forever (falls back to no sharing)
 * - metrics: lookups, hits, distinct values, rejected (over cap) - so you can prove the win
 * - only immutable types allowed: mutable flyweights are a shared-state bug waiting to happen
 */
public class CatalogInternerDemo {

    static final class Interner<T> {
        private final Map<T, T> pool = new ConcurrentHashMap<>();
        private final int maxDistinct;
        final LongAdder lookups = new LongAdder(), hits = new LongAdder(), overCap = new LongAdder();

        Interner(Class<T> type, int maxDistinct) {
            if (!(type.isRecord() || type == String.class))
                throw new IllegalArgumentException(type.getSimpleName() + " is not a known immutable type; refuse to share it");
            this.maxDistinct = maxDistinct;
        }

        T intern(T value) {
            lookups.increment();
            T existing = pool.get(value);
            if (existing != null) { hits.increment(); return existing; }
            if (pool.size() >= maxDistinct) { overCap.increment(); return value; }
            T winner = pool.putIfAbsent(value, value);
            if (winner != null) { hits.increment(); return winner; }
            return value;
        }

        String stats() {
            return String.format("lookups=%,d hits=%,d distinct=%d overCap=%,d hitRate=%.2f%%",
                    lookups.sum(), hits.sum(), pool.size(), overCap.sum(), 100.0 * hits.sum() / Math.max(1, lookups.sum()));
        }
    }

    record Attributes(String brand, String unit, String category, int gstPercent) { }
    record Sku(String code, String name, long paise, Attributes attrs) { }

    static List<Sku> load(int count, Interner<Attributes> interner, boolean junk) {
        String[] brands = {"GreenValley", "DairyDay", "FarmFresh", "DailyBake"};
        String[] units = {"kg", "L", "pcs"};
        String[] cats = {"Fruit", "Dairy", "Bakery", "Staples", "Snacks"};
        List<Sku> skus = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String brand = junk ? "Brand-" + i : brands[i % brands.length];                     // a broken feed: every row unique
            Attributes a = new Attributes(new String(brand), units[i % 3], cats[i % 5], i % 2 == 0 ? 5 : 12);   // fresh objects, like a parser makes
            skus.add(new Sku("SKU-" + i, "Item " + i, 1000 + i % 500, interner.intern(a)));
        }
        return skus;
    }

    public static void main(String[] args) {
        Interner<Attributes> attrs = new Interner<>(Attributes.class, 10_000);
        List<Sku> catalog = load(200_000, attrs, false);
        System.out.println("good feed: " + attrs.stats());
        System.out.println("rows 0 and 60 share attributes: " + (catalog.get(0).attrs() == catalog.get(60).attrs()));

        Interner<Attributes> guarded = new Interner<>(Attributes.class, 10_000);
        load(50_000, guarded, true);
        System.out.println("junk feed: " + guarded.stats());

        try { new Interner<>(StringBuilder.class, 10); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
```

1. Only records and `String`s are accepted: sharing a mutable object would leak changes between SKUs.
2. Fast path: an existing equal value is returned (a hit).
3. Over the cap, the interner stops sharing instead of growing forever; the feed still loads.
4. `putIfAbsent` makes concurrent first insertions agree on one winner.
5. Metrics show the hit rate and how often the cap was hit.

**Run it:** `java CatalogInternerDemo.java`

**Output:**

```text
good feed: lookups=200,000 hits=199,940 distinct=60 overCap=0 hitRate=99.97%
rows 0 and 60 share attributes: true
junk feed: lookups=50,000 hits=0 distinct=10000 overCap=40,000 hitRate=0.00%
StringBuilder is not a known immutable type; refuse to share it
```

**What to notice:**

- 200,000 rows shared just 60 attribute objects (99.97% hits).
- The junk feed stopped at 10,000 cached values; the other 40,000 were used unshared, and memory stayed bounded.
- `StringBuilder` was rejected before it could become a shared mutable object.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Memory | Duplicates stored once | No gain when shared data is small |
| GC | Smaller live heap, shorter GC work | Long-lived caches stay in old generation |
| Concurrency | Immutable flyweights are thread-safe | Factory must be thread-safe |
| Design | Explicit split of shared and per-use data | Extra parameters and indirection |
| Correctness | — | Mutable "flyweights" leak state; `==` confusion |
| Operations | Metrics show the win | Unbounded caches can leak memory |

## 8. When to Use / When NOT to Use

**Use it when:**

- the application holds very many objects (tens of thousands or more);
- most of each object's data is repeated across objects and can be immutable;
- measurements show memory or GC pressure from those duplicates.

**Don't use it when:**

- the object count is small, or the shared part is a few small fields;
- the "shared" data needs to change per object;
- you haven't measured; modern JVMs allocate short-lived objects very cheaply.

## 9. Best Practices

1. **Measure first** (heap histogram, MAT duplicate report). *(Consensus.)*
2. **Make flyweights immutable records** with private creation through a factory. *(Consensus.)*
3. **Use `computeIfAbsent` or `putIfAbsent`** for thread-safe factories. *(Consensus.)*
4. **Bound caches keyed by external input.** *(Consensus.)*
5. **Compare values with `equals`**, never `==`, even when values are interned. *(Consensus.)*
6. **Prefer enums** for small fixed sets of shared values. *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Mutable flyweight (production failure mode)**  
> **Symptom:** after a promotion on one product, dozens of unrelated products showed the promo price.
> **Root cause:** shared `PriceTier` objects had a `discountPercent` field; a promotion set it on the shared instance.
> **Fix:** make the tier an immutable record; keep per-product overrides in the product (extrinsic state).

> 🚨 **`==` on boxed IDs**  
> **Symptom:** `order.getStoreId() == selectedStoreId` worked in testing and failed for stores above 127 in production.
> **Root cause:** `Integer` cache covers only -128..127; larger values are different objects.
> **Fix:** `Objects.equals` or primitive `int`; static analysis rule.

> 🚨 **Unbounded interner**  
> **Symptom:** a service's heap grew steadily until `OutOfMemoryError` after a few days.
> **Root cause:** search queries were interned "to save memory"; every unique query stayed forever.
> **Fix:** intern only low-cardinality values; cap or use weak references.

> 🚨 **Flyweight for tiny objects**  
> **Symptom:** more complex code, no measurable memory change.
> **Root cause:** sharing objects with two small fields; each reference costs about the same as the data.
> **Fix:** remove the indirection.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `-XX:AutoBoxCacheMax` | 127 | Raises `Integer` cache upper bound | Hot code boxing larger ints | More permanent objects; doesn't fix `==` bugs |
| `-XX:+UseStringDeduplication` | off | G1/ZGC dedupe equal `String` backing arrays | Heaps full of duplicate strings | Some GC CPU |
| `-XX:StringTableSize` | ~65,536 buckets | String pool hash table size | Heavy `intern()` use | Memory for the table |
| Interner cap | your choice | Max distinct shared values | Per data cardinality | Too low: fewer savings |

## 12. Security Considerations

- Caches keyed by user input are a denial-of-service vector (memory exhaustion); cap them.
- Never put per-user data (names, tokens, addresses) in a shared flyweight.
- `String.intern()` on attacker-controlled strings fills the global pool for the life of the JVM.

## 13. Performance & Scalability

Benefits: smaller heaps, fewer cache misses when scanning large collections, and less GC work. Costs: a hash lookup per creation (cheap with `ConcurrentHashMap`), an extra pointer hop per access, and the cache itself staying live forever. For comparison: a `byte[2048]` thumbnail duplicated 10,000 times is about 20 MB; shared across 5 products it is 10 KB. JDK features already do much of this automatically: compact strings (Java 9) halve Latin-1 string memory, and G1/ZGC can deduplicate strings.

## 14. Testing It

Test identity (same instance for equal values), the cap, concurrency and the immutability guard:

**InternerTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InternerTest {

    record Unit(String code) { }

    @Test
    void equalValuesBecomeTheSameInstance() {
        var interner = new CatalogInternerDemo.Interner<>(Unit.class, 10);
        Unit first = interner.intern(new Unit("KG"));
        assertSame(first, interner.intern(new Unit("KG")));
    }

    @Test
    void stopsSharingAboveTheCap() {
        var interner = new CatalogInternerDemo.Interner<>(Unit.class, 1);
        interner.intern(new Unit("KG"));
        Unit l1 = interner.intern(new Unit("L"));
        assertNotSame(l1, interner.intern(new Unit("L")));
        assertEquals(2, interner.overCap.sum());
    }

    @Test
    void concurrentCallersAllGetOneInstance() {
        var interner = new CatalogInternerDemo.Interner<>(Unit.class, 10);
        var seen = ConcurrentHashMap.<Unit>newKeySet();
        IntStream.range(0, 10_000).parallel().forEach(i -> seen.add(interner.intern(new Unit("KG"))));
        assertEquals(1, seen.stream().map(System::identityHashCode).distinct().count());
    }

    @Test
    void refusesMutableTypes() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogInternerDemo.Interner<>(StringBuilder.class, 10));
    }
}
```

## 15. Observability & Operations

Export interner metrics (distinct values, hit rate, over-cap count) and alert on a falling hit rate or a rising distinct count: both point at a data change upstream. In production, `jcmd <pid> GC.class_histogram` shows whether instance counts of the shared type stay flat, and heap dumps analysed with MAT show remaining duplicates.

## 16. Version Notes

| Java | Change relevant to Flyweight |
|------|------------------------------|
| 5 | Autoboxing and `Integer.valueOf` cache |
| 7 | String pool moved from PermGen to the main heap |
| 8u20 | G1 string deduplication |
| 9 | Compact strings (JEP 254) |
| 16 | Records (immutable flyweights); wrapper constructors deprecated for removal |
| 18 | String deduplication available in Serial, Parallel, ZGC and Shenandoah, not only G1 |

## 17. Expert Deep Dive

- **Value classes (Project Valhalla).** Value objects without identity will let the JVM flatten small immutable objects into arrays and fields, removing headers and pointers. Flyweight-style identity sharing will matter less for small values and remain useful for heavy shared data.
- **Flyweight vs caching vs pooling.** Flyweights are **shared simultaneously** and immutable; pooled objects ([Object Pool](object-pool.md)) are **lent exclusively** and reset; caches store **results** that may expire. Mixing these concepts causes bugs.
- **Columnar thinking.** At very large scales, the next step after flyweights is storing extrinsic state in primitive arrays (structure of arrays) and intrinsic state in small lookup tables, as analytics engines and game engines do.
- **Interning in data pipelines.** Parsers (Jackson's `InternCache` for field names) intern low-cardinality strings automatically; apply the same idea to enum-like columns when loading large feeds.

> 📖 **War story: the 6 GB catalogue**  
> A grocery platform's search service loaded its 1.2 million-SKU catalogue into memory and needed a 6 GB heap. A heap dump showed 70% of the memory was duplicate strings and small objects: brand names, units, category paths and tax codes, each parsed fresh for every SKU. Interning attribute records (about 4,000 distinct combinations) and enabling G1 string deduplication brought the heap to 1.8 GB, and GC pauses dropped from around 400 ms to under 80 ms. Months later, a supplier feed began sending unique "brand" values (timestamps appended by a bug), and the uncapped interner grew until the service crashed; a cap and a distinct-count alert were added. **Lesson:** measure the win, and guard the cache against bad data.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What is Flyweight?** Sharing **immutable common state** across many objects to save memory.
2. **Intrinsic vs extrinsic state?** Intrinsic is **shared and context-free**; extrinsic is **per use**, kept outside or passed in.
3. **Give a JDK example.** **`Integer.valueOf`** cache, **String pool**.
4. **Why must flyweights be immutable?** They're **shared**; a change would affect every user.
5. **Why is `Integer a = 128, b = 128; a == b` false?** The cache covers only **-128..127**.

**▸ 🟡 L3**

1. **How do you build a thread-safe flyweight factory?** **`ConcurrentHashMap.computeIfAbsent`**.
2. **Flyweight vs Object Pool?** Flyweights are **shared at once and immutable**; pooled objects are **borrowed exclusively**.
3. **How do you know a flyweight is worth it?** **Heap histogram, MAT duplicates**, before/after measurements.
4. **What's G1 string deduplication?** GC **merges equal strings' backing arrays** automatically.
5. **Risks of `String.intern()`?** **Unbounded growth** from high-cardinality or attacker-controlled strings.

**▸ 🔴 L4–L5**

1. **A service needs 6 GB for a catalogue. How do you investigate?** **Heap dump, dominator tree, duplicate strings**; intern low-cardinality attributes; consider off-heap or columnar layouts.
2. **How do you protect a shared cache from bad input?** **Cap distinct values**, weak references, metrics and alerts.
3. **How will Valhalla value classes change this?** **Flattened, identity-free** small values; less need to share tiny objects.
4. **When would you use weak interners?** When values come and go and should be **reclaimed when unused**.
5. **How does compressed oops affect the trade-off?** References are **4 bytes**, so sharing tiny objects saves little.

**▸ 💻 Hands-on**

**Task:** Make `MarkerStyles` reject unknown combinations from a configuration file instead of creating them.

> ✅ **Solution**  
> ```java
> private final Set<String> allowed;    // loaded from config at startup
> MarkerStyle get(Status s, Vehicle v) {
>     String key = s + "/" + v;
>     if (!allowed.contains(key)) throw new IllegalArgumentException("no marker style for " + key);
>     return cache.computeIfAbsent(key, k -> createStyle(s, v));
> }
> ```

## 19. Practice

- [ ] Use JOL to measure the deep size of `IntrinsicExtrinsicDemo`'s two lists.
    > 💡 **Hint**  
    > `GraphLayout.parseInstance(list).totalSize()` from `org.openjdk.jol:jol-core`.

- [ ] Run a service with `-XX:+UseG1GC -XX:+UseStringDeduplication` and compare heap after GC.
    > 💡 **Hint**  
    > Use `-Xlog:stringdedup*=debug` to see deduplication statistics.

- [ ] Replace `CatalogInternerDemo`'s cap with Caffeine's `maximumSize` and weak values.
    > 💡 **Hint**  
    > `Caffeine.newBuilder().maximumSize(10_000).weakValues().build()`.

- [ ] Find `==` comparisons on `Integer`, `Long` or `String` in a codebase.
    > 💡 **Hint**  
    > Error Prone's `ReferenceEquality` check finds many of them.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: share immutable common (intrinsic) state among many objects; keep per-use (extrinsic) state outside.
> - Factory: one instance per key (`computeIfAbsent`); bound caches fed by external data.
> - JDK flyweights: `Integer.valueOf` (-128..127), `Boolean.TRUE/FALSE`, String pool.
> - Always `equals`, never `==`, for boxed values and strings.
> - Measure first; flyweights for tiny objects save almost nothing.
>
> ```java
> record MarkerStyle(String colour, String icon) { }
> static final Map<String, MarkerStyle> STYLES = new ConcurrentHashMap<>();
> static MarkerStyle style(String key) {
>     return STYLES.computeIfAbsent(key, MarkerStyles::create);
> }
> ```

## 21. Further Reading

- Gamma, Helm, Johnson, Vlissides, *Design Patterns* (1994), "Flyweight".
- [Integer.valueOf API documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html#valueOf(int))
- [JEP 254: Compact Strings](https://openjdk.org/jeps/254)
- [JOL: Java Object Layout](https://github.com/openjdk/jol)
- [Eclipse Memory Analyzer (MAT)](https://eclipse.dev/mat/)

---

[← Previous: Bridge](bridge.md) · [Next: Command →](command.md)
