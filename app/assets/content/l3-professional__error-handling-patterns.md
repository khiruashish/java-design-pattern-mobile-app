# Error Handling Patterns

L3 🟡 Professional · ⏱ 40 min read · Prerequisites: [Functional Patterns](functional-patterns.md), [Patterns in Modern Java](patterns-in-modern-java.md)

> 📌 **TL;DR**  
> Good error handling answers three questions: **who can do something about this failure, what do they need to know, and what state is the system left in?** Use **checked exceptions** (sparingly) for recoverable conditions a caller must handle, **unchecked** ones for bugs and unrecoverable failures. **Translate** exceptions at layer boundaries so each layer speaks its own language, keeping the cause. Use **Result types** when failure is an expected outcome, so the compiler forces handling. **Fail fast** on bad input and configuration, as early and clearly as possible. Use the **Notification pattern** to report every validation problem at once. Never swallow exceptions, and never lose the cause.

## 1. What It Is

**Plain English.** Things go wrong all the time in a grocery app: a customer types "two" for quantity, a card is declined, the database times out, a supplier sends a broken file. Error handling patterns are standard ways to detect those problems, decide who deals with them, and tell the right person (customer, caller, operator) the right thing.

**Definition.**

| Pattern | Use when |
|---------|----------|
| Checked vs unchecked exceptions | Choosing how a Java method signals failure |
| Exception translation | Crossing a layer or library boundary |
| Result types | Failure is a normal, expected outcome |
| Fail fast | Input or configuration is invalid |
| Notification | Validating many fields or rows and reporting all problems |

## 2. Why It Exists

Most production incidents involve error paths: an exception swallowed in a `catch` block, a retry storm on a non-retryable error, a misleading message that sent engineers down the wrong path, a half-applied batch. Error paths are tested less than happy paths, so their design must make the right behaviour the easy one. Java's exception system is powerful but easy to misuse (checked-exception noise, `catch (Exception e) {}`), and modern Java's sealed types and pattern matching add a second tool: errors as values.

## 3. Mental Model / Analogy

**Analogy.** A hospital. A nurse who sees something minor handles it (local recovery). Something serious is escalated to the doctor in medical terms, not "the machine beeped" (translation, keeping the original reading as evidence). Admission forms list every missing field at once (notification). Patients with obviously wrong data are stopped at reception, not discovered in surgery (fail fast). Routine outcomes like "test negative" are results, not emergencies (result types).

**Technical model.** Every failure has an **owner**: the layer that can act on it. Below the owner, failures propagate (often translated); at the owner, they're handled; above it, only an outcome is visible.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Checked vs. unchecked exceptions

**Description.** Java has **checked** exceptions (subclasses of `Exception` but not `RuntimeException`), which must be declared or caught, and **unchecked** ones (`RuntimeException`, `Error`), which needn't be. The common guideline (Effective Java, Item 70): checked for **recoverable conditions** the caller can reasonably handle; unchecked for **programming errors** (bad arguments, broken invariants) and failures nobody nearby can fix.

**Advantages / features.** Checked: the compiler reminds callers of real alternatives. Unchecked: no signature noise; work with lambdas and streams.

**CheckedVsUncheckedDemo.java**

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** Checked exceptions for recoverable conditions the caller must handle; unchecked for bugs and unrecoverable failures. */
public class CheckedVsUncheckedDemo {

    /** Recoverable and expected: the caller can do something sensible (try another warehouse). */
    static final class OutOfStockException extends Exception {
        final String sku;
        OutOfStockException(String sku) { super(sku + " is out of stock"); this.sku = sku; }
    }

    static int reserve(Map<String, Integer> stock, String sku, int qty) throws OutOfStockException {
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive, got " + qty);
        int left = stock.getOrDefault(sku, 0);
        if (left < qty) throw new OutOfStockException(sku);
        stock.put(sku, left - qty);
        return left - qty;
    }

