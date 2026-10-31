# Bridge

L2 🔵 Practitioner · ⏱ 35 min read · Prerequisites: [Adapter](../l1-foundations/adapter.md), [Strategy](../l1-foundations/strategy.md), [Composition over inheritance](../l1-foundations/other-design-principles.md)

> 📌 **TL;DR**  
> Bridge splits one class hierarchy that varies in **two independent ways** into two hierarchies connected by a reference: the **abstraction** (what you do: order update, promo, report) holds an **implementor** (how it's done: SMS, push, CSV, S3). Each side grows on its own, so m kinds × n back ends need m + n classes instead of m × n. JDBC is the classic Java example: your code uses `Connection`, each vendor ships a `Driver`.

## 1. What It Is

**Plain English.** FreshCart sends several kinds of messages (order updates, promotions, OTP codes) through several channels (SMS, push, email). If each combination were its own class (`SmsOrderUpdate`, `PushPromo`, ...), adding WhatsApp would mean writing a class for every message kind. Bridge separates "what message" from "which channel", and the message simply holds a channel.

**Definition (GoF).** "Decouple an abstraction from its implementation so that the two can vary independently." Parts:

- **Abstraction:** the high-level interface clients use (`Notification`), holding a reference to an implementor.
- **Refined abstraction:** variants of the abstraction (`OrderUpdate`, `PromoMessage`).
- **Implementor:** a lower-level interface for the "how" (`Channel`), usually smaller and more primitive.
- **Concrete implementors:** the platform- or vendor-specific code (`Sms`, `Push`, `Email`).

"Implementation" here doesn't mean "the class that implements an interface"; it means the platform-specific side that the abstraction delegates to.

## 2. Why It Exists

Inheritance handles one dimension of variation well. With two dimensions, subclassing multiplies: every new message kind needs a class per channel, and every new channel needs a class per message kind. Code is duplicated across combinations, and changing how SMS works means editing many classes. Bridge turns one dimension into a field (composition), so the two dimensions can change, be tested and be deployed separately, and the implementor can even be chosen at runtime from configuration.

## 3. Mental Model / Analogy

**Analogy.** A TV remote and a TV. Remotes vary (basic, universal, voice); TVs vary (brands, models). Any remote works with any TV through a small agreed protocol (power, volume, channel). Remote makers and TV makers innovate independently; you don't buy a "basic-remote-for-brand-X-TV".

**Technical model.** Two interfaces: the abstraction's rich, domain-level API and the implementor's small, primitive API. The abstraction's methods are built from implementor calls. The link is a constructor-injected field, so the pairing is chosen at the composition root.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Abstraction and implementor

**Description.** The abstraction is what client code talks to; it holds an implementor and builds its behaviour from the implementor's primitive operations. Refined abstractions add variants on the "what" side; concrete implementors add variants on the "how" side. The bridge is the field that connects them.

**Advantages / features.** Either side can be extended without touching the other; the pairing is chosen at runtime; each side can be tested on its own.

**AbstractionImplementorDemo.java**

```java
/** Bridge: WHAT we send (abstraction) and HOW it's delivered (implementor) vary independently. */
public class AbstractionImplementorDemo {

    // ===================== Implementor side: how a message travels =====================
    interface Channel { String deliver(String to, String title, String body); }
    record Sms() implements Channel { public String deliver(String to, String t, String b) { return "SMS to " + to + ": " + t + " - " + b; } }
    record Push() implements Channel { public String deliver(String to, String t, String b) { return "PUSH to device of " + to + " [" + t + "] " + b; } }
    record Email() implements Channel { public String deliver(String to, String t, String b) { return "EMAIL to " + to + "@mail, subject '" + t + "'"; } }

    // ===================== Abstraction side: what kind of message =====================
    abstract static class Notification {
        protected final Channel channel;
        Notification(Channel channel) { this.channel = channel; }
        abstract String send(String customer);
    }
    static final class OrderUpdate extends Notification {
        private final String orderId, status;
        OrderUpdate(Channel c, String orderId, String status) { super(c); this.orderId = orderId; this.status = status; }
        String send(String customer) { return channel.deliver(customer, "Order " + orderId, status); }
    }
    static final class PromoMessage extends Notification {
        private final int percent;
        PromoMessage(Channel c, int percent) { super(c); this.percent = percent; }
        String send(String customer) { return channel.deliver(customer, percent + "% off fruit", "today only"); }
    }

    public static void main(String[] args) {
        System.out.println(new OrderUpdate(new Sms(), "FC-5001", "out for delivery").send("asha"));
        System.out.println(new OrderUpdate(new Push(), "FC-5001", "delivered").send("asha"));
        System.out.println(new PromoMessage(new Email(), 20).send("ravi"));
        System.out.println(new PromoMessage(new Push(), 20).send("ravi"));
    }
}
```

1. The implementor interface is small and knows nothing about orders or promotions.
2. The abstraction holds the implementor: that field is the bridge.
3. A refined abstraction decides *what* to say and leaves *how* to the channel.

**Run it:** `java AbstractionImplementorDemo.java`

```text
SMS to asha: Order FC-5001 - out for delivery
PUSH to device of asha [Order FC-5001] delivered
EMAIL to ravi@mail, subject '20% off fruit'
PUSH to device of ravi [20% off fruit] today only
```

| Pros | Cons |
|------|------|
| Two dimensions vary independently | Two hierarchies to understand instead of one |
| Implementor chosen at runtime | Designing a good implementor interface is hard |
| Platform code isolated from domain code | Overkill when there's only one dimension |

**Use it** when a concept varies along two axes that change for different reasons (business kinds vs technical back ends). **Avoid** it when one of the axes has only one member and no realistic second.
**Real-world Java:** JDBC (`Connection` vs `Driver`), SLF4J (logging API vs Logback/Log4j binding), AWT peers (`java.awt.Button` vs the native `ButtonPeer`), Spring's `PlatformTransactionManager` behind `TransactionTemplate`.
**Best practice:** make the implementor interface primitive (deliver bytes, store blobs) and keep policies in the abstraction. **Gotcha:** if the implementor interface mirrors the abstraction method for method, you've built an Adapter or a pass-through, not a bridge.

### Avoiding class explosion

**Description.** The main motivation. Combining two dimensions by inheritance needs m × n classes; a bridge needs m + n. The gap grows with every addition.

**Advantages / features.** Adding a channel is one class; adding a message kind is one class; no duplicated code across combinations.

**ClassExplosionDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** Why Bridge exists: two dimensions in one inheritance tree multiply classes; a bridge adds them instead. */
public class ClassExplosionDemo {

    static List<String> inheritanceClasses(List<String> kinds, List<String> channels) {
        List<String> classes = new ArrayList<>();
        for (String k : kinds) for (String c : channels) classes.add(c + k);
        return classes;
    }

    static List<String> bridgeClasses(List<String> kinds, List<String> channels) {
        List<String> classes = new ArrayList<>(kinds);
        classes.addAll(channels);
        return classes;
    }

    public static void main(String[] args) {
        List<String> kinds = new ArrayList<>(List.of("OrderUpdate", "Promo", "OtpCode"));
        List<String> channels = new ArrayList<>(List.of("Sms", "Push", "Email"));
        System.out.println("inheritance: " + inheritanceClasses(kinds, channels).size() + " classes, e.g. " + inheritanceClasses(kinds, channels).subList(0, 3));
        System.out.println("bridge:      " + bridgeClasses(kinds, channels).size() + " classes " + bridgeClasses(kinds, channels));

        channels.add("WhatsApp"); kinds.add("RefundNotice");
        System.out.println("after adding WhatsApp and RefundNotice:");
        System.out.println("inheritance: " + inheritanceClasses(kinds, channels).size() + " classes (m x n)");
        System.out.println("bridge:      " + bridgeClasses(kinds, channels).size() + " classes (m + n)");
    }
}
```

1. Inheritance: one class per (kind, channel) pair.
2. Bridge: one class per kind plus one per channel.
3. Adding one channel and one kind: 16 classes versus 8.

**Run it:** `java ClassExplosionDemo.java`

```text
inheritance: 9 classes, e.g. [SmsOrderUpdate, PushOrderUpdate, EmailOrderUpdate]
bridge:      6 classes [OrderUpdate, Promo, OtpCode, Sms, Push, Email]
after adding WhatsApp and RefundNotice:
inheritance: 16 classes (m x n)
bridge:      8 classes (m + n)
```

| Growth | Inheritance (m × n) | Bridge (m + n) |
|--------|---------------------|----------------|
| 3 kinds, 3 channels | 9 | 6 |
| 4 kinds, 4 channels | 16 | 8 |
| 10 kinds, 5 channels | 50 | 15 |

**Use** this test: "if I add one more X, how many classes do I touch?" If the answer is "one per Y", consider a bridge. **Avoid** creating the second hierarchy speculatively; wait for the second real member.
**Real-world Java:** without SLF4J's bridge, every library would need a version per logging framework.
**Best practice:** name classes by one dimension only (`Push`, `OrderUpdate`); a class name with both (`PushOrderUpdate`) is the smell. **Gotcha:** a third dimension (language, brand) can be a second bridge field, but each extra bridge adds wiring; keep it to what really varies.

### Bridge vs. Adapter vs. Strategy

**Description.** All three hold a reference to an interface and delegate to it, so the code looks similar. The difference is intent and timing. **Adapter** makes an existing, incompatible API fit, after the fact. **Strategy** swaps one algorithm inside one object. **Bridge** is designed up front so two whole hierarchies can grow independently.

**Advantages / features (of knowing the difference).** You name classes correctly and choose the right shape for the problem.

**BridgeVsAdapterStrategyDemo.java**

```java
/** Three patterns with the same shape (an object holding an interface) and different intents. */
public class BridgeVsAdapterStrategyDemo {

    // ===================== Adapter: make an EXISTING, incompatible API fit after the fact =====================
    static final class LegacyTextBlastApi { int sendMessage(String msisdn, String payload, int priority) { return 202; } }
    interface SmsSender { boolean send(String phone, String text); }
    record TextBlastAdapter(LegacyTextBlastApi api) implements SmsSender {
        public boolean send(String phone, String text) { return api.sendMessage(phone, text, 1) == 202; }
    }

    // ===================== Strategy: swap ONE algorithm inside one object =====================
    interface FeeRule { long feePaise(long orderPaise); }
    record Checkout(FeeRule rule) {
        long total(long orderPaise) { return orderPaise + rule.feePaise(orderPaise); }
    }

    // ===================== Bridge: TWO hierarchies designed up front to vary independently =====================
    interface Renderer { String render(String title, String body); }
    record Plain() implements Renderer { public String render(String t, String b) { return t + ": " + b; } }
    record Html() implements Renderer { public String render(String t, String b) { return "<h1>" + t + "</h1><p>" + b + "</p>"; } }
    abstract static class Page { final Renderer r; Page(Renderer r) { this.r = r; } abstract String show(); }
    static final class OrderPage extends Page { OrderPage(Renderer r) { super(r); } String show() { return r.render("Order FC-5101", "3 items"); } }
    static final class HelpPage extends Page { HelpPage(Renderer r) { super(r); } String show() { return r.render("Help", "call 1800-000"); } }

    public static void main(String[] args) {
        System.out.println("adapter:  sent = " + new TextBlastAdapter(new LegacyTextBlastApi()).send("+9198", "hi"));
        System.out.println("strategy: " + new Checkout(o -> o >= 50_000 ? 0 : 2_500).total(30_000) + " / " + new Checkout(o -> 1_000).total(30_000));
        System.out.println("bridge:   " + new OrderPage(new Plain()).show() + " | " + new HelpPage(new Html()).show());
    }
}
```

1. Adapter: translates our `SmsSender` call into the legacy API's different method and return code.
2. Strategy: one class (`Checkout`) with one swappable rule.
3. Bridge: a family of pages on one side, a family of renderers on the other.

**Run it:** `java BridgeVsAdapterStrategyDemo.java`

```text
adapter:  sent = true
strategy: 32500 / 31000
bridge:   Order FC-5101: 3 items | <h1>Help</h1><p>call 1800-000</p>
```

| | Adapter | Strategy | Bridge |
|---|---------|----------|--------|
| Intent | Make incompatible interfaces work together | Swap an algorithm | Let two hierarchies vary independently |
| When designed | After the fact | Up front | Up front |
| Hierarchies | One (wrapping one class) | One context, many strategies | Two, both extensible |
| Typical size | One method mapping | One method | Several methods on each side |

**Use** the name that matches your intent; reviewers read it as documentation. **Avoid** arguing about labels when the code is simple; the shape is what matters.
**Real-world Java:** Adapter: `InputStreamReader`; Strategy: `Comparator`; Bridge: JDBC, SLF4J.
**Best practice:** a Bridge implementor often ends up being created via an Adapter around a vendor SDK; that's normal. **Gotcha:** a Strategy that grows its own subclass hierarchy on the "context" side has become a Bridge; refactor names to reflect that.

### JDBC as a bridge

**Description.** JDBC separates the API your code uses (`DriverManager`/`DataSource`, `Connection`, `PreparedStatement`, `ResultSet`) from vendor drivers that implement it. `DriverManager` picks a driver by URL (`jdbc:postgresql:...`), and since JDBC 4.0 drivers register themselves via `ServiceLoader`. Your repository code doesn't change when you switch database vendors (as long as the SQL is portable).

**Advantages / features.** Database vendors and application developers evolve separately; tests can run on an embedded database; one connection pool works with every driver.

**JdbcBridgeDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC is the textbook Bridge. Your code uses the abstraction (DriverManager, Connection, Statement);
 * each database vendor ships an implementor (a Driver). Mini version below; real JDBC works the same way.
 */
public class JdbcBridgeDemo {

    // ===================== Implementor interface (what vendors implement) =====================
    interface Driver { boolean acceptsUrl(String url); Connection connect(String url); }
    interface Connection { List<String> query(String sql); }

    // ===================== Abstraction (what application code uses) =====================
    static final class DriverManager {
        private static final List<Driver> DRIVERS = new ArrayList<>();
        static void registerDriver(Driver d) { DRIVERS.add(d); }
        static Connection getConnection(String url) {
            return DRIVERS.stream().filter(d -> d.acceptsUrl(url)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No suitable driver found for " + url)).connect(url);
        }
    }

    // ===================== Two "vendors" =====================
    static final class MemDbDriver implements Driver {
        public boolean acceptsUrl(String url) { return url.startsWith("jdbc:memdb:"); }
        public Connection connect(String url) { return sql -> List.of("memdb row for '" + sql + "'"); }
    }
    static final class CsvDriver implements Driver {
        final Map<String, List<String>> files = new LinkedHashMap<>(Map.of("stock", List.of("MILK,40", "BREAD,12")));
        public boolean acceptsUrl(String url) { return url.startsWith("jdbc:csv:"); }
        public Connection connect(String url) { return sql -> files.getOrDefault(sql.substring(sql.lastIndexOf(' ') + 1), List.of()); }
    }

    // ===================== Application code: depends only on the abstraction =====================
    static List<String> loadStock(String url) {
        Connection c = DriverManager.getConnection(url);
        return c.query("SELECT * FROM stock");
    }

    public static void main(String[] args) {
        DriverManager.registerDriver(new MemDbDriver());
        DriverManager.registerDriver(new CsvDriver());
        System.out.println("memdb: " + loadStock("jdbc:memdb:freshcart"));
        System.out.println("csv:   " + loadStock("jdbc:csv:/data/freshcart"));
        try { loadStock("jdbc:otherdb://reports-db"); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
    }
}
```

1. The implementor interfaces that vendors implement.
2. The abstraction that application code calls; it chooses a driver from the URL.
3. Application code depends only on the abstraction.
4. Registration: real drivers do this automatically through `META-INF/services/java.sql.Driver`.

**Run it:** `java JdbcBridgeDemo.java`

```text
memdb: [memdb row for 'SELECT * FROM stock']
csv:   [MILK,40, BREAD,12]
No suitable driver found for jdbc:otherdb://reports-db
```

| Pros | Cons |
|------|------|
| Switch databases by URL and driver jar | SQL dialects still leak through the abstraction |
| Pools (HikariCP) work with any driver | Lowest-common-denominator API; vendor features need `unwrap` |
| Embedded databases for tests | Driver bugs surface as generic `SQLException`s |

**Use** the JDBC idea for your own pluggable back ends (storage, payments, search). **Avoid** assuming the abstraction hides everything; test on the real vendor too (Testcontainers).
**Real-world Java:** `java.sql.Driver`, `javax.sql.DataSource`, R2DBC (reactive equivalent), JPA providers behind the JPA API.
**Best practice:** depend on `DataSource` (injected) rather than `DriverManager` in applications. **Gotcha:** "No suitable driver found" usually means the driver jar isn't on the runtime classpath or the URL prefix is mistyped.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** receipts come in two types (short, detailed) and two formats (plain text for the thermal printer, HTML for email).

**ReceiptPrinterDemo.java**

```java
import java.util.List;

/** Minimal Bridge: receipt TYPE (short or detailed) x receipt FORMAT (plain text or HTML). */
public class ReceiptPrinterDemo {

    interface Format { String line(String label, String value); String wrap(String content); }
    record PlainText() implements Format {
        public String line(String l, String v) { return String.format("%-10s %8s%n", l, v); }
        public String wrap(String c) { return c.stripTrailing(); }
    }
    record Html() implements Format {
        public String line(String l, String v) { return "<tr><td>" + l + "</td><td>" + v + "</td></tr>"; }
        public String wrap(String c) { return "<table>" + c + "</table>"; }
    }

    record Item(String name, long paise) { }

    abstract static class Receipt {
        final Format f; Receipt(Format f) { this.f = f; }
        abstract String print(List<Item> items);
        static String rs(long p) { return String.format("%.2f", p / 100.0); }
    }
    static final class ShortReceipt extends Receipt {
        ShortReceipt(Format f) { super(f); }
        String print(List<Item> items) { return f.wrap(f.line("TOTAL", rs(items.stream().mapToLong(Item::paise).sum()))); }
    }
    static final class DetailedReceipt extends Receipt {
        DetailedReceipt(Format f) { super(f); }
        String print(List<Item> items) {
            StringBuilder sb = new StringBuilder();
            items.forEach(i -> sb.append(f.line(i.name(), rs(i.paise()))));
            sb.append(f.line("TOTAL", rs(items.stream().mapToLong(Item::paise).sum())));
            return f.wrap(sb.toString());
        }
    }

    public static void main(String[] args) {
        List<Item> items = List.of(new Item("Milk", 6800), new Item("Bread", 4500));
        System.out.println(new DetailedReceipt(new PlainText()).print(items));
        System.out.println(new ShortReceipt(new Html()).print(items));
    }
}
```

1. The implementor: how a line and the whole receipt are formatted.
2. The abstraction: which lines a receipt contains; it holds a format.

**Run it:** `java ReceiptPrinterDemo.java`

**Output:**

```text
Milk          68.00
Bread         45.00
TOTAL        113.00
<table><tr><td>TOTAL</td><td>113.00</td></tr></table>
```

**What to notice:**

- Four combinations from four small classes; a PDF format would be one more class.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the operations team gets daily reports (sales per store, low stock) and wants them as CSV for spreadsheets and JSON for the dashboard. New reports and new formats keep arriving, from different people.

**ReportExportDemo.java**

```java
import java.util.List;
import java.util.Map;

/** Realistic Bridge: FreshCart's ops reports (what data) x export formats (how it is written). New report or new format = one class. */
public class ReportExportDemo {

    // ===================== Implementor: how rows are written =====================
    interface Exporter {
        String export(String title, List<String> columns, List<List<Object>> rows);
    }
    record CsvExporter() implements Exporter {
        public String export(String t, List<String> cols, List<List<Object>> rows) {
            StringBuilder sb = new StringBuilder(String.join(",", cols));
            rows.forEach(r -> sb.append("\n").append(String.join(",", r.stream().map(ReportExportDemo::csv).toList())));
            return sb.toString();
        }
    }
    record JsonExporter() implements Exporter {
        public String export(String t, List<String> cols, List<List<Object>> rows) {
            StringBuilder sb = new StringBuilder("{\"report\":\"" + t + "\",\"rows\":[");
            for (int i = 0; i < rows.size(); i++) {
                sb.append(i == 0 ? "{" : ",{");
                for (int c = 0; c < cols.size(); c++) {
                    Object v = rows.get(i).get(c);
                    sb.append(c == 0 ? "" : ",").append('"').append(cols.get(c)).append("\":").append(v instanceof Number ? v : "\"" + v + "\"");
                }
                sb.append("}");
            }
            return sb.append("]}").toString();
        }
    }
    static String csv(Object v) { String s = String.valueOf(v); return s.contains(",") ? "\"" + s + "\"" : s; }

    // ===================== Abstraction: which data =====================
    abstract static class Report {
        protected final Exporter exporter;
        Report(Exporter e) { exporter = e; }
        abstract String generate();
    }
    static final class DailySalesReport extends Report {
        private final Map<String, Long> salesByStore;
        DailySalesReport(Exporter e, Map<String, Long> s) { super(e); salesByStore = s; }
        String generate() {
            List<List<Object>> rows = salesByStore.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(en -> List.<Object>of(en.getKey(), en.getValue() / 100)).toList();
            return exporter.export("daily-sales", List.of("store", "rupees"), rows);
        }
    }
    static final class LowStockReport extends Report {
        private final Map<String, Integer> stock; private final int threshold;
        LowStockReport(Exporter e, Map<String, Integer> s, int threshold) { super(e); stock = s; this.threshold = threshold; }
        String generate() {
            List<List<Object>> rows = stock.entrySet().stream().filter(en -> en.getValue() < threshold).sorted(Map.Entry.comparingByKey())
                    .map(en -> List.<Object>of(en.getKey(), en.getValue())).toList();
            return exporter.export("low-stock", List.of("sku", "units"), rows);
        }
    }

    public static void main(String[] args) {
        Map<String, Long> sales = Map.of("Koramangala", 18_45_000_00L, "Indiranagar, 2nd stage", 12_10_500_00L);
        Map<String, Integer> stock = Map.of("MILK-1L", 4, "BREAD", 30, "EGGS-6", 2);
        System.out.println(new DailySalesReport(new CsvExporter(), sales).generate());
        System.out.println(new DailySalesReport(new JsonExporter(), sales).generate());
        System.out.println(new LowStockReport(new CsvExporter(), stock, 5).generate());
    }
}
```

1. The implementor writes rows; it knows nothing about sales or stock.
2. Format rules such as quoting values with commas live in the exporter, once.
3. A new report only decides which rows and columns to produce.

**Run it:** `java ReportExportDemo.java`

**Output:**

```text
store,rupees
"Indiranagar, 2nd stage",1210500
Koramangala,1845000
{"report":"daily-sales","rows":[{"store":"Indiranagar, 2nd stage","rupees":1210500},{"store":"Koramangala","rupees":1845000}]}
sku,units
EGGS-6,2
MILK-1L,4
```

**What to notice:**

- "Indiranagar, 2nd stage" was quoted correctly in CSV, for every report, because quoting lives on the format side.
- Report authors and format authors never edit the same class.

### Example 3: Production-grade

**Scenario:** invoices must be archived for 8 years. Developers use an in-memory store; production uses an object store that sometimes times out. Requirements: the back end is chosen from a config URL; retries, key naming and checksums are written once (in the abstraction) for every back end; a finance-audited variant records every read and write; tampered or corrupted invoices are detected on read.

**InvoiceArchiveDemo.java**

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Production-grade Bridge: invoice archiving (abstraction) over blob storage back ends (implementor).
 * - the implementor interface is SMALL and primitive (put/get bytes); policies live in the abstraction
 * - the back end is chosen from a config URL at the composition root (like JDBC URLs)
 * - the abstraction adds retries, checksums and naming rules ONCE for every back end
 * - a refined abstraction (AuditedInvoiceArchive) adds behaviour without touching any back end
 */
public class InvoiceArchiveDemo {

    // ===================== Implementor: primitive storage operations =====================
    interface BlobStore {
        void put(String key, byte[] data);
        Optional<byte[]> get(String key);
        String name();
    }

    static final class InMemoryBlobStore implements BlobStore {
        final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
        public void put(String k, byte[] d) { blobs.put(k, d.clone()); }
        public Optional<byte[]> get(String k) { return Optional.ofNullable(blobs.get(k)).map(byte[]::clone); }
        public String name() { return "memory"; }
    }

    /** Simulates an object store that sometimes times out (first N puts fail). */
    static final class FlakyObjectStore implements BlobStore {
        final InMemoryBlobStore inner = new InMemoryBlobStore(); int failuresLeft;
        FlakyObjectStore(int failures) { failuresLeft = failures; }
        public void put(String k, byte[] d) { if (failuresLeft-- > 0) throw new IllegalStateException("timeout talking to object store"); inner.put(k, d); }
        public Optional<byte[]> get(String k) { return inner.get(k); }
        public String name() { return "object-store"; }
    }

    static final Map<String, Function<String, BlobStore>> BACKENDS = Map.of(
            "mem", url -> new InMemoryBlobStore(),
            "flaky", url -> new FlakyObjectStore(Integer.parseInt(url.substring(url.indexOf("failures=") + 9))));

    static BlobStore storeFor(String url) {
        String scheme = url.substring(0, url.indexOf("://"));
        Function<String, BlobStore> f = BACKENDS.get(scheme);
        if (f == null) throw new IllegalArgumentException("no blob store for scheme '" + scheme + "'");
        return f.apply(url);
    }

    // ===================== Abstraction: invoice archiving rules =====================
    static class InvoiceArchive {
        protected final BlobStore store; private final int maxAttempts;
        InvoiceArchive(BlobStore store, int maxAttempts) { this.store = store; this.maxAttempts = maxAttempts; }

        String save(String orderId, String invoiceText) {
            String key = "invoices/" + orderId.substring(3, 7) + "/" + orderId + ".txt";       // naming rule lives here, not in back ends
            byte[] data = invoiceText.getBytes(StandardCharsets.UTF_8);
            String checksum = sha256(data);
            for (int attempt = 1; ; attempt++) {
                try {
                    store.put(key, data);
                    store.put(key + ".sha256", checksum.getBytes(StandardCharsets.UTF_8));
                    return key + " (" + attempt + " attempt" + (attempt > 1 ? "s" : "") + ")";
                } catch (IllegalStateException e) {
                    if (attempt == maxAttempts) throw new IllegalStateException("could not archive " + orderId + " on " + store.name() + ": " + e.getMessage(), e);
                }
            }
        }

        String load(String key) {
            byte[] data = store.get(key).orElseThrow(() -> new IllegalArgumentException("no invoice " + key));
            String expected = new String(store.get(key + ".sha256").orElseThrow(), StandardCharsets.UTF_8);
            if (!sha256(data).equals(expected)) throw new IllegalStateException("checksum mismatch for " + key);
            return new String(data, StandardCharsets.UTF_8);
        }

        static String sha256(byte[] d) {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d)).substring(0, 16); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
    }

    // ===================== Refined abstraction: adds an audit trail for finance =====================
    static final class AuditedInvoiceArchive extends InvoiceArchive {
        final List<String> audit = new ArrayList<>();
        AuditedInvoiceArchive(BlobStore s, int attempts) { super(s, attempts); }
        @Override String save(String orderId, String text) { String r = super.save(orderId, text); audit.add("saved " + orderId + " to " + store.name()); return r; }
        @Override String load(String key) { audit.add("read " + key); return super.load(key); }
    }

    public static void main(String[] args) {
        InvoiceArchive dev = new InvoiceArchive(storeFor("mem://local"), 3);
        String key = dev.save("FC-2026-11-0042", "Invoice FC-2026-11-0042: Rs 649.00");
        System.out.println("dev saved " + key);

        AuditedInvoiceArchive prod = new AuditedInvoiceArchive(storeFor("flaky://bucket?failures=2"), 3);
        String saved = prod.save("FC-2026-11-0043", "Invoice FC-2026-11-0043: Rs 1,197.45");
        System.out.println("prod saved " + saved);
        String k2 = saved.replaceAll(" \\(.*", "");
        System.out.println("prod load: " + prod.load(k2));
        System.out.println("audit: " + prod.audit);

        InMemoryBlobStore raw = new InMemoryBlobStore();
        InvoiceArchive tampered = new InvoiceArchive(raw, 1);
        String k3 = tampered.save("FC-2026-11-0045", "Invoice: Rs 100").replaceAll(" \\(.*", "");
        raw.put(k3, "Invoice: Rs 1".getBytes(StandardCharsets.UTF_8));
        try { tampered.load(k3); } catch (IllegalStateException e) { System.out.println("tamper detected: " + e.getMessage()); }

        try { new InvoiceArchive(storeFor("flaky://bucket?failures=5"), 3).save("FC-2026-11-0046", "x"); }
        catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        try { storeFor("ftp://old-server"); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
    }
}
```

1. The implementor is primitive (bytes in, bytes out), so a new back end is small and easy to get right.
2. Back ends are chosen by URL scheme at the composition root, the way JDBC chooses drivers.
3. Retry policy lives in the abstraction, so every back end gets it for free.
4. Integrity checks also live in the abstraction: a checksum written on save is verified on load.
5. A refined abstraction adds auditing without changing any back end.

**Run it:** `java InvoiceArchiveDemo.java`

**Output:**

```text
dev saved invoices/2026/FC-2026-11-0042.txt (1 attempt)
prod saved invoices/2026/FC-2026-11-0043.txt (3 attempts)
prod load: Invoice FC-2026-11-0043: Rs 1,197.45
audit: [saved FC-2026-11-0043 to object-store, read invoices/2026/FC-2026-11-0043.txt]
tamper detected: checksum mismatch for invoices/2026/FC-2026-11-0045.txt
could not archive FC-2026-11-0046 on object-store: timeout talking to object store
no blob store for scheme 'ftp'
```

**What to notice:**

- The object store failed twice; the archive succeeded on the third attempt without the back end knowing about retries.
- Changing a stored invoice from Rs 100 to Rs 1 was caught by the checksum.
- An unknown scheme (`ftp`) failed at startup with a clear message, not on the first invoice.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Extensibility | m + n classes instead of m × n | Two hierarchies to learn |
| Flexibility | Implementor chosen at runtime or by config | Indirection in every call |
| Testability | Each side tested alone; contract tests for implementors | Needs contract tests to keep implementors consistent |
| Design | Domain code isolated from platform code | A poor implementor interface leaks platform details |
| Teams | Different teams own each side | Interface changes need coordination |
| Simplicity | — | Overkill for one dimension |

## 8. When to Use / When NOT to Use

**Use it when:**

- a concept varies in two independent ways (what × how, domain × platform);
- you need to switch back ends by configuration (storage, messaging, payments);
- different teams or vendors own the two sides.

**Don't use it when:**

- only one dimension varies (use Strategy or plain polymorphism);
- you're wrapping one existing API to fit yours (that's Adapter);
- the "second hierarchy" is speculative.

## 9. Best Practices

1. **Keep the implementor interface small and primitive.** *(Consensus.)*
2. **Put policies (retries, validation, naming) in the abstraction**, not in each implementor. *(Consensus.)*
3. **Write a contract test** every implementor must pass. *(Consensus.)*
4. **Choose implementors at the composition root** from configuration, failing fast on unknown values. *(Consensus.)*
5. **Name classes by one dimension**; a name with both is a smell. *(Opinion, widely shared.)*
6. **Allow escape hatches** (`unwrap`) for vendor-specific features instead of bloating the interface. *(Consensus; JDBC does this.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Leaky implementor (production failure mode)**  
> **Symptom:** moving invoice storage to a new object store broke archiving; the new back end didn't support "list keys by prefix" efficiently, and the nightly job timed out.
> **Root cause:** the implementor interface had grown a `listByPrefix` method that the old back end handled cheaply, and the abstraction depended on it in a loop.
> **Fix:** keep the implementor primitive; keep an index of invoice keys in the database (owned by the abstraction); run the contract test, including performance expectations, against each back end before migrating.

> 🚨 **Bridge with one implementor forever**  
> **Symptom:** every change touches two interfaces and three classes, with a single `Default...Impl`.
> **Root cause:** a bridge introduced "for flexibility" with no second back end.
> **Fix:** collapse it; introduce the split when the second implementor actually arrives.

> 🚨 **Policies duplicated in implementors**  
> **Symptom:** S3 uploads retried 3 times, local disk 0 times, Azure 5 times, with different logs.
> **Root cause:** each implementor added its own retries.
> **Fix:** move retries to the abstraction (or a decorator around the implementor) once.

> 🚨 **Lowest common denominator**  
> **Symptom:** a valuable vendor feature (server-side encryption) can't be used.
> **Root cause:** the implementor interface only exposes what all back ends share.
> **Fix:** optional capabilities (a `supports(Feature)` method or `unwrap`), not a bigger mandatory interface.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Back-end URL (`archive.url`) | none (required) | Selects the implementor | Per environment | Typos must fail at startup |
| `spring.datasource.url` / driver | none | Picks the JDBC driver (a bridge) | Switching databases | Dialect differences |
| Retry attempts (abstraction) | 3 | Transient failure handling for all back ends | Flaky back ends | Long waits, duplicate writes if not idempotent |
| Checksum verification | on | Detects corruption | Never turn off for financial data | CPU cost on huge files |

## 12. Security Considerations

- Never let user input choose the implementor (URL scheme, driver class); an attacker could select a debug or local-file back end.
- Encryption, access control and audit belong in the abstraction so no back end can forget them.
- Credentials for each implementor should come from a secrets manager at the composition root, not from the abstraction.
- JDBC URLs can carry dangerous options (for example, some drivers allow loading classes or files via URL parameters); don't build them from user input.

## 13. Performance & Scalability

The bridge itself is one extra method call. Performance differences come from implementors: an in-memory store and a remote object store have very different latency, so design the implementor interface around the slowest realistic back end (batch operations, streaming for large data). Put caching and batching in the abstraction so every back end benefits, and measure per implementor (tag metrics with the back-end name).

## 14. Testing It

A contract test every implementor must pass, plus tests of the abstraction's own policies:

**InvoiceArchiveTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InvoiceArchiveTest {

    /** Every BlobStore implementor must pass the same contract; the abstraction relies on it. */
    abstract static class BlobStoreContract {
        abstract InvoiceArchiveDemo.BlobStore store();

        @Test
        void returnsWhatWasStored() {
            var s = store();
            s.put("a/b.txt", new byte[] {1, 2, 3});
            assertArrayEquals(new byte[] {1, 2, 3}, s.get("a/b.txt").orElseThrow());
        }

        @Test
        void missingKeyIsEmptyNotAnError() {
            assertTrue(store().get("nope").isEmpty());
        }

        @Test
        void callersCannotMutateStoredBytes() {
            var s = store();
            byte[] data = {9};
            s.put("k", data);
            data[0] = 0;
            assertEquals(9, s.get("k").orElseThrow()[0]);
        }
    }

    @Nested class InMemory extends BlobStoreContract {
        InvoiceArchiveDemo.BlobStore store() { return new InvoiceArchiveDemo.InMemoryBlobStore(); }
    }

    @Nested class FlakyAfterRecovery extends BlobStoreContract {
        InvoiceArchiveDemo.BlobStore store() { return new InvoiceArchiveDemo.FlakyObjectStore(0); }
    }

    @Test
    void archiveRetriesTransientFailures() {
        var archive = new InvoiceArchiveDemo.InvoiceArchive(new InvoiceArchiveDemo.FlakyObjectStore(2), 3);
        assertTrue(archive.save("FC-2026-11-0001", "x").endsWith("(3 attempts)"));
    }

    @Test
    void archiveDetectsCorruption() {
        var raw = new InvoiceArchiveDemo.InMemoryBlobStore();
        var archive = new InvoiceArchiveDemo.InvoiceArchive(raw, 1);
        String key = archive.save("FC-2026-11-0002", "Rs 100").replaceAll(" \\(.*", "");
        raw.put(key, "Rs 1".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> archive.load(key));
    }
}
```

## 15. Observability & Operations

Tag every metric and log line with the implementor's name (`backend=object-store`) so dashboards can compare back ends and migrations can be watched side by side. Log the chosen implementor at startup. During a back-end migration, run both implementors in parallel (write to both, read from old, compare) using a bridge-level "dual write" implementor.

## 16. Version Notes

| Version | Change relevant to Bridge |
|---------|---------------------------|
| Java 1.1 | JDBC 1.0 and AWT peers: the JDK's original bridges |
| Java 6 / JDBC 4.0 | Drivers self-register via `ServiceLoader` (no `Class.forName`) |
| Java 9 | Module system: `ServiceLoader` with `provides ... with` |
| Java 17 | Sealed interfaces can close a bridge's implementor set when you own all back ends |
| SLF4J 2.0 | Bindings discovered via `ServiceLoader` instead of static binder classes |

## 17. Expert Deep Dive

- **Bridges at architecture scale.** Hexagonal architecture's "ports and adapters" is a bridge: the domain defines ports (implementor interfaces) and infrastructure provides adapters (concrete implementors). See [Architectural Patterns](../l4-senior/architectural-patterns.md).
- **Service Provider Interfaces (SPI).** Java's standard way to ship implementors separately: the API module defines the interface, providers ship jars with `META-INF/services` entries, and `ServiceLoader` finds them. JDBC, JAXP, SLF4J 2 and `java.nio.file.spi.FileSystemProvider` all work this way.
- **Capability negotiation.** Mature bridges let implementors declare optional features (`DatabaseMetaData.supportsBatchUpdates()`), so the abstraction can use them when present and fall back otherwise.
- **Migrations.** Because the implementor is swappable, a bridge is the natural place for strangler-style migrations: a composite implementor writes to both back ends, then reads switch over, then the old one is removed.

> 📖 **War story: the SMS bill**  
> A grocery company sent all customer notifications through a `Notification` × `Channel` bridge. A new "Promo" message kind was added in a hurry, and its author hard-coded the SMS channel because "promos always go by SMS". Push was the default for everything else, and promotions were sent nightly to 2 million customers. The first night's SMS bill was more than the month's budget. **Fixes:** channel selection moved to a preference service at the composition root (customer preference, cost tier, quiet hours); message kinds can no longer construct channels themselves; and a per-channel spending limit was added to the abstraction. **Lesson:** a bridge only helps if the pairing is decided in one place, not by whoever writes the next refined abstraction.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What does Bridge do?** Separates an **abstraction** from its **implementation** so both can vary independently.
2. **What problem does it solve?** **Class explosion**: m × n subclasses for two dimensions.
3. **Give a JDK example.** **JDBC**: `Connection` API vs vendor `Driver`s.
4. **What connects the two hierarchies?** A **field** in the abstraction referencing the implementor.
5. **Give a FreshCart example.** **Notification kinds × channels** (SMS, push, email).

**▸ 🟡 L3**

1. **Bridge vs Adapter?** Adapter fits an **existing** API after the fact; Bridge is designed **up front** for two hierarchies.
2. **Bridge vs Strategy?** Strategy swaps **one algorithm** in one context; Bridge has **hierarchies on both sides**.
3. **Where should retries live?** In the **abstraction** (or one decorator), not in each implementor.
4. **How do you keep implementors consistent?** A **contract test** they all pass.
5. **How does JDBC find a driver?** **`ServiceLoader`** registration, then matching the **URL prefix**.

**▸ 🔴 L4–L5**

1. **Design a pluggable storage layer for invoices.** **Primitive implementor**, policies in the abstraction, URL-based selection, contract tests, checksums, migration via dual write.
2. **How do you handle features only some back ends support?** **Capability flags** or `unwrap`, not a bigger mandatory interface.
3. **How does hexagonal architecture relate to Bridge?** **Ports** are implementor interfaces; **adapters** are concrete implementors.
4. **How would you migrate from one back end to another with no downtime?** **Dual-write implementor**, backfill, compare reads, switch, remove.
5. **When is a bridge harmful?** **Speculative** second hierarchies, leaky interfaces, or when it hides **performance differences** that matter.

**▸ 💻 Hands-on**

**Task:** Add a `DualWriteBlobStore` to `InvoiceArchiveDemo` for migrations.

> ✅ **Solution**  
> ```java
> record DualWriteBlobStore(BlobStore oldStore, BlobStore newStore) implements BlobStore {
>     public void put(String k, byte[] d) { oldStore.put(k, d); newStore.put(k, d); }
>     public Optional<byte[]> get(String k) { return oldStore.get(k).or(() -> newStore.get(k)); }
>     public String name() { return "dual(" + oldStore.name() + "," + newStore.name() + ")"; }
> }
> ```

## 19. Practice

- [ ] Add a WhatsApp channel and an OTP message kind to `AbstractionImplementorDemo`; count the classes you touched.
    > 💡 **Hint**  
    > You should add two classes and change none.

- [ ] Add a `MarkdownExporter` to `ReportExportDemo`.
    > 💡 **Hint**  
    > Header row, a `|---|` separator row, then one `|`-separated row per data row.

- [ ] Add a `listInvoices(year)` feature to `InvoiceArchiveDemo` without adding a method to `BlobStore`.
    > 💡 **Hint**  
    > Keep an index blob per year, updated by the abstraction on each save.

- [ ] Find a class pair in a codebase whose names combine two dimensions (`S3CsvExporter`, `KafkaOrderPublisher`).
    > 💡 **Hint**  
    > Ask whether each half varies independently.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Intent: split two independent dimensions into two hierarchies joined by a field.
> - Abstraction (what) holds implementor (how); m + n classes instead of m × n.
> - Implementor small and primitive; policies (retries, checksums) in the abstraction.
> - Choose the implementor at the composition root; contract-test all implementors.
> - Java examples: JDBC, SLF4J, AWT peers; architecture: ports and adapters.
>
> ```java
> abstract class Notification {
>     protected final Channel channel;                 // the bridge
>     Notification(Channel channel) { this.channel = channel; }
>     abstract String send(String customer);
> }
> ```

## 21. Further Reading

- Gamma, Helm, Johnson, Vlissides, *Design Patterns* (1994), "Bridge".
- [JDBC API specification and `java.sql` package](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/package-summary.html)
- [java.util.ServiceLoader API documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html)
- [SLF4J user manual](https://www.slf4j.org/manual.html)
- Alistair Cockburn, "Hexagonal Architecture" (2005).

---

[← Previous: Proxy](proxy.md) · [Next: Flyweight →](flyweight.md)
