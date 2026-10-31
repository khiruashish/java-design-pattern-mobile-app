# SOLID Principles

L1 🟢 Foundations · ⏱ 55 min read · Prerequisites: [OOP Refresher for Patterns](oop-refresher.md)

> 📌 **TL;DR**  
> SOLID is five principles for classes that are easy to change: one reason to change per class, extend without editing, subtypes that keep their parent's promises, small interfaces, and dependencies on abstractions. They are the "why" behind most design patterns: when you can name which principle a design breaks, you usually know which pattern fixes it.

## 1. What It Is

**Plain English.** SOLID is a checklist of five questions to ask about a class: Does it do one job? Can I add behaviour without editing it? Can a subclass stand in for its parent safely? Does it force callers to depend on methods they don't use? Does it depend on details or on abstractions?

**Precise definition.** The acronym, popularised by Robert C. Martin (the name was suggested by Michael Feathers), stands for:

| Letter | Principle | Short statement |
|--------|-----------|-----------------|
| **S** | Single Responsibility (SRP) | A module should be responsible to one, and only one, actor (one reason to change). |
| **O** | Open/Closed (OCP) | Software entities should be open for extension but closed for modification (Bertrand Meyer, 1988). |
| **L** | Liskov Substitution (LSP) | Objects of a subtype must be usable wherever the supertype is expected without breaking correctness (Barbara Liskov, 1987). |
| **I** | Interface Segregation (ISP) | Clients should not be forced to depend on methods they do not use. |
| **D** | Dependency Inversion (DIP) | High-level policy should not depend on low-level details; both should depend on abstractions. |

New terms: an **actor** is a group of people who request changes (marketing, finance, rider operations). **Policy** is the business logic; **details** are databases, frameworks, email providers.

## 2. Why It Exists

Code that's hard to change fails in predictable ways: one change breaks something unrelated (fragility), a small change needs edits in many places (rigidity), and parts can't be reused or tested alone (immobility). Martin described these "design smells" in the early 2000s and collected the principles that prevent them. Before SOLID was a shared checklist, teams learned these lessons separately and painfully, usually through production incidents caused by "small" changes.

## 3. Mental Model / Analogy

**Analogy.** A well-run FreshCart store: each person has one job (the packer packs, the cashier takes payment: **S**). A new payment terminal plugs into the standard socket without rewiring the counter (**O**). Any trained cashier can cover any till (**L**). The rider app shows riders only rider functions, not the store's stock-taking screens (**I**). The store manager's process says "use a delivery partner", not "call Imran on his personal phone" (**D**).

**Technical model.** Each principle limits how change spreads through the dependency graph. SRP and ISP keep each node small, OCP and DIP point the arrows toward stable abstractions, and LSP makes sure that substituting one node for another along those arrows is safe.

## 4. How It Works

DIP is the principle that changes the *shape* of the dependency graph. Without it, business policy depends directly on details. With it, both depend on an interface owned by the policy side:

> 📊 *Diagram: open this page on the website to see it drawn.*

The arrows from the details now point *toward* the policy layer: the dependency is inverted. That's what lets you test `OrderService` with in-memory fakes and swap the email provider without touching it.

## 5. Subtopics

Each principle below gets the same deep dive: what it means, why it helps, a runnable before/after example in FreshCart, a pros/cons table, when to use and avoid it, where you already see it in real Java, a best practice and the most common mistake.

### S: Single Responsibility Principle

**Description.** A class should have one reason to change. Robert C. Martin's sharper version: a module should be *responsible to one, and only one, actor*, meaning one group of people who request changes. An invoice service that calculates tax (Finance), lays out the invoice (Marketing) and sends it (Platform) has three actors, so three groups edit the same file for unrelated reasons, and one group's change can break another's feature.

**Advantages / features.**

- Changes requested by one team can't break another team's behaviour.
- Smaller classes are easier to read, name, review and test.
- Each part can be reused (the calculator also feeds the refund screen).
- Merge conflicts drop, because different teams edit different files.

**Production example: splitting an invoice service by actor.**

**SrpDemo.java**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** SRP: one class per actor. Finance owns tax, Marketing owns the layout, Platform owns delivery. */
public class SrpDemo {

    record Line(String product, int quantity, BigDecimal unitPrice) {
        Line {
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (unitPrice.signum() < 0) throw new IllegalArgumentException("price must not be negative");
        }
        BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }

    // ===================== BEFORE: three actors, one class =====================
    static final class InvoiceServiceBefore {
        final List<String> outbox = new ArrayList<>();