    /** Lambdas can't throw checked exceptions, so wrap them at the boundary, keeping the cause. */
    static String readLabel(String sku) {
        try {
            if (sku.startsWith("BAD")) throw new IOException("label printer offline");
            return "LABEL-" + sku;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> koramangala = new java.util.HashMap<>(Map.of("CURD-400G", 1));
        Map<String, Integer> indiranagar = new java.util.HashMap<>(Map.of("CURD-400G", 9));
        try {
            reserve(koramangala, "CURD-400G", 2);
        } catch (OutOfStockException e) {
            System.out.println("recovered: " + e.getMessage() + ", trying Indiranagar");
            try {
                System.out.println("Indiranagar left: " + reserve(indiranagar, "CURD-400G", 2));
            } catch (OutOfStockException again) {
                System.out.println("no stock anywhere");
            }
        }
        try {
            reserve(indiranagar, "CURD-400G", -1);
        } catch (OutOfStockException e) {
            System.out.println("unreachable");
        } catch (IllegalArgumentException bug) {
            System.out.println("caller bug: " + bug.getMessage());
        }
        try {
            List.of("MILK", "BAD-1").stream().map(CheckedVsUncheckedDemo::readLabel).toList();
        } catch (UncheckedIOException e) {
            System.out.println("wrapped: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
        }
    }
}
```

1. A checked exception for an expected, recoverable situation, carrying data the caller can use.
2. A negative quantity is a caller bug: an unchecked `IllegalArgumentException`.
3. Lambdas can't throw checked exceptions; wrap in an unchecked one and keep the cause.

**Run it:** `java CheckedVsUncheckedDemo.java`

```text
recovered: CURD-400G is out of stock, trying Indiranagar
Indiranagar left: 7
caller bug: qty must be positive, got -1
wrapped: IOException: label printer offline
```

| Kind | Examples | Use for |
|------|----------|---------|
| Checked | `IOException`, `SQLException`, custom `OutOfStockException` | Recoverable, expected conditions |
| Unchecked | `IllegalArgumentException`, `IllegalStateException`, `NullPointerException` | Bugs, violated preconditions |
| Unchecked (framework) | Spring `DataAccessException`, `UncheckedIOException` | Infrastructure failures most callers can't fix |
| `Error` | `OutOfMemoryError`, `StackOverflowError` | Never catch (except at the very top for logging) |

**Use** checked exceptions sparingly, for recoverable conditions in APIs with few callers. **Avoid** checked exceptions in widely used APIs and functional code; Spring, Hibernate and most modern libraries are unchecked.
**Real-world Java:** `java.io` (checked), `java.time` and collections (unchecked), Spring's choice to make everything unchecked.
**Best practice:** for expected business outcomes, a Result type (below) often beats both. **Gotcha:** `NumberFormatException` is an `IllegalArgumentException`; a `catch (IllegalArgumentException e)` also catches parse errors, which may or may not be what you meant.

### Exception translation

**Description.** Each layer catches exceptions from the layer below and rethrows them in its own vocabulary, **with the original as the cause**. The repository turns `SQLException` into data-access exceptions; the service turns a duplicate-key error into `CouponAlreadyRedeemedException`. Callers depend on abstractions, not on JDBC or a vendor SDK.

**Advantages / features.** Layers stay decoupled; error meaning matches the caller's level; full diagnostic chain preserved.

**ExceptionTranslationDemo.java**

```java
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientConnectionException;

/** Each layer translates low-level exceptions into its own vocabulary, keeping the original as the cause. */
public class ExceptionTranslationDemo {

    // Infrastructure-neutral data-access exceptions (like Spring's DataAccessException hierarchy).
    static class DataAccessException extends RuntimeException {
        DataAccessException(String m, Throwable cause) { super(m, cause); }
    }
    static class DuplicateKeyException extends DataAccessException {
        DuplicateKeyException(String m, Throwable c) { super(m, c); }
    }
    static class TransientDataAccessException extends DataAccessException {
        TransientDataAccessException(String m, Throwable c) { super(m, c); }
    }

    // Domain exception: what the business cares about.
    static class CouponAlreadyRedeemedException extends RuntimeException {
        CouponAlreadyRedeemedException(String code, Throwable cause) { super("coupon " + code + " was already used", cause); }
    }

    /** JDBC-level code: throws SQLExceptions. */
    static void insertRedemption(String code, String scenario) throws SQLException {
        switch (scenario) {
            case "duplicate" -> throw new SQLIntegrityConstraintViolationException("Duplicate entry 'WELCOME50-c42' for key 'uk_coupon_customer'", "23000", 1062);
            case "timeout" -> throw new SQLTransientConnectionException("Connection is not available, request timed out after 30000ms", "08001");
            default -> { }
        }
    }

    /** Repository: translates SQL exceptions; callers never see java.sql. */
    static void saveRedemption(String code, String scenario) {
        try {
            insertRedemption(code, scenario);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DuplicateKeyException("redemption exists", e);
        } catch (SQLTransientConnectionException e) {
            throw new TransientDataAccessException("database busy", e);
        } catch (SQLException e) {
            throw new DataAccessException("redemption insert failed", e);
        }
    }

    /** Service: translates data-access exceptions that have business meaning. */
    static String redeem(String code, String scenario) {
        try {
            saveRedemption(code, scenario);
            return "redeemed " + code;
        } catch (DuplicateKeyException e) {
            throw new CouponAlreadyRedeemedException(code, e);
        }
    }

    public static void main(String[] args) {
        for (String scenario : new String[] {"ok", "duplicate", "timeout"}) {
            try {
                System.out.println(scenario + ": " + redeem("WELCOME50", scenario));
            } catch (CouponAlreadyRedeemedException e) {
                System.out.println(scenario + ": tell customer '" + e.getMessage() + "'");
                System.out.println("  cause chain: " + chain(e));
            } catch (TransientDataAccessException e) {
                System.out.println(scenario + ": retry later (" + e.getMessage() + ")");
                System.out.println("  cause chain: " + chain(e));
            }
        }
    }

    static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) sb.append(sb.isEmpty() ? "" : " <- ").append(c.getClass().getSimpleName());
        return sb.toString();
    }
}
```

1. The repository translates by kind of failure, not one generic wrapper.
2. The service translates only errors that have business meaning; others pass through.
3. The cause chain keeps every level's evidence for debugging.

**Run it:** `java ExceptionTranslationDemo.java`

```text
ok: redeemed WELCOME50
duplicate: tell customer 'coupon WELCOME50 was already used'
  cause chain: CouponAlreadyRedeemedException <- DuplicateKeyException <- SQLIntegrityConstraintViolationException
timeout: retry later (database busy)
  cause chain: TransientDataAccessException <- SQLTransientConnectionException
```

| Layer | Catches | Throws |
|-------|---------|--------|
| Repository / adapter | `SQLException`, vendor SDK errors, HTTP status codes | Data-access or gateway exceptions |
| Service | Data-access exceptions with business meaning | Domain exceptions or Results |
| API | Domain exceptions | HTTP responses (RFC 9457 problem details) |

**Use** translation at every boundary to infrastructure or third parties. **Avoid** translating without a cause (`throw new X(e.getMessage())`), or wrapping at every method ("exception tunnelling" five levels deep).
**Real-world Java:** Spring's `PersistenceExceptionTranslationPostProcessor` and `SQLExceptionTranslator`, `@Repository` translation, `UncheckedIOException`.
**Best practice:** log an exception once, at the place it's handled, not at every rethrow. **Gotcha:** translating checked to unchecked hides the failure from the compiler; document it (`@throws`) for callers.

### Result types

**Description.** A Result (or Either) type returns success **or** an error as a value: `Result<Zone>` is `Ok(zone)` or `Err(error)`. With sealed interfaces and pattern matching, the compiler checks that every error case is handled. Results compose with `map`/`flatMap`, so a pipeline stops at the first error without nested `try` blocks.

**Advantages / features.** Errors visible in the signature; exhaustive handling; cheap (no stack trace); works naturally with streams and lambdas.

**ResultTypeDemo.java**

```java
import java.util.List;
import java.util.function.Function;

/** A Result type makes expected failures part of the method signature and forces callers to handle them. */
public class ResultTypeDemo {

    sealed interface Result<T> permits Ok, Err {
        <R> Result<R> map(Function<T, R> f);
        <R> Result<R> flatMap(Function<T, Result<R>> f);
    }
    record Ok<T>(T value) implements Result<T> {
        public <R> Result<R> map(Function<T, R> f) { return new Ok<>(f.apply(value)); }
        public <R> Result<R> flatMap(Function<T, Result<R>> f) { return f.apply(value); }
    }
    record Err<T>(PinCodeError error) implements Result<T> {
        public <R> Result<R> map(Function<T, R> f) { return new Err<>(error); }
        public <R> Result<R> flatMap(Function<T, Result<R>> f) { return new Err<>(error); }
    }

    sealed interface PinCodeError {}
    record Malformed(String input) implements PinCodeError {}
    record NotServiceable(int pin) implements PinCodeError {}

    static Result<Integer> parse(String input) {
        return input.matches("[1-9]\\d{5}") ? new Ok<>(Integer.parseInt(input)) : new Err<>(new Malformed(input));
    }
    static Result<String> zoneFor(int pin) {
        return pin / 1000 == 560 ? new Ok<>("BLR-" + pin % 1000) : new Err<>(new NotServiceable(pin));
    }

    static String message(Result<String> r) {
        return switch (r) {
            case Ok<String> ok -> "Delivering to zone " + ok.value();
            case Err<String>(Malformed m) -> "'" + m.input() + "' is not a valid PIN code";
            case Err<String>(NotServiceable n) -> "We don't deliver to " + n.pin() + " yet";
        };
    }

    public static void main(String[] args) {
        for (String input : List.of("560034", "56003A", "110001")) {
            Result<String> zone = parse(input).flatMap(ResultTypeDemo::zoneFor);
            System.out.println(input + " -> " + message(zone));
        }
    }
}
```

1. A sealed Result with two cases and `map`/`flatMap` to chain steps.
2. Error cases are their own sealed hierarchy, so each can carry precise data.
3. The switch is exhaustive: adding an error type makes this a compile error until it's handled.

**Run it:** `java ResultTypeDemo.java`

```text
560034 -> Delivering to zone BLR-34
56003A -> '56003A' is not a valid PIN code
110001 -> We don't deliver to 110001 yet
```

| | Exceptions | Result type | `Optional` |
|-|------------|-------------|------------|
| Failure visible in signature | Only checked | Yes | Yes (absence only) |
| Carries error detail | Yes | Yes | No |
| Compiler forces handling | Checked only | Yes (with sealed + switch) | Partly |
| Cost | Stack trace capture | Allocation only | Allocation only |
| Best for | Unexpected failures | Expected business outcomes | "Not found" |

**Use** Results for validation, parsing and business outcomes (declined, not serviceable). **Avoid** them for truly exceptional failures (database down) and in code bases where the team expects exceptions; mixing styles randomly is worse than either.
**Real-world Java:** Vavr's `Either` and `Try`, `CompletableFuture` (a result with failure), Java 21 sealed types for hand-written results.
**Best practice:** keep error types sealed and specific; convert to exceptions or HTTP responses at the boundary. **Gotcha:** don't silently `.orElse(null)` a Result; you've thrown away the error.

### Fail fast

**Description.** Detect problems as early as possible, as close to the cause as possible, and stop with a precise message. Validate configuration at startup, method arguments at entry (`Objects.requireNonNull`, guard clauses), and invariants in constructors. A system that fails fast is easier to debug and safer than one that carries bad data forward.

**Advantages / features.** Errors appear near their cause; bad deploys fail at startup, not at the first payment; invalid objects can't exist.

**Before (late failure) and after (validated at startup):**

**FailFastDemo.java**

```java
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Fail fast: detect bad input or configuration at the earliest point, with a precise message. */
public class FailFastDemo {

    // ---- Before: bad config is accepted and fails much later, far from the cause. ----
    static final class LazyConfig {
        final Map<String, String> raw;
        LazyConfig(Map<String, String> raw) { this.raw = raw; }
        Duration paymentTimeout() { return Duration.ofMillis(Long.parseLong(raw.get("payment.timeout.ms"))); }
    }

    // ---- After: validated once, at startup, into a typed object. ----
    record PaymentConfig(String baseUrl, Duration timeout, int maxRetries) {
        PaymentConfig {
            Objects.requireNonNull(baseUrl, "payment.base-url is required");
            if (!baseUrl.startsWith("https://")) throw new IllegalArgumentException("payment.base-url must use https: " + baseUrl);
            if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
                throw new IllegalArgumentException("payment.timeout.ms must be 1..30000, got " + timeout.toMillis());
            if (maxRetries < 0 || maxRetries > 5) throw new IllegalArgumentException("payment.max-retries must be 0..5, got " + maxRetries);
        }
        static PaymentConfig from(Map<String, String> raw) {
            return new PaymentConfig(raw.get("payment.base-url"),
                    Duration.ofMillis(number(raw, "payment.timeout.ms", "3000")),
                    (int) number(raw, "payment.max-retries", "2"));
        }
        private static long number(Map<String, String> raw, String key, String fallback) {
            String value = raw.getOrDefault(key, fallback);
            try { return Long.parseLong(value); }
            catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be a whole number, got '" + value + "'", e); }
        }
    }

    static long refund(long paidPaise, long refundPaise) {
        if (refundPaise <= 0 || refundPaise > paidPaise)
            throw new IllegalArgumentException("refund " + refundPaise + " must be in 1.." + paidPaise);
        return paidPaise - refundPaise;
    }