        void sendInvoice(String email, List<Line> lines) {
            BigDecimal net = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = net.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);   // Finance's rule
            String body = "FreshCart invoice | net " + net + " | tax " + tax + " | total " + net.add(tax); // Marketing's layout
            outbox.add(email + " <- " + body);                                                       // Platform's delivery
        }
    }

    // ===================== AFTER: one reason to change per class =====================
    record InvoiceTotals(BigDecimal net, BigDecimal tax, BigDecimal total) { }

    /** Owned by Finance. Changes when tax law changes. */
    static final class InvoiceCalculator {
        private final BigDecimal taxRate;
        InvoiceCalculator(BigDecimal taxRate) {
            if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("tax rate must be in [0, 1]");
            this.taxRate = taxRate;
        }
        InvoiceTotals totals(List<Line> lines) {
            if (lines.isEmpty()) throw new IllegalArgumentException("invoice needs at least one line");
            BigDecimal net = lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = net.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
            return new InvoiceTotals(net, tax, net.add(tax));
        }
    }

    /** Owned by Marketing. Changes when the brand or layout changes. */
    static final class InvoiceRenderer {
        String render(String orderId, List<Line> lines, InvoiceTotals t) {
            StringBuilder sb = new StringBuilder("FreshCart invoice " + orderId + "\n");
            for (Line l : lines) sb.append("  %-10s x%d  %8s%n".formatted(l.product(), l.quantity(), l.subtotal()));
            sb.append("  net %s | tax %s | TOTAL %s".formatted(t.net(), t.tax(), t.total()));
            return sb.toString();
        }
    }

    /** Owned by Platform. Changes when the email provider changes. */
    interface InvoiceSender { void send(String email, String body); }

    /** Coordinates the three; owns none of their rules. */
    static final class InvoiceService {
        private final InvoiceCalculator calculator;
        private final InvoiceRenderer renderer;
        private final InvoiceSender sender;
        InvoiceService(InvoiceCalculator c, InvoiceRenderer r, InvoiceSender s) { calculator = c; renderer = r; sender = s; }

        InvoiceTotals sendInvoice(String orderId, String email, List<Line> lines) {
            InvoiceTotals totals = calculator.totals(lines);
            sender.send(email, renderer.render(orderId, lines, totals));
            return totals;
        }
    }

    public static void main(String[] args) {
        List<Line> lines = List.of(new Line("MILK-1L", 2, new BigDecimal("1.29")), new Line("BREAD-WHT", 1, new BigDecimal("2.49")));

        InvoiceServiceBefore before = new InvoiceServiceBefore();
        before.sendInvoice("asha@example.com", lines);
        System.out.println("BEFORE: " + before.outbox.get(0));

        InvoiceSender console = (email, body) -> System.out.println("AFTER: to " + email + "\n" + body);
        new InvoiceService(new InvoiceCalculator(new BigDecimal("0.05")), new InvoiceRenderer(), console)
                .sendInvoice("FC-8001", "asha@example.com", lines);

        // Finance changes the tax rate: only the calculator's configuration changes.
        InvoiceTotals newTax = new InvoiceCalculator(new BigDecimal("0.12")).totals(lines);
        System.out.println("Tax change only touches InvoiceCalculator: " + newTax);

        try {
            new InvoiceCalculator(new BigDecimal("0.05")).totals(List.of());
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}
```

1. **Before:** tax rule, text layout and delivery mixed in one method. A layout tweak means re-testing tax.
2. Finance's class: validated tax rate, `BigDecimal` with explicit rounding, rejects empty invoices.
3. Marketing's class: only formatting. It can change freely without touching money.
4. Platform's interface: email today, SMS or PDF-to-S3 tomorrow, without editing the others.

**Run it:** `java SrpDemo.java`

```text
BEFORE: asha@example.com <- FreshCart invoice | net 5.07 | tax 0.25 | total 5.32
AFTER: to asha@example.com
FreshCart invoice FC-8001
  MILK-1L    x2      2.58
  BREAD-WHT  x1      2.49
  net 5.07 | tax 0.25 | TOTAL 5.32
Tax change only touches InvoiceCalculator: InvoiceTotals[net=5.07, tax=0.61, total=5.68]
rejected: invoice needs at least one line
```

| Pros | Cons |
|------|------|
| Changes are isolated per actor | More classes and constructor wiring |
| Smaller, focused unit tests | Logic for one use case is spread over several files |
| Parts are reusable (calculator in refunds, renderer in emails) | Over-splitting creates "one method per class" noise |
| Fewer merge conflicts across teams | Needs judgement to identify the actors |

**Use it when** a class is edited for unrelated reasons, its tests need lots of unrelated setup, or different teams keep touching the same file. **Avoid over-applying it** to small, stable classes with one obvious job; splitting a 30-line class into five adds cost and no benefit.

**Real-world Java.** Spring separates `@Controller` (HTTP), `@Service` (business process) and `@Repository` (persistence) for exactly this reason. In the JDK, `java.time.format.DateTimeFormatter` formats dates, while `LocalDate` only represents them.

**Best practice:** name each class after its single job (`InvoiceCalculator`, not `InvoiceManager`); "Manager", "Helper" and "Util" are SRP warning signs.
**Gotcha:** SRP isn't "one method per class". A class with ten methods that all serve Finance is fine.

### O: Open/Closed Principle

**Description.** Software entities should be *open for extension, closed for modification* (Bertrand Meyer, 1988). In Java: put an interface on the part that keeps changing, and add new behaviour as new implementations, so tested code isn't edited. Every new payment method, promotion or delivery partner should be a new class plus one registration line, not another `case` in a growing `switch`.

**Advantages / features.**

- New features can't break existing ones, because existing code isn't edited.
- Each variant is tested in isolation.
- Several developers can add variants in parallel without conflicts.
- Variants can be enabled per country or per customer through configuration.

**Production example: a payment-method registry.**

**OcpDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OCP: payment methods are added by registering a new rule, never by editing the checkout. */
public class OcpDemo {

    // ===================== BEFORE: closed for extension, open for modification =====================
    static long surchargeBefore(String method, long amountInCents) {
        return switch (method) {
            case "CARD" -> Math.round(amountInCents * 0.015);
            case "WALLET" -> 0;
            case "COD" -> 99;
            default -> throw new IllegalArgumentException("unsupported payment method " + method);
        };
    }

    // ===================== AFTER: open for extension, closed for modification =====================
    interface PaymentMethod {
        String code();
        long surchargeInCents(long amountInCents);
        default boolean supports(long amountInCents) { return true; }
    }

    record Card(double feeRate) implements PaymentMethod {
        public String code() { return "CARD"; }
        public long surchargeInCents(long amount) { return Math.round(amount * feeRate); }
    }
    record Wallet() implements PaymentMethod {
        public String code() { return "WALLET"; }
        public long surchargeInCents(long amount) { return 0; }
    }
    record CashOnDelivery(long feeInCents, long maxOrderInCents) implements PaymentMethod {
        public String code() { return "COD"; }
        public long surchargeInCents(long amount) { return feeInCents; }
        @Override public boolean supports(long amount) { return amount <= maxOrderInCents; }
    }
    /** Added for the India launch. Nothing above this line changed. */
    record Upi() implements PaymentMethod {
        public String code() { return "UPI"; }
        public long surchargeInCents(long amount) { return 0; }
    }

    /** The closed part: never edited when a method is added. */
    static final class PaymentRegistry {
        private final Map<String, PaymentMethod> methods = new LinkedHashMap<>();

        PaymentRegistry register(PaymentMethod method) {
            if (methods.putIfAbsent(method.code(), method) != null) {
                throw new IllegalStateException("duplicate payment method " + method.code());
            }
            return this;
        }

        long totalWithSurcharge(String code, long amountInCents) {
            PaymentMethod m = methods.get(code);
            if (m == null) throw new IllegalArgumentException("unsupported payment method " + code + ", available: " + methods.keySet());
            if (!m.supports(amountInCents)) throw new IllegalArgumentException(code + " not available for this amount");
            return amountInCents + m.surchargeInCents(amountInCents);
        }
    }

    public static void main(String[] args) {
        System.out.println("before CARD: " + (4_000 + surchargeBefore("CARD", 4_000)));

        PaymentRegistry registry = new PaymentRegistry()
                .register(new Card(0.015)).register(new Wallet())
                .register(new CashOnDelivery(99, 10_000)).register(new Upi());

        for (String code : List.of("CARD", "WALLET", "COD", "UPI")) {
            System.out.println("after  " + code + ": " + registry.totalWithSurcharge(code, 4_000));
        }
        for (Runnable bad : List.<Runnable>of(
                () -> registry.totalWithSurcharge("COD", 25_000),
                () -> registry.totalWithSurcharge("CRYPTO", 4_000),
                () -> registry.register(new Upi()))) {
            try { bad.run(); } catch (RuntimeException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
```

1. **Before:** every new method edits this `switch` and risks the existing cases.
2. The extension point: everything that varies per payment method is behind one interface.
3. Variants can carry their own rules: cash on delivery only up to $100.
4. UPI was added for the India launch without touching any line above it.
5. The registry fails fast on duplicates and lists the available methods in its error message.

**Run it:** `java OcpDemo.java`

```text
before CARD: 4060
after  CARD: 4060
after  WALLET: 4000
after  COD: 4099
after  UPI: 4000
rejected: COD not available for this amount
rejected: unsupported payment method CRYPTO, available: [CARD, WALLET, COD, UPI]
rejected: duplicate payment method UPI
```

| Pros | Cons |
|------|------|
| Adding a variant can't break existing ones | You must predict the axis of change |
| Variants are tested alone | More types and a registry to understand |
| Parallel work without merge conflicts | Cross-cutting changes (to every variant) touch many files |
| Variants can be selected by configuration | Behaviour is no longer visible in one `switch` |

**Use it when** the same kind of change has happened two or three times and more are coming (payment methods, promotions, notification channels). **Avoid it when** the set is small and stable; a `switch` over a sealed type or an `enum` is simpler and the compiler checks it's complete.

**Real-world Java.** `java.util.Comparator`, JDBC drivers (`java.sql.Driver` found through `ServiceLoader`), Servlet filters and Spring's `HandlerInterceptor` all let you add behaviour without editing the framework.

**Best practice:** combine OCP with a registry or DI (`List<PaymentMethod>` injected by Spring) so adding a variant is truly one new class.
**Gotcha:** OCP is about the code that *uses* the variants. The composition root (where variants are registered) is expected to change.

### L: Liskov Substitution Principle

**Description.** If `S` is a subtype of `T`, code that works with a `T` must keep working when given an `S` (Barbara Liskov, 1987). In practice a subtype must not require more (stricter inputs), promise less (weaker results), throw new exceptions, or break the supertype's invariants. LSP is what makes polymorphism safe: Strategy, Decorator and Proxy all assume any implementation can be swapped in.

**Advantages / features.**

- You can swap implementations (in-memory, database, remote) without surprises.
- Callers don't need `instanceof` checks or special cases.
- Contract tests can verify every implementation the same way.
- Bugs from "almost compatible" subclasses are caught before production.

**Production example: one contract test for every stock implementation.**

**LspContractDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** LSP in production: one contract, checked against every implementation. */
public class LspContractDemo {

    /**
     * Contract:
     *  - reserve(sku, qty) with qty > 0 returns true and lowers available(sku) by qty if enough stock, else false
     *  - it never throws for an unknown sku (it returns false)
     *  - available(sku) is never negative
     */
    interface StockReservations {
        boolean reserve(String sku, int qty);
        int available(String sku);
    }

    static final class InMemoryStock implements StockReservations {
        private final Map<String, Integer> stock = new HashMap<>();
        InMemoryStock(Map<String, Integer> initial) { stock.putAll(initial); }
        public synchronized boolean reserve(String sku, int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
            int have = stock.getOrDefault(sku, 0);
            if (have < qty) return false;
            stock.put(sku, have - qty);
            return true;
        }
        public synchronized int available(String sku) { return stock.getOrDefault(sku, 0); }
    }

    /** VIOLATES the contract: throws for unknown SKUs and lets stock go negative ("backorders"). */
    static final class BackorderStock implements StockReservations {
        private final Map<String, Integer> stock = new HashMap<>();
        BackorderStock(Map<String, Integer> initial) { stock.putAll(initial); }
        public boolean reserve(String sku, int qty) {
            Integer have = Optional.ofNullable(stock.get(sku)).orElseThrow(() -> new IllegalStateException("no such sku " + sku));
            stock.put(sku, have - qty);
            return true;
        }
        public int available(String sku) { return stock.getOrDefault(sku, 0); }
    }

    /** Honours the contract AND adds backorders as a separate, explicit capability. */
    interface Backorders { int backordered(String sku); }

    static final class BackorderAwareStock implements StockReservations, Backorders {
        private final InMemoryStock delegate;
        private final Map<String, Integer> backorders = new HashMap<>();
        BackorderAwareStock(Map<String, Integer> initial) { delegate = new InMemoryStock(initial); }
        public boolean reserve(String sku, int qty) {
            if (delegate.reserve(sku, qty)) return true;
            backorders.merge(sku, qty, Integer::sum);
            return false;
        }
        public int available(String sku) { return delegate.available(sku); }
        public int backordered(String sku) { return backorders.getOrDefault(sku, 0); }
    }

    /** The contract test every implementation must pass. In a real project this is an abstract JUnit class. */
    static List<String> checkContract(Supplier<StockReservations> factory) {
        List<String> failures = new ArrayList<>();
        check(failures, "reserves when enough stock", () -> { var s = factory.get(); return s.reserve("MILK", 2) && s.available("MILK") == 3; });
        check(failures, "refuses when not enough stock", () -> { var s = factory.get(); return !s.reserve("MILK", 9) && s.available("MILK") == 5; });
        check(failures, "unknown sku returns false", () -> !factory.get().reserve("NOPE", 1));
        check(failures, "never negative", () -> { var s = factory.get(); s.reserve("MILK", 9); return s.available("MILK") >= 0; });
        return failures;
    }

    interface Check { boolean ok() throws Exception; }
    static void check(List<String> failures, String name, Check c) {
        try { if (!c.ok()) failures.add(name); } catch (Exception e) { failures.add(name + " (threw " + e.getClass().getSimpleName() + ")"); }
    }

    public static void main(String[] args) {
        Map<String, Integer> initial = Map.of("MILK", 5);
        Map<String, Supplier<StockReservations>> impls = new java.util.LinkedHashMap<>();
        impls.put("InMemoryStock", () -> new InMemoryStock(initial));
        impls.put("BackorderStock", () -> new BackorderStock(initial));
        impls.put("BackorderAwareStock", () -> new BackorderAwareStock(initial));

        impls.forEach((name, factory) -> {
            List<String> failures = checkContract(factory);
            System.out.println((failures.isEmpty() ? "PASS " : "FAIL ") + name + (failures.isEmpty() ? "" : " " + failures));
        });

        var aware = new BackorderAwareStock(initial);
        aware.reserve("MILK", 9);
        System.out.println("backordered MILK: " + aware.backordered("MILK") + ", available: " + aware.available("MILK"));
    }
}
```

1. The contract is written down: what callers may rely on, including "never throws for an unknown SKU" and "never negative".
2. A violating implementation: it throws, and lets stock go negative to support backorders.
3. The fix: keep the contract, and expose backorders as a separate, explicit capability.
4. The same checks run against every implementation. In a real project this is an abstract JUnit test class that each implementation's test extends.

**Run it:** `java LspContractDemo.java`

```text
PASS InMemoryStock
FAIL BackorderStock [refuses when not enough stock, unknown sku returns false (threw IllegalStateException), never negative]
PASS BackorderAwareStock
backordered MILK: 9, available: 5
```

| Pros | Cons |
|------|------|
| Safe substitution: polymorphism you can trust | Forces you to drop "natural" but broken hierarchies |
| No `instanceof` special cases in callers | Contracts must be written down and tested |
| Contract tests catch violations early | Extra interfaces for optional capabilities |
| Enables Strategy, Decorator, Proxy | Non-functional behaviour (speed, blocking) is hard to specify |

**Use it** for every interface with more than one implementation, especially ones other teams implement. **Watch for violations** when an override throws `UnsupportedOperationException`, ignores its input, or adds a precondition the parent didn't have.

**Real-world Java.** The `java.util.Collection` Javadoc marks `add` and `remove` as *optional operations*, so `List.of(...)` throws on `add`. It's a documented, deliberate LSP trade-off, and the reason you should never assume a `List` you receive is mutable.

**Best practice:** write a reusable contract test and run it against every implementation, including fakes used in other tests.
**Gotcha:** LSP also covers behaviour: an implementation that blocks for 30 seconds where the contract implies fast in-memory access breaks callers just as surely as an exception.

### I: Interface Segregation Principle

**Description.** Clients should not be forced to depend on methods they don't use. A fat interface forces implementers to write fake methods that throw, and forces clients to depend on (and recompile for) changes they don't care about. Split it into *role interfaces* shaped by what each client needs; one class can still implement several roles.

**Advantages / features.**

- Implementers only implement what they can really do: no `UnsupportedOperationException`.
- Clients declare exactly what they need, which documents the dependency.
- Test doubles are tiny (often a lambda).
- Least privilege: a component given only `Scale` can't print or scan.

**Production example: store devices with role interfaces.**

**IspDemo.java**

```java
import java.util.List;

/** ISP: store devices expose only the capabilities they really have. */
public class IspDemo {

    // ===================== BEFORE: one fat interface =====================
    interface StoreDevice {
        String printLabel(String text);
        String scanBarcode();
        int weighGrams();
    }

    static final class LabelPrinterBefore implements StoreDevice {
        public String printLabel(String text) { return "printed [" + text + "]"; }
        public String scanBarcode() { throw new UnsupportedOperationException("printer cannot scan"); }
        public int weighGrams() { throw new UnsupportedOperationException("printer cannot weigh"); }
    }

    // ===================== AFTER: role interfaces =====================
    interface LabelPrinter { String printLabel(String text); }
    interface BarcodeScanner { String scanBarcode(); }
    interface Scale { int weighGrams(); }

    record ZebraPrinter(String id) implements LabelPrinter {
        public String printLabel(String text) { return id + " printed [" + text + "]"; }
    }
    /** A checkout counter really has all three, so it implements all three. */
    record CheckoutCounter(String id, String nextBarcode, int grams) implements LabelPrinter, BarcodeScanner, Scale {
        public String printLabel(String text) { return id + " printed [" + text + "]"; }
        public String scanBarcode() { return nextBarcode; }
        public int weighGrams() { return grams; }
    }

    /** Clients ask only for what they use. */
    static final class LooseProduceStation {
        private final Scale scale;
        private final LabelPrinter printer;
        LooseProduceStation(Scale scale, LabelPrinter printer) { this.scale = scale; this.printer = printer; }

        String weighAndLabel(String product, long centsPerKg) {
            int grams = scale.weighGrams();
            if (grams <= 0) throw new IllegalStateException("scale returned " + grams + " g, re-zero the scale");
            long price = Math.round(grams * centsPerKg / 1000.0);
            return printer.printLabel("%s %d g $%d.%02d".formatted(product, grams, price / 100, price % 100));
        }
    }

    public static void main(String[] args) {
        StoreDevice old = new LabelPrinterBefore();
        System.out.println("before: " + old.printLabel("BANANA"));
        try { old.weighGrams(); } catch (UnsupportedOperationException e) { System.out.println("before: runtime surprise -> " + e.getMessage()); }

        var counter = new CheckoutCounter("counter-3", "8901234567890", 1_250);
        var station = new LooseProduceStation(counter, new ZebraPrinter("zebra-1"));
        System.out.println("after:  " + station.weighAndLabel("BANANA", 129));
        System.out.println("after:  scanned " + counter.scanBarcode());

        for (Scale broken : List.<Scale>of(() -> 0)) {
            try { new LooseProduceStation(broken, new ZebraPrinter("zebra-1")).weighAndLabel("APPLE", 300); }
            catch (IllegalStateException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
```

1. **Before:** one interface for every device capability.
2. The printer must "implement" scanning and weighing, and fails at runtime.
3. Three role interfaces, each with one capability.
4. A checkout counter really has all three, so it implements all three.
5. The produce station asks only for a scale and a printer, and validates the scale's reading.

**Run it:** `java IspDemo.java`

```text
before: printed [BANANA]
before: runtime surprise -> printer cannot weigh
after:  zebra-1 printed [BANANA 1250 g $1.61]
after:  scanned 8901234567890
rejected: scale returned 0 g, re-zero the scale
```

| Pros | Cons |
|------|------|
| No fake or throwing methods | More interfaces to name and discover |
| Clients state exactly what they use | Over-splitting into one-method interfaces with no clear role |
| Tiny test doubles (lambdas) | Some clients need several interfaces injected |
| Least-privilege access by design | Refactoring a published fat interface is a breaking change |

**Use it when** implementers leave methods empty or throwing, or when different clients use clearly different subsets of an interface. **Avoid** splitting interfaces that are always used together.

**Real-world Java.** `java.lang.Readable`, `Appendable`, `AutoCloseable`, `Comparable` and `Iterable` are all small role interfaces that many unrelated classes implement. Spring Data offers `CrudRepository`, `PagingAndSortingRepository` and the minimal `Repository` marker, so you can expose only the operations you want.

**Best practice:** design interfaces from the *client's* side ("what does the produce station need?"), not from the implementation's side ("what can the device do?").
**Gotcha:** ISP doesn't mean every interface has one method; a cohesive set of methods always used together belongs in one interface.

### D: Dependency Inversion Principle

**Description.** High-level modules (business policy) should not depend on low-level modules (SMTP, HTTP, databases); both should depend on abstractions, and the abstractions belong to the high-level side. The concrete classes are chosen in one place, the *composition root* (your `main`, or a DI container such as Spring).

**Advantages / features.**

- Business rules are testable without infrastructure (no SMTP server, no database).
- Infrastructure can be replaced (email → Slack) without editing business code.
- Cross-cutting behaviour (retries, metrics, caching) can be added as decorators around the abstraction.
- Configuration can choose implementations per environment.

**Production example: low-stock alerts with pluggable channels.**

**DipDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** DIP: the stock-alert policy owns its abstractions; Slack, email and the warehouse API are plug-ins. */
public class DipDemo {

    // ===================== BEFORE: policy creates its own details =====================
    static final class SmtpClient { String send(String to, String text) { return "SMTP " + to + ": " + text; } }

    static final class StockAlertsBefore {
        private final SmtpClient smtp = new SmtpClient();       // hard-wired: can't test without SMTP
        String check(String sku, int qty) { return qty < 10 ? smtp.send("store@freshcart.example", sku + " low: " + qty) : "ok"; }
    }

    // ===================== AFTER: abstractions owned by the policy =====================
    interface StockLevels { Map<String, Integer> current(); }
    interface AlertChannel { void alert(String message); }

    /** High-level policy. Knows nothing about Slack, SMTP or HTTP. */
    static final class LowStockAlerts {
        private final StockLevels stock;
        private final AlertChannel channel;
        private final int threshold;
        LowStockAlerts(StockLevels stock, AlertChannel channel, int threshold) {
            if (threshold <= 0) throw new IllegalArgumentException("threshold must be positive");
            this.stock = stock; this.channel = channel; this.threshold = threshold;
        }
        int run() {
            int alerts = 0;
            for (var e : new java.util.TreeMap<>(stock.current()).entrySet()) {
                if (e.getValue() < threshold) { channel.alert(e.getKey() + " low: " + e.getValue() + " left"); alerts++; }
            }
            return alerts;
        }
    }

    // ---- Low-level details implement the policy's interfaces ----
    record SlackChannel(String webhookName) implements AlertChannel {
        public void alert(String m) { System.out.println("slack#" + webhookName + " <- " + m); }
    }
    record EmailChannel(String to) implements AlertChannel {
        public void alert(String m) { System.out.println("email " + to + " <- " + m); }
    }

    /** A decorator that adds retries to ANY channel: possible only because of the abstraction. */
    record RetryingChannel(AlertChannel inner, int attempts) implements AlertChannel {
        public void alert(String m) {
            RuntimeException last = null;
            for (int i = 1; i <= attempts; i++) {
                try { inner.alert(m); return; } catch (RuntimeException e) { last = e; System.out.println("  attempt " + i + " failed: " + e.getMessage()); }
            }
            throw new IllegalStateException("alert failed after " + attempts + " attempts", last);
        }
    }

    /** Composition root: the only place that picks concrete classes, here from configuration. */
    static AlertChannel channelFrom(Map<String, String> config) {
        AlertChannel base = switch (config.getOrDefault("alerts.channel", "email")) {
            case "slack" -> new SlackChannel(config.getOrDefault("alerts.slack.webhook", "store-ops"));
            case "email" -> new EmailChannel(config.getOrDefault("alerts.email.to", "store@freshcart.example"));
            default -> throw new IllegalArgumentException("unknown alerts.channel " + config.get("alerts.channel"));
        };
        return new RetryingChannel(base, 3);
    }

    public static void main(String[] args) {
        System.out.println("before: " + new StockAlertsBefore().check("MILK-1L", 4));

        StockLevels warehouse = () -> Map.of("MILK-1L", 4, "BREAD-WHT", 25, "EGGS-12", 7);
        int sent = new LowStockAlerts(warehouse, channelFrom(Map.of("alerts.channel", "slack")), 10).run();
        System.out.println("alerts sent: " + sent);

        int[] calls = {0};
        AlertChannel flaky = m -> { if (++calls[0] < 3) throw new RuntimeException("timeout"); System.out.println("flaky <- " + m); };
        new LowStockAlerts(() -> Map.of("MILK-1L", 4), new RetryingChannel(flaky, 3), 10).run();

        List<String> captured = new ArrayList<>();
        new LowStockAlerts(warehouse, captured::add, 10).run();
        System.out.println("test double captured: " + captured);
    }
}
```

1. **Before:** the policy creates its own `SmtpClient`, so it can't be tested or switched to Slack.
2. Abstractions owned by the policy: "give me stock levels" and "send an alert".
3. Retries added once, for every channel, as a decorator.
4. The composition root chooses the channel from configuration and fails fast on unknown values.
5. In tests, a method reference (`captured::add`) is the whole test double.

**Run it:** `java DipDemo.java`

```text
before: SMTP store@freshcart.example: MILK-1L low: 4
slack#store-ops <- EGGS-12 low: 7 left
slack#store-ops <- MILK-1L low: 4 left
alerts sent: 2
  attempt 1 failed: timeout
  attempt 2 failed: timeout
flaky <- MILK-1L low: 4 left
test double captured: [EGGS-12 low: 7 left, MILK-1L low: 4 left]
```

| Pros | Cons |
|------|------|
| Business logic testable without infrastructure | Indirection: you must find the implementation |
| Swap infrastructure without touching policy | Wiring code or a DI container to maintain |
| Decorators add retries, caching, metrics once | Abstractions designed too early can be wrong |
| Configuration chooses implementations per environment | Misconfigured wiring fails only at startup or runtime |

**Use it at real boundaries:** databases, external APIs, messaging, email, file systems, the clock and random numbers. **Avoid** wrapping stable, in-process libraries (`java.util`, `java.time` types) behind your own interfaces.

**Real-world Java.** Spring's whole IoC container exists to implement DIP: you depend on `JavaMailSender` or your own `OrderRepository` interface, and Spring injects the implementation. JDBC code depends on `javax.sql.DataSource`, not on a specific connection pool.

**Best practice:** inject through constructors, keep `new SomeInfrastructureClient()` out of business classes, and have exactly one composition root.
**Gotcha:** an interface in the *infrastructure* package that mirrors the vendor's API (`SmtpClientInterface`) isn't inversion; the abstraction must speak the business's language (`AlertChannel.alert(message)`).

### SOLID at a glance

| Principle | Question to ask in review | Typical fix | Pattern it leads to |
|-----------|---------------------------|-------------|---------------------|
| SRP | Who would ask for changes to this class? | Split by actor | Facade, Service Layer |
| OCP | Where will the next variant go? | Interface + registry | Strategy, Decorator, Plugin |
| LSP | Can every implementation pass the same contract test? | Narrow the contract or split capabilities | Template Method (done right), Composite |
| ISP | Does any client or implementer ignore methods? | Role interfaces | Adapter, Proxy |
| DIP | Can I test this without the database, network and clock? | Inject abstractions owned by the policy | Dependency Injection, Hexagonal architecture |

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L1)

**Scenario:** FreshCart prints shelf labels. A helper written for `Rectangle` sets width then height and expects the area to be width × height. Someone adds `Square extends Rectangle` "because a square is a rectangle". This breaks LSP.

**Code / Config:**

**LiskovDemo.java**

```java
public class LiskovDemo {

    // ---- Violation: Square IS-A Rectangle mathematically, but not behaviourally ----
    static class Rectangle {
        protected int width;
        protected int height;
        void setWidth(int width) { this.width = width; }
        void setHeight(int height) { this.height = height; }
        int area() { return width * height; }
    }

    static class Square extends Rectangle {
        @Override void setWidth(int side) { this.width = side; this.height = side; }
        @Override void setHeight(int side) { this.width = side; this.height = side; }
    }

    /** Written against Rectangle's contract: setting the height doesn't change the width. */
    static int resizeShelfLabel(Rectangle label) {
        label.setWidth(5);
        label.setHeight(4);
        return label.area();
    }

    // ---- Fix: no mutable "is-a"; each shape is an immutable value with its own rules ----
    sealed interface Shape permits Rect, Sq { int area(); }
    record Rect(int width, int height) implements Shape { public int area() { return width * height; } }
    record Sq(int side) implements Shape { public int area() { return side * side; } }

    public static void main(String[] args) {
        System.out.println("Rectangle label area: " + resizeShelfLabel(new Rectangle()) + " (expected 20)");
        System.out.println("Square label area:    " + resizeShelfLabel(new Square()) + " (expected 20)");

        for (Shape s : new Shape[] {new Rect(5, 4), new Sq(4)}) {
            System.out.println(s + " area " + s.area());
        }
    }
}
```

1. Each setter changes both sides, to keep the square square.
2. This code is correct for any `Rectangle`, but a `Square` silently breaks its assumption.
3. The fix: immutable values in a sealed hierarchy. Nothing can be "resized into" an invalid state.

**Run it:**

```bash
java LiskovDemo.java
```

**Output:**

```text
Rectangle label area: 20 (expected 20)
Square label area:    16 (expected 20)
Rect[width=5, height=4] area 20
Sq[side=4] area 16
```

**What to notice:**

- No exception, no compiler warning, just a wrong number: LSP violations are quiet.
- The problem is the mutable *setters*, not the geometry. Immutable `Rect` and `Sq` have no contract to break.
- "Is-a" in the real world isn't enough; the subtype must behave like the supertype.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** The original `OrderService` calculated prices, saved to MySQL and sent emails in one class, so pricing campaigns, database migrations and email template changes all edited the same file. The refactored version applies SRP (the service only coordinates), OCP (pricing rules are pluggable) and DIP (the service owns `OrderRepository` and `Notifier`).

**Code / Config:**

**OrderServiceDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** FreshCart order placement after applying SRP, OCP and DIP. */
public class OrderServiceDemo {

    record Order(String id, String customerEmail, long basketInCents, String promoCode) { }

    // ---- OCP: new pricing rules are new classes; the service never changes ----
    interface PricingRule { long adjust(Order order, long runningTotal); }

    record DeliveryFee(long feeInCents, long freeOverInCents) implements PricingRule {
        public long adjust(Order o, long total) { return o.basketInCents() >= freeOverInCents ? total : total + feeInCents; }
    }

    record PromoCode(String code, int percentOff) implements PricingRule {
        public long adjust(Order o, long total) {
            return code.equals(o.promoCode()) ? total - o.basketInCents() * percentOff / 100 : total;
        }
    }

    // ---- DIP: the service owns these abstractions; details implement them ----
    interface OrderRepository { void save(Order order, long totalInCents); Optional<Long> totalOf(String orderId); }
    interface Notifier { void orderConfirmed(String email, String orderId, long totalInCents); }

    // ---- SRP: this class only coordinates placing an order ----
    static final class OrderService {
        private final List<PricingRule> rules;
        private final OrderRepository repository;
        private final Notifier notifier;

        OrderService(List<PricingRule> rules, OrderRepository repository, Notifier notifier) {
            this.rules = List.copyOf(rules);
            this.repository = repository;
            this.notifier = notifier;
        }

        long place(Order order) {
            long total = order.basketInCents();
            for (PricingRule rule : rules) total = rule.adjust(order, total);
            repository.save(order, total);
            notifier.orderConfirmed(order.customerEmail(), order.id(), total);
            return total;
        }
    }

    // ---- Details, chosen only in main (the composition root) ----
    static final class InMemoryOrders implements OrderRepository {
        private final Map<String, Long> totals = new HashMap<>();
        public void save(Order order, long total) { totals.put(order.id(), total); }
        public Optional<Long> totalOf(String id) { return Optional.ofNullable(totals.get(id)); }
    }

    static final class ConsoleEmail implements Notifier {
        public void orderConfirmed(String email, String orderId, long total) {
            System.out.printf("email to %s: order %s confirmed, total $%d.%02d%n", email, orderId, total / 100, total % 100);
        }
    }

    public static void main(String[] args) {
        List<PricingRule> rules = new ArrayList<>(List.of(new DeliveryFee(499, 5_000), new PromoCode("WELCOME10", 10)));
        InMemoryOrders repo = new InMemoryOrders();
        OrderService service = new OrderService(rules, repo, new ConsoleEmail());

        service.place(new Order("FC-5001", "asha@example.com", 3_000, null));
        service.place(new Order("FC-5002", "ravi@example.com", 6_000, "WELCOME10"));
        System.out.println("stored FC-5002 total: " + repo.totalOf("FC-5002").orElseThrow());
    }
}
```

1. **OCP:** a new campaign is a new `PricingRule`, applied in order.
2. **DIP:** the abstractions belong to the business side; the database and email code implement them.
3. **SRP:** the service changes only if the *process* of placing an order changes.
4. The composition root: the only place that names concrete classes.

**Run it:**

```bash
java OrderServiceDemo.java
```

**Output:**

```text
email to asha@example.com: order FC-5001 confirmed, total $34.99
email to ravi@example.com: order FC-5002 confirmed, total $54.00
stored FC-5002 total: 5400
```

**What to notice:**

- FC-5001 is under $50, so it pays the $4.99 fee: $34.99. FC-5002 gets free delivery and 10% off: $54.00.
- Swapping `InMemoryOrders` for a JDBC repository changes `main` only.
- Rule order matters (fee before or after discount). That's now an explicit list you can see and test, not code buried in a method.

### Example 3: Production-grade

**Scenario:** FreshCart refunds must be idempotent (a retried request never pays twice), allowed only within 48 hours of delivery, capped at the amount paid, and fully audited. The service must be testable without a payment provider, a database or the real clock. ISP keeps each dependency small; DIP lets tests replace every one of them.

**Code / Config:**

**RefundServiceDemo.java**

```java
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Refunds with ISP (small role interfaces) and DIP (injected gateway, clock and audit log). */
public class RefundServiceDemo {

    record DeliveredOrder(String id, long paidInCents, Instant deliveredAt) { }

    // ---- ISP: three small interfaces instead of one fat "OrderStore" ----
    interface OrderLookup { Optional<DeliveredOrder> find(String orderId); }
    interface RefundLedger { boolean alreadyRefunded(String refundKey); void record(String refundKey, String orderId, long cents); }
    interface PaymentGateway { String refund(String orderId, long cents); }
    interface AuditLog { void write(String line); }

    static final class RefundRejected extends RuntimeException {
        RefundRejected(String message) { super(message); }
    }

    static final class RefundService {
        static final long REFUND_WINDOW_HOURS = 48;

        private final OrderLookup orders;
        private final RefundLedger ledger;
        private final PaymentGateway gateway;
        private final AuditLog audit;
        private final Clock clock;

        RefundService(OrderLookup orders, RefundLedger ledger, PaymentGateway gateway, AuditLog audit, Clock clock) {
            this.orders = orders; this.ledger = ledger; this.gateway = gateway; this.audit = audit; this.clock = clock;
        }

        /** Idempotent: the same refundKey never pays twice. */
        String refund(String refundKey, String orderId, long cents) {
            if (ledger.alreadyRefunded(refundKey)) {
                audit.write("refund_duplicate key=" + refundKey);
                return "already refunded";
            }
            DeliveredOrder order = orders.find(orderId).orElseThrow(() -> reject(orderId, "unknown order"));
            if (cents <= 0 || cents > order.paidInCents()) throw reject(orderId, "amount must be between 1 and " + order.paidInCents());
            Instant deadline = order.deliveredAt().plus(REFUND_WINDOW_HOURS, ChronoUnit.HOURS);
            if (clock.instant().isAfter(deadline)) throw reject(orderId, "refund window closed at " + deadline);

            String reference = gateway.refund(orderId, cents);
            ledger.record(refundKey, orderId, cents);
            audit.write("refund_ok order=" + orderId + " cents=" + cents + " ref=" + reference);
            return reference;
        }

        private RefundRejected reject(String orderId, String reason) {
            audit.write("refund_rejected order=" + orderId + " reason=\"" + reason + "\"");
            return new RefundRejected(reason);
        }
    }

    // ---- One in-memory class can implement several small roles ----
    static final class InMemoryStore implements OrderLookup, RefundLedger {
        private final Map<String, DeliveredOrder> orders = new HashMap<>();
        private final Set<String> refundKeys = new HashSet<>();
        void add(DeliveredOrder o) { orders.put(o.id(), o); }
        public Optional<DeliveredOrder> find(String id) { return Optional.ofNullable(orders.get(id)); }
        public boolean alreadyRefunded(String key) { return refundKeys.contains(key); }
        public void record(String key, String orderId, long cents) { refundKeys.add(key); }
    }

    public static void main(String[] args) {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        InMemoryStore store = new InMemoryStore();
        store.add(new DeliveredOrder("FC-6001", 4_250, Instant.parse("2026-11-01T18:30:00Z")));
        store.add(new DeliveredOrder("FC-6002", 1_999, Instant.parse("2026-10-29T09:00:00Z")));

        List<String> auditLines = new ArrayList<>();
        int[] counter = {0};
        PaymentGateway gateway = (orderId, cents) -> "RF-" + (++counter[0]);
        RefundService service = new RefundService(store, store, gateway, auditLines::add, fixed);

        System.out.println("1: " + service.refund("req-1", "FC-6001", 1_000));
        System.out.println("2: " + service.refund("req-1", "FC-6001", 1_000));
        for (String[] bad : new String[][] {{"req-2", "FC-6001", "9999"}, {"req-3", "FC-6002", "500"}, {"req-4", "FC-9999", "100"}}) {
            try {
                service.refund(bad[0], bad[1], Long.parseLong(bad[2]));
            } catch (RefundRejected e) {
                System.out.println("rejected: " + e.getMessage());
            }
        }
        System.out.println("audit:");
        auditLines.forEach(line -> System.out.println("  " + line));
    }
}
```

1. **ISP:** the refund flow needs "find an order", not the 20 methods of a full order store.
2. **DIP:** the gateway is an interface the service owns; the provider's SDK hides behind an adapter.
3. Time is a dependency too. A fixed `Clock` makes the 48-hour rule testable and the output deterministic.
4. Idempotency by request key: the duplicate is detected before any money moves.
5. One class can implement several small roles; clients still see only what they need.

**Run it:**

```bash
java RefundServiceDemo.java
```

**Output:**

```text
1: RF-1
2: already refunded
rejected: amount must be between 1 and 4250
rejected: refund window closed at 2026-10-31T09:00:00Z
rejected: unknown order
audit:
  refund_ok order=FC-6001 cents=1000 ref=RF-1
  refund_duplicate key=req-1
  refund_rejected order=FC-6001 reason="amount must be between 1 and 4250"
  refund_rejected order=FC-6002 reason="refund window closed at 2026-10-31T09:00:00Z"
  refund_rejected order=FC-9999 reason="unknown order"
```

**What to notice:**

- The retry with key `req-1` returns "already refunded" without calling the gateway.
- FC-6002 was delivered on 29 October; the window closed 48 hours later, so the refund on 2 November is rejected.
- Every outcome, including rejections, is written to the audit log.
- `main` builds the gateway from a lambda: a one-method interface is the cheapest possible test double.

## 7. Pros, Cons & Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| Simplicity | Each class is small and focused | More classes and interfaces overall |
| Performance | Usually no measurable cost | Extra indirection on very hot paths |
| Scalability | Teams change their parts without colliding | Wiring grows with the number of components |
| Operability | Clear seams for logging, metrics and auditing | Behaviour spread across classes is harder to trace |
| Security | Narrow interfaces limit what each component can do | More extension points to review |
| Testability | Fakes for every dependency, including time | Over-abstracted code needs many fakes |
| Cost | Cheaper, safer changes over the code base's life | Up-front design effort |
| Learning curve | Five memorable checks | The principles are easy to quote and hard to apply well |

## 8. When to Use / When NOT to Use

**Apply SOLID when:**

- code is long-lived and changed by several people or teams;
- business rules change often (pricing, refunds, promotions);
- you need to test business logic without infrastructure.

**Go easy when:**

- writing scripts, prototypes or one-off migrations;
- the code is simple data plumbing with no rules;
- applying a principle would add abstraction for a change nobody expects.

See [DRY, KISS, YAGNI and Friends](other-design-principles.md) for the principles that balance SOLID.

## 9. Best Practices

1. **Split classes by reason to change (actor), not by size.** *(Consensus.)*
2. **Let the business side own the interfaces it depends on.** *(Consensus: DIP.)*
3. **Inject `Clock`, ID generators and randomness.** *(Consensus.)*
4. **Prefer immutable types to avoid LSP traps in hierarchies.** *(Opinion, widely shared.)*
5. **Design interfaces from the client's needs (role interfaces).** *(Consensus: ISP.)*
6. **Keep one composition root per application.** *(Opinion.)*
7. **Don't apply OCP pre-emptively; wait for the second change on an axis.** *(Opinion.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **God service**  
> **Symptom:** `OrderService` has 3,000 lines, and every sprint's changes conflict in it.
> **Root cause:** responsibilities for several actors (pricing, notifications, persistence, fraud) in one class.
> **Fix:** extract one responsibility per change request, behind an interface the service owns.

> 🚨 **Refusing subclass**  
> **Symptom:** `UnsupportedOperationException` from a subclass method in production.
> **Root cause:** a subtype that can't honour its parent's contract (LSP violation).
> **Fix:** split the interface (ISP) so the subtype only promises what it can do, or use composition.

> 🚨 **Double refunds (production failure mode)**  
> **Symptom:** customers occasionally got refunded twice after the payment provider timed out.
> **Root cause:** the client retried the refund request, and the service had no idempotency key, so both requests paid out.
> **Fix:** require an idempotency key per refund request, check it before paying (Example 3), and pass the same key to the payment provider so its side is idempotent too.

> 🚨 **Interfaces everywhere**  
> **Symptom:** `UserServiceImpl implements UserService` for every class, with no second implementation and no fakes.
> **Root cause:** DIP applied mechanically.
> **Fix:** add interfaces at real boundaries (infrastructure, other teams, things you fake in tests).

## 12. Security Considerations

ISP is also a least-privilege tool: a component that receives only `OrderLookup` can't write orders or issue refunds, even if it's compromised or buggy. Audit logs (as in Example 3) belong behind an interface so that every implementation, including tests, records rejections as well as successes.

## 13. Performance & Scalability

SOLID rarely affects runtime performance; interface calls are cheap after JIT inlining. It does affect *team* scalability: SRP and ISP reduce merge conflicts and the blast radius of a change, which matters more than nanoseconds in most business systems.

## 14. Testing It

DIP is what makes this test possible: a fixed clock, an in-memory store and a recording gateway, with no mocking library.

**RefundServiceTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundServiceTest {

    private final RefundServiceDemo.InMemoryStore store = new RefundServiceDemo.InMemoryStore();
    private final List<String> gatewayCalls = new ArrayList<>();
    private final RefundServiceDemo.PaymentGateway gateway = (orderId, cents) -> {
        gatewayCalls.add(orderId + ":" + cents);
        return "RF-TEST";
    };

    private RefundServiceDemo.RefundService serviceAt(String now) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        return new RefundServiceDemo.RefundService(store, store, gateway, line -> { }, clock);
    }

    @Test
    void sameRefundKeyPaysOnlyOnce() {
        store.add(new RefundServiceDemo.DeliveredOrder("FC-1", 1_000, Instant.parse("2026-11-01T00:00:00Z")));
        var service = serviceAt("2026-11-01T12:00:00Z");
        service.refund("k-1", "FC-1", 500);
        service.refund("k-1", "FC-1", 500);
        assertEquals(List.of("FC-1:500"), gatewayCalls);
    }

    @Test
    void refundWindowEndsExactly48HoursAfterDelivery() {
        store.add(new RefundServiceDemo.DeliveredOrder("FC-2", 1_000, Instant.parse("2026-11-01T00:00:00Z")));
        serviceAt("2026-11-03T00:00:00Z").refund("k-2", "FC-2", 100);          // exactly at the deadline: allowed
        assertThrows(RefundServiceDemo.RefundRejected.class,
                () -> serviceAt("2026-11-03T00:00:01Z").refund("k-3", "FC-2", 100));
    }
}
```

## 15. Observability & Operations

Give audit and metrics their own interfaces (as `AuditLog` here) so business code can report outcomes without knowing about the logging framework. For refunds, alert on the rate of `refund_duplicate` (clients retrying too aggressively) and `refund_rejected` by reason.

## 16. Version Notes

The principles don't depend on the Java version, but modern Java makes them cheaper: records make immutable values (helping LSP) one line; sealed interfaces (17) let you close a hierarchy deliberately; lambdas make one-method interfaces (DIP/ISP) easy to implement in tests.

## 17. Expert Deep Dive

- **SRP is about people.** Martin's later formulation ("responsible to one actor") is the useful one. Seniors map classes to the teams and stakeholders who request changes; this is the same thinking that later draws module and service boundaries (Conway's Law).
- **OCP is strategic, not total.** You choose which changes to be closed against. Experienced engineers use change history and roadmap knowledge to pick those axes, and accept that other changes will need edits.
- **LSP is about contracts, including non-functional ones.** A subtype that's 100× slower, or that blocks where the parent didn't, can break callers too. Treat timeouts, thread-safety and exceptions as part of the contract.
- **DIP at architecture scale** becomes hexagonal / clean architecture: the domain owns ports, infrastructure provides adapters. See [Architectural Patterns](../l4-senior/architectural-patterns.md).
- **In a design review**, ask: "Who will ask for changes to this class?", "What's the next rule we'll add and where does it go?", and "Can I test this without the database and the clock?". Those three questions catch most SOLID problems.

> 📖 **War story: the refund that ran on summer time**  
> A grocery company's refund window was implemented with `LocalDateTime.now()` in the service. Twice a year, when the clocks changed, a handful of refunds near the deadline were wrongly rejected or accepted, and support tickets spiked. Nobody could reproduce it in tests, because the tests ran on whatever the machine's clock said. The fix was injecting a `Clock`, storing delivery times as `Instant`, and adding tests at the exact deadline (like `refundWindowEndsExactly48HoursAfterDelivery`). **Lesson:** the system clock is an infrastructure detail; DIP applies to it too.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What does SOLID stand for?** **Single responsibility, open/closed, Liskov substitution, interface segregation, dependency inversion.**
2. **Explain SRP.** A class should have **one reason to change**, serving **one actor**.
3. **Give an LSP violation.** **Square extending a mutable Rectangle**: setting the width changes the height.
4. **What is ISP?** Clients **shouldn't depend on methods they don't use**; prefer small role interfaces.
5. **How does DIP help testing?** Depending on **interfaces** lets tests pass **fakes** instead of real infrastructure.

**▸ 🟡 L3**

1. **DIP vs. dependency injection?** DIP is **who owns the abstraction** (the policy side); DI is the **technique of passing dependencies in**.
2. **How do you decide where to apply OCP?** Use **change history** and the roadmap; apply on the **axis that keeps changing**.
3. **Is `Collections.unmodifiableList` an LSP violation?** Arguably yes; the JDK documents mutators as **optional operations**, a deliberate trade-off.
4. **How would you test time-based rules?** Inject a **`Clock`** and use **`Clock.fixed`** in tests, including at the **exact boundary**.
5. **Signs a class breaks SRP?** **Many unrelated imports**, frequent merge conflicts, changes requested by **different teams**.

**▸ 🔴 L4–L5**

1. **How do SOLID principles show up at service level?** SRP → **service boundaries by business capability**; DIP → **ports and adapters**; ISP → **narrow APIs per consumer**.
2. **When is applying SOLID wrong?** For **throwaway or very stable code**, or when abstractions are added for **imagined change**.
3. **Design idempotent refunds across retries and provider timeouts.** **Idempotency key** end to end, **ledger before/after** with a unique constraint, and **pass the key to the provider**.
4. **How do you raise SOLID awareness in 10 teams without dogma?** Review **real incidents** and change costs, add **review questions** rather than rules.
5. **Now refunds come from three channels (app, support, chat bot). What changes?** A **single refund policy** behind one port, channel-specific **adapters**, and **per-channel limits** as configuration.

**▸ 💻 Hands-on**

**Task:** Add a "loyalty points" pricing rule to Example 2 that takes 1% off for customers with more than 1,000 points, without changing `OrderService`.

> ✅ **Solution**  
> ```java
> record LoyaltyDiscount(java.util.Map<String, Integer> pointsByEmail) implements PricingRule {
>     public long adjust(Order o, long total) {
>         int points = pointsByEmail.getOrDefault(o.customerEmail(), 0);
>         return points > 1_000 ? total - o.basketInCents() / 100 : total;
>     }
> }
> // main: rules.add(new LoyaltyDiscount(Map.of("ravi@example.com", 1_500)));
> ```
> `OrderService` is untouched: the change is one new class and one line in the composition root.

## 19. Practice

- [ ] List the actors for a class in your current project. Does it serve more than one?
    > 💡 **Hint**  
    > Look at the last ten commits that touched it and who asked for each.

- [ ] Add an SMS notifier to Example 2 that sends alongside email.
    > 💡 **Hint**  
    > Write a `CompositeNotifier` that holds a list of notifiers: a preview of the Composite pattern.

- [ ] In Example 3, make the ledger check and record atomic.
    > 💡 **Hint**  
    > Replace `alreadyRefunded` + `record` with one `boolean tryReserve(String key)` that uses `Set.add`'s return value.

- [ ] Find an LSP violation in code you know (an override that throws or ignores its input).
    > 💡 **Hint**  
    > Search for `UnsupportedOperationException` and empty overrides.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - **S:** one actor, one reason to change.
> - **O:** add behaviour by adding classes on the axis that changes.
> - **L:** subtypes keep the parent's promises; immutability helps.
> - **I:** small role interfaces shaped by clients.
> - **D:** policy owns the interfaces; details implement them; wire in one place.
>
> ```java
> final class RefundService {                     // S: coordinates refunds only
>     RefundService(OrderLookup orders,           // I: small role interface
>                   PaymentGateway gateway,       // D: owned abstraction
>                   Clock clock) { /* ... */ }
> }
> ```

## 21. Further Reading

- Robert C. Martin, *Agile Software Development, Principles, Patterns, and Practices* (2002).
- Robert C. Martin, *Clean Architecture* (2017), part III "Design Principles".
- Barbara Liskov, "Data Abstraction and Hierarchy" (OOPSLA 1987 keynote); Liskov and Wing, "A Behavioral Notion of Subtyping" (1994).
- Bertrand Meyer, *Object-Oriented Software Construction* (1988).

---

[← Previous: OOP Refresher for Patterns](oop-refresher.md) · [Next: DRY, KISS, YAGNI and Friends →](other-design-principles.md)