    public static void main(String[] args) {
        Map<String, String> typo = Map.of("payment.base-url", "https://api.paylink.example", "payment.timeout.ms", "3s");
        LazyConfig lazy = new LazyConfig(typo);
        System.out.println("lazy config accepted at startup");
        try {
            lazy.paymentTimeout();
        } catch (NumberFormatException e) {
            System.out.println("first payment, hours later: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        for (Map<String, String> raw : java.util.List.of(
                typo,
                Map.of("payment.base-url", "http://api.paylink.example"),
                Map.of("payment.base-url", "https://api.paylink.example", "payment.max-retries", "9"),
                Map.of("payment.base-url", "https://api.paylink.example"))) {
            try {
                System.out.println("startup ok: " + PaymentConfig.from(raw));
            } catch (RuntimeException e) {
                System.out.println("startup refused: " + e.getMessage());
            }
        }
        try {
            refund(10_000, 25_000);
        } catch (IllegalArgumentException e) {
            System.out.println("refund refused: " + e.getMessage());
        }
    }
}
```

1. The compact constructor validates every field, with messages naming the config key.
2. A guard clause rejects bad arguments before any work happens.
3. Before: a typo in config ("3s") only surfaces when the first customer pays.

**Run it:** `java FailFastDemo.java`

```text
lazy config accepted at startup
first payment, hours later: NumberFormatException: For input string: "3s"
startup refused: payment.timeout.ms must be a whole number, got '3s'
startup refused: payment.base-url must use https: http://api.paylink.example
startup refused: payment.max-retries must be 0..5, got 9
startup ok: PaymentConfig[baseUrl=https://api.paylink.example, timeout=PT3S, maxRetries=2]
refund refused: refund 25000 must be in 1..10000
```

| Where | Tool |
|-------|------|
| Configuration | `@ConfigurationProperties` + `@Validated`, record constructors |
| Method arguments | `Objects.requireNonNull`, `Objects.checkIndex`, guard clauses |
| Object invariants | Constructors, compact record constructors |
| HTTP requests | Bean Validation (`@Valid`, `@NotBlank`) |
| Collections | Immutable copies (`List.copyOf` rejects nulls) |

**Use** fail fast for configuration, preconditions and invariants. **Avoid** failing fast where partial results are acceptable (dashboards, search results) or where you should collect all errors (forms; see Notification).
**Real-world Java:** Spring Boot failing startup on invalid `@ConfigurationProperties`, `List.of` rejecting nulls, fail-fast iterators (`ConcurrentModificationException`).
**Best practice:** messages should say what was expected, what was found, and where (key, field, line). **Gotcha:** `assert` statements are disabled by default in production JVMs; don't use them for validation.

### Notification pattern for validation

**Description.** Instead of throwing on the first problem, validation collects every problem into a **notification** object and returns it. The caller decides what to do: show all field errors on the form, or reject a file row with all its issues. Fowler described it as a replacement for exceptions in validation.

**Advantages / features.** Users fix everything in one round trip; validation logic separated from error presentation; structured errors (field, code, message).

**NotificationPatternDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** The Notification pattern collects every validation problem and returns them together, instead of throwing on the first. */
public class NotificationPatternDemo {

    record AddressForm(String name, String phone, String pin, String line1) {}

    record FieldError(String field, String message) {}

    static final class Notification {
        private final List<FieldError> errors = new ArrayList<>();
        void add(String field, String message) { errors.add(new FieldError(field, message)); }
        boolean hasErrors() { return !errors.isEmpty(); }
        List<FieldError> errors() { return List.copyOf(errors); }
    }

    // ---- Before: throws on the first problem; the user fixes one field per round trip. ----
    static void validateThrowing(AddressForm f) {
        if (f.name().isBlank()) throw new IllegalArgumentException("name is required");
        if (!f.phone().matches("\\d{10}")) throw new IllegalArgumentException("phone must have 10 digits");
        if (!f.pin().matches("[1-9]\\d{5}")) throw new IllegalArgumentException("PIN must have 6 digits");
    }

    // ---- After: all problems at once. ----
    static Notification validate(AddressForm f) {
        Notification n = new Notification();
        if (f.name().isBlank()) n.add("name", "is required");
        if (!f.phone().matches("\\d{10}")) n.add("phone", "must have 10 digits");
        if (!f.pin().matches("[1-9]\\d{5}")) n.add("pin", "must have 6 digits");
        if (f.line1().length() < 5) n.add("line1", "is too short");
        return n;
    }

    static String save(AddressForm f) {
        Notification n = validate(f);
        if (n.hasErrors()) return "400 " + n.errors();
        return "201 saved";
    }

    public static void main(String[] args) {
        AddressForm bad = new AddressForm("", "98765", "56003", "12");
        try {
            validateThrowing(bad);
        } catch (IllegalArgumentException e) {
            System.out.println("throwing: " + e.getMessage());
        }
        System.out.println("notification: " + save(bad));
        System.out.println("valid: " + save(new AddressForm("Asha", "9876543210", "560034", "12 MG Road")));
    }
}
```

1. A small collector of field errors.
2. Each rule adds an error and validation continues.
3. The API returns every problem with one 400 response.

**Run it:** `java NotificationPatternDemo.java`

```text
throwing: name is required
notification: 400 [FieldError[field=name, message=is required], FieldError[field=phone, message=must have 10 digits], FieldError[field=pin, message=must have 6 digits], FieldError[field=line1, message=is too short]]
valid: 201 saved
```

| | Throw on first error | Notification |
|-|----------------------|--------------|
| Round trips to fix a form | One per error | One |
| Structured output | Usually a message | List of field errors |
| Control flow | Exceptions | Plain return value |
| Fits | Preconditions, invariants | User input, file rows, imports |

**Use** notifications for user input and batch rows. **Avoid** them for programming errors; those should fail fast.
**Real-world Java:** Bean Validation's `Set<ConstraintViolation>`, Spring's `BindingResult` and `Errors`, RFC 9457 problem details with an `errors` array.
**Best practice:** give each error a stable code (`E_PRICE_FORMAT`) as well as a message; clients and translators depend on codes. **Gotcha:** validating dependent fields (PIN depends on country) needs ordering; skip dependent checks when the field they depend on already failed.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop's quantity box accepts free text. Parse it, enforce 1 to 20, and give a clear message instead of a stack trace.

**ParseQuantityDemo.java**

```java
import java.util.List;

/** QuickDrop: parse a quantity typed by a user, failing fast with a clear message instead of a stack trace. */
public class ParseQuantityDemo {

    static int parseQuantity(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("quantity is required");
        try {
            int qty = Integer.parseInt(input.strip());
            if (qty < 1 || qty > 20) throw new IllegalArgumentException("quantity must be 1 to 20, got " + qty);
            return qty;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + input + "' is not a number", e);
        }
    }

    public static void main(String[] args) {
        for (String input : List.of("3", " 12 ", "0", "two", "")) {
            try {
                System.out.println("'" + input + "' -> " + parseQuantity(input));
            } catch (IllegalArgumentException e) {
                System.out.println("'" + input + "' -> error: " + e.getMessage());
            }
        }
    }
}
```

1. Range checking fails fast with the expected range and the actual value.
2. The parse error is translated into a user-friendly message, with the original as the cause.

**Run it:** `java ParseQuantityDemo.java`

**Output:**

```text
'3' -> 3
' 12 ' -> 12
'0' -> error: quantity must be 1 to 20, got 0
'two' -> error: 'two' is not a number
'' -> error: quantity is required
```

**What to notice:**

- The range `IllegalArgumentException` isn't caught by `catch (NumberFormatException e)`; the two failures stay distinct.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** FreshCart charges cards through PayLink. Some failures are temporary (503, 429) and worth retrying with the same idempotency key; others (insufficient funds, expired card) must never be retried. The app needs a customer-friendly outcome either way.

**PaymentErrorHandlingDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** FreshCart payments: translate PayLink's errors, retry only what's retryable, and give the app a clear outcome. */
public class PaymentErrorHandlingDemo {

    // ---- What the PayLink HTTP client throws (third-party vocabulary) ----
    static final class PayLinkHttpException extends RuntimeException {
        final int status; final String code;
        PayLinkHttpException(int status, String code) { super("PayLink " + status + " " + code); this.status = status; this.code = code; }
    }

    // ---- Our vocabulary ----
    sealed interface PaymentOutcome {}
    record Paid(String paymentId) implements PaymentOutcome {}
    record Declined(String reasonForCustomer) implements PaymentOutcome {}
    record TryAgainLater(String reasonForCustomer) implements PaymentOutcome {}

    static final class PaymentException extends RuntimeException {
        final boolean retryable;
        PaymentException(String m, boolean retryable, Throwable cause) { super(m, cause); this.retryable = retryable; }
    }

    static final Map<String, String> CUSTOMER_MESSAGES = Map.of(
            "insufficient_funds", "Your bank declined the payment: insufficient funds",
            "card_expired", "This card has expired",
            "do_not_honor", "Your bank declined the payment");

    /** Gateway adapter: translates PayLink errors into PaymentException, classifying retryable ones. */
    static String charge(PayLinkClient client, String orderId, long paise) {
        try {
            return client.charge(orderId, paise);
        } catch (PayLinkHttpException e) {
            boolean retryable = e.status == 429 || e.status >= 500;
            throw new PaymentException(e.code, retryable, e);
        }
    }

    interface PayLinkClient { String charge(String idempotencyKey, long paise); }

    /** Service: bounded retries for retryable errors only, with the order ID as idempotency key. */
    static PaymentOutcome pay(PayLinkClient client, String orderId, long paise, List<String> log) {
        for (int attempt = 1; ; attempt++) {
            try {
                return new Paid(charge(client, orderId, paise));
            } catch (PaymentException e) {
                log.add(orderId + " attempt " + attempt + ": " + e.getMessage() + (e.retryable ? " (retryable)" : ""));
                if (!e.retryable)
                    return new Declined(CUSTOMER_MESSAGES.getOrDefault(e.getMessage(), "Payment failed"));
                if (attempt == 3)
                    return new TryAgainLater("Payments are slow right now. You have not been charged.");
            }
        }
    }

    /** Scripted fake: each call returns or throws the next scripted answer. */
    static PayLinkClient scripted(Object... answers) {
        int[] i = {0};
        return (key, paise) -> {
            Object next = answers[Math.min(i[0]++, answers.length - 1)];
            if (next instanceof PayLinkHttpException e) throw e;
            return (String) next;
        };
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        System.out.println(pay(scripted("pay_001"), "FC-1", 51_800, log));
        System.out.println(pay(scripted(new PayLinkHttpException(503, "unavailable"), "pay_002"), "FC-2", 12_000, log));
        System.out.println(pay(scripted(new PayLinkHttpException(402, "insufficient_funds")), "FC-3", 99_900, log));
        System.out.println(pay(scripted(new PayLinkHttpException(429, "rate_limited")), "FC-4", 5_000, log));
        System.out.println("log:");
        log.forEach(l -> System.out.println("  " + l));
    }
}
```

1. The adapter translates PayLink's HTTP errors into our `PaymentException`, classifying retryable ones.
2. The service retries at most three times, only for retryable errors, using the order ID as idempotency key.
3. Declines become a customer message; the raw code stays in the log.

**Run it:** `java PaymentErrorHandlingDemo.java`

**Output:**

```text
Paid[paymentId=pay_001]
Paid[paymentId=pay_002]
Declined[reasonForCustomer=Your bank declined the payment: insufficient funds]
TryAgainLater[reasonForCustomer=Payments are slow right now. You have not been charged.]
log:
  FC-2 attempt 1: unavailable (retryable)
  FC-3 attempt 1: insufficient_funds
  FC-4 attempt 1: rate_limited (retryable)
  FC-4 attempt 2: rate_limited (retryable)
  FC-4 attempt 3: rate_limited (retryable)
```

**What to notice:**

- The insufficient-funds decline was tried once; retrying a decline can trigger bank fraud checks.
- The rate-limited order stopped after three attempts and told the customer they weren't charged.
- In production, add exponential backoff with jitter between attempts (see [Resilience Patterns](../l4-senior/resilience-patterns.md)).

### Example 3: Production-grade

**Scenario:** GreenValley sends a nightly CSV of supplier prices. Bad configuration must stop the job at startup. Each bad row must be reported with every problem and an error code; good rows are applied. If more than 20% of rows are bad (a broken export), nothing is applied. I/O failures are reported in the job's own terms, and the file is always closed.

**SupplierImportDemo.java**

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GreenValley's nightly supplier price import. Config fails fast; each row becomes a Result with all its problems
 * (notification); good rows are applied, bad ones reported with codes; too many bad rows aborts the whole import
 * (error budget); I/O failures are translated and resources are always closed.
 */
public class SupplierImportDemo {

    // ---------- Fail fast on configuration ----------
    record ImportConfig(int maxErrorPercent, long maxPricePaise) {
        ImportConfig {
            if (maxErrorPercent < 0 || maxErrorPercent > 50) throw new IllegalArgumentException("maxErrorPercent must be 0..50");
            if (maxPricePaise <= 0) throw new IllegalArgumentException("maxPricePaise must be positive");
        }
    }

    // ---------- Row results with error codes ----------
    record Price(String sku, long paise) {}
    record RowError(int line, String code, String detail) {}
    sealed interface RowResult {}
    record Good(Price price) implements RowResult {}
    record Bad(List<RowError> errors) implements RowResult {}

    static RowResult parseRow(int line, String text, ImportConfig cfg) {
        String[] f = text.split(",", -1);
        if (f.length != 2) return new Bad(List.of(new RowError(line, "E_COLUMNS", "expected 2 columns, got " + f.length)));
        List<RowError> errors = new ArrayList<>();
        String sku = f[0].strip();
        if (!sku.matches("[A-Z0-9-]{3,20}")) errors.add(new RowError(line, "E_SKU", "'" + sku + "'"));
        long paise = -1;
        try {
            paise = Math.round(Double.parseDouble(f[1].strip()) * 100);
            if (paise <= 0 || paise > cfg.maxPricePaise()) errors.add(new RowError(line, "E_PRICE_RANGE", f[1].strip()));
        } catch (NumberFormatException e) {
            errors.add(new RowError(line, "E_PRICE_FORMAT", "'" + f[1].strip() + "'"));
        }
        return errors.isEmpty() ? new Good(new Price(sku, paise)) : new Bad(errors);
    }

    // ---------- Translation of I/O failures ----------
    static final class ImportFailedException extends RuntimeException {
        ImportFailedException(String m, Throwable cause) { super(m, cause); }
    }

    sealed interface ImportReport {}
    record Applied(int applied, List<RowError> rejected) implements ImportReport {}
    record Aborted(String reason, List<RowError> rejected) implements ImportReport {}

    static ImportReport runImport(BufferedReader source, ImportConfig cfg, Map<String, Long> catalogue) {
        List<Price> good = new ArrayList<>();
        List<RowError> rejected = new ArrayList<>();
        int rows = 0;
        try (source) {
            String header = source.readLine();
            if (!"sku,price".equals(header)) throw new ImportFailedException("unexpected header: " + header, null);
            String text;
            int line = 1;
            while ((text = source.readLine()) != null) {
                line++;
                if (text.isBlank()) continue;
                rows++;
                switch (parseRow(line, text, cfg)) {
                    case Good g -> good.add(g.price());
                    case Bad b -> rejected.addAll(b.errors());
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new ImportFailedException("could not read supplier file", e);
        }
        long badRows = rejected.stream().mapToInt(RowError::line).distinct().count();
        if (rows == 0 || badRows * 100 > (long) rows * cfg.maxErrorPercent())
            return new Aborted(badRows + " of " + rows + " rows invalid, over the " + cfg.maxErrorPercent() + "% budget; nothing applied", rejected);
        good.forEach(p -> catalogue.put(p.sku(), p.paise()));
        return new Applied(good.size(), rejected);
    }

    static void print(String title, ImportReport r) {
        System.out.println(title + ": " + switch (r) {
            case Applied a -> "applied " + a.applied() + ", rejected " + a.rejected().size() + " problem(s)";
            case Aborted a -> "ABORTED, " + a.reason();
        });
        List<RowError> errors = r instanceof Applied a ? a.rejected() : ((Aborted) r).rejected();
        errors.forEach(e -> System.out.println("  line " + e.line() + " " + e.code() + " " + e.detail()));
    }

    public static void main(String[] args) {
        ImportConfig cfg = new ImportConfig(20, 500_000);
        Map<String, Long> catalogue = new LinkedHashMap<>();

        String mostlyGood = "sku,price\nTOMATO-1KG,32.50\nONION-1KG,41\nspinach,25\nOKRA-500G,abc\n\nPOTATO-1KG,28\nCARROT-1KG,39.9\nBEANS-500G,44\nPEAS-500G,61\nCORN-2,22\nGARLIC-200G,58\n";
        print("night 1", runImport(new BufferedReader(new StringReader(mostlyGood)), cfg, catalogue));
        System.out.println("catalogue now has " + catalogue.size() + " prices, TOMATO-1KG=" + catalogue.get("TOMATO-1KG"));

        String broken = "sku,price\nTOMATO-1KG;30\nONION-1KG;40\nPOTATO-1KG,27\n";
        print("night 2", runImport(new BufferedReader(new StringReader(broken)), cfg, catalogue));
        System.out.println("TOMATO-1KG still " + catalogue.get("TOMATO-1KG"));

        BufferedReader failing = new BufferedReader(new StringReader("sku,price\n")) {
            @Override public String readLine() throws IOException { throw new IOException("SFTP connection reset"); }
        };
        try {
            runImport(failing, cfg, catalogue);
        } catch (ImportFailedException e) {
            System.out.println("night 3: " + e.getMessage() + " (cause: " + e.getCause().getMessage() + ")");
        }
        try {
            new ImportConfig(80, 500_000);
        } catch (IllegalArgumentException e) {
            System.out.println("startup: " + e.getMessage());
        }
    }
}
```

1. Fail fast: impossible configuration is rejected at construction.
2. Each row becomes a `Good` or a `Bad` with all its errors (Result + Notification).
3. try-with-resources closes the reader whatever happens; I/O errors are translated with their cause.
4. An error budget: a mostly broken file means the export is broken, so nothing is applied.
5. Only after the decision are good rows applied, so a run is all-or-budgeted, never half-done by accident.

**Run it:** `java SupplierImportDemo.java`

**Output:**

```text
night 1: applied 8, rejected 2 problem(s)
  line 4 E_SKU 'spinach'
  line 5 E_PRICE_FORMAT 'abc'
catalogue now has 8 prices, TOMATO-1KG=3250
night 2: ABORTED, 2 of 3 rows invalid, over the 20% budget; nothing applied
  line 2 E_COLUMNS expected 2 columns, got 1
  line 3 E_COLUMNS expected 2 columns, got 1
TOMATO-1KG still 3250
night 3: could not read supplier file (cause: SFTP connection reset)
startup: maxErrorPercent must be 0..50
```

**What to notice:**

- Night 1 applied eight prices and reported two bad rows with codes the supplier can act on.
- Night 2's file used semicolons; the budget stopped it, and yesterday's prices stayed.
- Night 3's network error surfaced as `ImportFailedException` with the SFTP cause attached.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Checked exceptions | Compiler-enforced handling | Signature noise; awkward with lambdas |
| Unchecked exceptions | Clean signatures | Easy to forget to handle |
| Exception translation | Layer independence; rich causes | Boilerplate; risk of over-wrapping |
| Result types | Exhaustive, explicit, cheap | Verbose without language support; mixed styles |
| Fail fast | Errors near their cause | Less tolerant of partial data |
| Notification | All errors at once | More code than throwing |

## 8. When to Use / When NOT to Use

**Use them when:**

- failures cross layers or third-party boundaries (translation);
- failure is a normal outcome (Result);
- input comes from users, files or configuration (fail fast, notification).

**Don't use them when:**

- the "error" is just absence; `Optional` is enough;
- wrapping adds no information (rethrowing the same meaning one layer up);
- in tight inner loops, don't use exceptions for control flow at all.

## 9. Best Practices

1. **Never swallow exceptions**; at minimum log with the cause, and usually rethrow. *(Consensus.)*
2. **Always keep the cause when wrapping.** *(Consensus.)*
3. **Log once, where handled; include context (IDs), not personal data.** *(Consensus.)*
4. **Classify errors: retryable vs not, client vs server.** *(Consensus.)*
5. **Fail fast on config at startup.** *(Consensus.)*
6. **Return problem details (RFC 9457) from APIs with stable error codes.** *(Consensus.)*
7. **Prefer Results for expected business outcomes in new code.** *(Opinion, increasingly common with Java 21.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Retrying a non-retryable error (production failure mode)**  
> **Symptom:** customers saw five "payment declined" SMSes from their bank, and some cards were blocked for suspected fraud.
> **Root cause:** a generic retry wrapped every payment exception, including hard declines.
> **Fix:** classify errors at the gateway adapter; retry only timeouts, 429 and 5xx, with idempotency keys and backoff.

> 🚨 **Swallowed exception**  
> **Symptom:** nightly stock sync "succeeded" for a week while stock drifted; no errors in logs.
> **Root cause:** `catch (Exception e) { }` around the sync call, added "temporarily" to stop a crash.
> **Fix:** remove it; handle specific exceptions; alert on failure.

> 🚨 **Lost cause**  
> **Symptom:** logs show `ServiceException: payment failed` with no clue why.
> **Root cause:** `throw new ServiceException("payment failed")` without passing `e`.
> **Fix:** always pass the cause; a static-analysis rule (Error Prone, Sonar) can enforce it.

> 🚨 **Log and rethrow at every layer**  
> **Symptom:** one failure produces six identical stack traces; alerts fire six times.
> **Root cause:** each layer logs and rethrows.
> **Fix:** log once, where the exception is finally handled.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `server.error.include-stacktrace` (Spring Boot) | `never` | Stack traces in error responses | Never in production | Information leak |
| `spring.mvc.problemdetails.enabled` | `false` | RFC 9457 responses | Enable for APIs | Response format change |
| `-XX:-OmitStackTraceInFastThrow` | omit enabled | Keeps stack traces for frequent JVM exceptions | When logs show traceless NPEs | Slight cost |
| Retry max attempts | — | Bounds retries | Per dependency | Retry storms |
| Import error budget | — | Max bad rows before aborting | Per data source | Too low: noisy aborts; too high: bad data |
| Logback `maxDepthPerThrowable` | unlimited | Truncates huge traces | Very deep chains | Losing the root cause |

## 12. Security Considerations

- Never return stack traces, SQL or internal class names to clients; map to generic messages with an error ID you can look up.
- Distinguish user-facing messages from log messages; logs may contain IDs, never passwords, card numbers or OTPs.
- Uniform error messages for authentication ("invalid phone or OTP") avoid revealing which part was wrong.
- Validate on the server even if the client validates; fail fast on malformed input before it reaches parsers (size limits, content types).
- Error budgets and rate limits stop attackers from probing with malformed requests cheaply.

## 13. Performance & Scalability

Creating an exception captures a stack trace, which costs microseconds; throwing thousands per second (exceptions as control flow) shows up in profiles. For high-volume expected failures (validation, parsing), Results or a preallocated exception without a stack trace (`super(msg, null, false, false)`) are cheaper. Retries multiply load; bound them and add jitter. Large notification lists for huge files should be capped ("first 1,000 errors").

## 14. Testing It

Test error paths as carefully as happy paths: each error code, the retry classification, the budget boundary, the cause chain and that resources are closed:

**SupplierImportTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplierImportTest {

    static final SupplierImportDemo.ImportConfig CFG = new SupplierImportDemo.ImportConfig(20, 500_000);

    static SupplierImportDemo.ImportReport run(String csv, Map<String, Long> catalogue) {
        return SupplierImportDemo.runImport(new BufferedReader(new StringReader(csv)), CFG, catalogue);
    }

    @Test
    void rowCollectsAllItsProblems() {
        var result = SupplierImportDemo.parseRow(7, "bad sku, -3", CFG);
        var bad = assertInstanceOf(SupplierImportDemo.Bad.class, result);
        assertEquals(List.of("E_SKU", "E_PRICE_RANGE"), bad.errors().stream().map(SupplierImportDemo.RowError::code).toList());
    }

    @Test
    void overBudgetAppliesNothing() {
        Map<String, Long> catalogue = new HashMap<>(Map.of("A-1", 100L));
        var report = run("sku,price\nA-1,2\nx,1\ny,1\n", catalogue);
        assertInstanceOf(SupplierImportDemo.Aborted.class, report);
        assertEquals(100L, catalogue.get("A-1"));
    }

    @Test
    void withinBudgetAppliesGoodRowsAndReportsBadOnes() {
        Map<String, Long> catalogue = new HashMap<>();
        StringBuilder csv = new StringBuilder("sku,price\n");
        for (int i = 0; i < 9; i++) csv.append("SKU-").append(i).append(",10\n");
        csv.append("bad,10\n");
        var applied = assertInstanceOf(SupplierImportDemo.Applied.class, run(csv.toString(), catalogue));
        assertEquals(9, applied.applied());
        assertEquals(1, applied.rejected().size());
        assertEquals(1000L, catalogue.get("SKU-0"));
    }

    @Test
    void ioFailuresAreTranslatedAndTheReaderIsClosed() {
        boolean[] closed = {false};
        BufferedReader failing = new BufferedReader(new StringReader("")) {
            @Override public String readLine() throws IOException { throw new IOException("reset"); }
            @Override public void close() { closed[0] = true; }
        };
        var e = assertThrows(SupplierImportDemo.ImportFailedException.class,
                () -> SupplierImportDemo.runImport(failing, CFG, new HashMap<>()));
        assertInstanceOf(IOException.class, e.getCause());
        assertTrue(closed[0]);
    }

    @Test
    void badConfigFailsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new SupplierImportDemo.ImportConfig(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SupplierImportDemo.ImportConfig(10, 0));
    }

    @Test
    void paymentsRetryOnlyRetryableErrorsAndAtMostThreeTimes() {
        int[] calls = {0};
        List<String> log = new ArrayList<>();
        PaymentErrorHandlingDemo.PayLinkClient alwaysBusy = (k, p) -> { calls[0]++; throw new PaymentErrorHandlingDemo.PayLinkHttpException(503, "unavailable"); };
        assertInstanceOf(PaymentErrorHandlingDemo.TryAgainLater.class, PaymentErrorHandlingDemo.pay(alwaysBusy, "O", 1, log));
        assertEquals(3, calls[0]);

        calls[0] = 0;
        PaymentErrorHandlingDemo.PayLinkClient declined = (k, p) -> { calls[0]++; throw new PaymentErrorHandlingDemo.PayLinkHttpException(402, "card_expired"); };
        assertEquals(new PaymentErrorHandlingDemo.Declined("This card has expired"), PaymentErrorHandlingDemo.pay(declined, "O", 1, log));
        assertEquals(1, calls[0]);
    }

    @Test
    void notificationReportsEveryField() {
        var n = NotificationPatternDemo.validate(new NotificationPatternDemo.AddressForm("", "1", "1", ""));
        assertEquals(4, n.errors().size());
    }
}
```

## 15. Observability & Operations

Count errors by type and code (Micrometer counters tagged with `error.code`), not just "500s". Give every unexpected error a correlation ID returned to the client and logged with the full cause chain. Alert on rates, not individual errors. For batch jobs, emit the report (applied, rejected, aborted) as a metric and keep rejected rows for replay. Use structured logging so causes and codes are searchable.

## 16. Version Notes

| Java | Change |
|------|--------|
| 1.0 | Checked and unchecked exceptions |
| 1.4 | Exception chaining (`getCause`), `assert` |
| 7 | try-with-resources, multi-catch, suppressed exceptions |
| 8 | `UncheckedIOException`, `Optional` |
| 14 | Helpful `NullPointerException` messages (on by default from 15) |
| 17 | Sealed classes: exhaustive error hierarchies |
| 21 | Record patterns and pattern matching for `switch`: ergonomic Result types |

Spring Framework 6 added `ProblemDetail` and `ErrorResponse` for RFC 7807 (now RFC 9457).

## 17. Expert Deep Dive

- **Error ownership.** Decide per failure who can act: the user (validation), the caller (business outcome), an operator (infrastructure). That decides type, message and logging level.
- **Exceptions vs Results at scale.** Results make error handling explicit in domain code; exceptions keep infrastructure failures out of every signature. A common split: Results inside the domain, exceptions for infrastructure, translation at the boundary.
- **Suppressed exceptions.** try-with-resources attaches exceptions from `close()` as suppressed rather than hiding the original; read them in logs (`getSuppressed`).
- **Idempotency and retries.** Retrying is safe only for idempotent operations; payment and order APIs need idempotency keys so a retry after a timeout doesn't double-charge.
- **Error budgets beyond SRE.** The import's "abort above 20%" rule is the same idea as an SLO error budget: tolerate some failure, stop when the pattern says something is systemically wrong.

> 📖 **War story: the price list that zeroed the store**  
> GreenValley's supplier changed their export to use a comma as the decimal separator. FreshCart's import caught `NumberFormatException` per row and defaulted the price to 0 "so the job wouldn't crash". Overnight, 1,400 products were priced at Rs 0; by morning, 300 orders had been placed for free. **Fixes:** the default-to-zero catch was removed; rows became Results with error codes; an error budget aborted the run when more than 20% of rows failed; and a sanity check compared each new price with the old one (reject changes over 50% without review). **Lesson:** a catch block that "keeps the job running" with a made-up value turns a visible failure into silent data corruption.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Checked vs unchecked?** Checked must be **declared or caught**; unchecked (`RuntimeException`) needn't be.
2. **What's try-with-resources?** Automatically **closes resources**, even on exceptions.
3. **Why not `catch (Exception e) {}`?** It **hides failures**; bugs become silent.
4. **What's exception chaining?** Wrapping with the **original as cause**.
5. **What's fail fast?** **Stop early** with a clear error when something is invalid.

**▸ 🟡 L3**

1. **When would you use a Result type instead of exceptions?** For **expected business outcomes** that callers must handle.
2. **What's exception translation?** Rethrowing in the **current layer's vocabulary**, keeping the cause.
3. **How do you validate a form?** **Notification pattern**: collect all errors, return them together.
4. **Which errors should be retried?** **Transient** ones (timeouts, 429, 5xx) on **idempotent** operations.
5. **Where should you log exceptions?** **Once**, where handled.

**▸ 🔴 L4–L5**

1. **Design error handling for a payments service.** **Classify retryable, idempotency keys, translate gateway errors, customer vs log messages, metrics by code.**
2. **Exceptions or Results in a new code base?** **Results in the domain, exceptions for infrastructure**, translate at boundaries; be consistent.
3. **A batch job sometimes corrupts data on bad input; how do you redesign it?** **Per-row results, error budget, apply after decision, sanity checks, replayable rejects.**
4. **How do you expose errors in a public API?** **RFC 9457 problem details**, stable codes, correlation IDs, no internals.
5. **What's the cost of exceptions?** **Stack-trace capture**; avoid for control flow, or disable stack traces for hot expected errors.

**▸ 💻 Hands-on**

**Task:** Add a sanity check to `SupplierImportDemo`: reject (code `E_PRICE_JUMP`) any row whose new price differs from the current catalogue price by more than 50%.

> ✅ **Solution**  
> ```java
> static RowResult checkJump(RowResult r, int line, Map<String, Long> catalogue) {
>     if (!(r instanceof Good g)) return r;
>     Long old = catalogue.get(g.price().sku());
>     if (old == null) return r;
>     long diff = Math.abs(g.price().paise() - old);
>     return diff * 2 > old
>             ? new Bad(List.of(new RowError(line, "E_PRICE_JUMP", old + " -> " + g.price().paise())))
>             : r;
> }
> // in runImport: switch (checkJump(parseRow(line, text, cfg), line, catalogue)) 
> ```

## 19. Practice

- [ ] Convert `ResultTypeDemo`'s errors into an RFC 9457 problem-details JSON response.
    > 💡 **Hint**  
    > Map each error type to a `type` URI, `title`, `status` and `detail`.

- [ ] Add exponential backoff with jitter to `PaymentErrorHandlingDemo`.
    > 💡 **Hint**  
    > `delay = base * 2^attempt + random(0, base)`, capped.

- [ ] Find a `catch` block in your code base that swallows exceptions and fix it.
    > 💡 **Hint**  
    > Search for `catch (Exception` and empty blocks.

- [ ] Write a test that the import report lists both errors when a row has a bad SKU and a bad price.
    > 💡 **Hint**  
    > `parseRow` already collects both.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Who can act on this failure? Handle it there; let it propagate until then.
> - Checked = recoverable, expected; unchecked = bugs, infrastructure.
> - Translate at boundaries; always keep the cause; log once.
> - Results for expected outcomes; sealed + switch makes handling exhaustive.
> - Fail fast on config and preconditions; Notification for user input and rows.
> - Retry only transient errors on idempotent operations.
>
> ```java
> try {
>     return client.charge(orderId, paise);
> } catch (PayLinkHttpException e) {
>     throw new PaymentException(e.code, e.status == 429 || e.status >= 500, e);   // translate, classify, keep cause
> }
> ```

## 21. Further Reading

- Joshua Bloch, *Effective Java* (3rd ed.), Chapter 10, "Exceptions".
- Martin Fowler, ["Replacing Throwing Exceptions with Notification in Validations"](https://martinfowler.com/articles/replaceThrowWithNotification.html).
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457).
- Michael Nygard, *Release It!* (2nd ed., 2018), on stability patterns and failure handling.
- [Spring Framework: Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).

---

[← Previous: UI and Mobile Patterns](ui-and-mobile-patterns.md) · [Next: Anti-Patterns and Code Smells →](anti-patterns-and-code-smells.md)
