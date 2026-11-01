# Designing Frameworks and Libraries

L5 🔴 Staff / Principal · ⏱ 40 min read · Prerequisites: [Dependency Injection](../l2-practitioner/dependency-injection.md), [JVM Internals Behind Patterns](jvm-internals-behind-patterns.md), [Evolutionary Architecture](evolutionary-architecture.md)

> 📌 **TL;DR**  
> A library's API is a promise other teams build on. Give them **extension points** (an SPI discovered with `ServiceLoader`, plugins with a versioned contract, annotations the framework scans) and let the framework own the flow (**inversion of control**). Keep everything else hidden behind interfaces, static factories and **JPMS exports**. Evolve without breaking anyone: add default methods and overloads, deprecate before removing, and remember that **source-compatible is not binary-compatible**. Automate the promise with an API compatibility check that enforces semantic versioning in CI.

## 1. What It Is

**Plain English.** Application code solves one product's problem. Framework and library code is used by many teams you may never meet, often in ways you didn't expect, and upgraded on their schedule rather than yours. Designing it means deciding what is public, how others extend it, and how it changes over time without breaking them.

**Definition.**

| Concern | Main tools |
|---------|------------|
| Extension | SPI + `ServiceLoader`, plugins, annotations, callbacks |
| Control flow | Inversion of control: the framework calls user code |
| Encapsulation | Interfaces, static factories, package-private, JPMS `exports` |
| Evolution | Default methods, overloads, deprecation, semantic versioning |
| Verification | Binary compatibility checks (japicmp, revapi), contract tests |

## 2. Why It Exists

At FreshCart, the pricing library is used by 14 services and two partners (QuickDrop and ParcelGo) through the Partner SDK. Every accidental public class becomes something someone depends on; every renamed method becomes a failed deploy in a team that didn't change anything. Libraries live much longer than the features that created them. Good framework design reduces coordination: teams extend without asking you, and you change internals without asking them.

## 3. Mental Model / Analogy

**Analogy.** A power socket. The socket's shape (the API) is fixed and standardised; any appliance (plugin) with the right plug works, and the electricity company can rebuild the power station (internals) without anyone changing their kettles. Changing the socket shape is a big event, announced years ahead, with adapters in between.

**Technical model.** Separate the **API** (what callers use), the **SPI** (what extenders implement) and the **implementation** (everything else). API and SPI are versioned contracts; the implementation is free to change.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Service Provider Interface and ServiceLoader

**Description.** An **SPI** is an interface the library defines for others to implement. Providers ship a file `META-INF/services/<interface name>` listing their implementation classes (or, in modules, `provides ... with ...`). `ServiceLoader.load(Interface.class)` finds and instantiates them at run time, so the library never depends on the providers at compile time. JDBC drivers, `java.time` zone providers, SLF4J bindings and Jackson modules all work this way.

**Advantages / features.** Zero compile-time coupling; add a capability by adding a jar; lazy discovery with `stream()` and `Provider::type`.

**ServiceLoaderDemo.java**

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Service Provider Interface (SPI) with java.util.ServiceLoader: the library defines an interface, providers ship
 * implementations plus a META-INF/services file naming them, and the library discovers them at run time without
 * compile-time dependencies. Here the services file is written to a temp folder, exactly as it would sit in a jar.
 */
public class ServiceLoaderDemo {

    /** The SPI owned by FreshCart's delivery library. */
    public interface DeliveryPartner {
        String name();
        long quotePaise(int distanceKm);
    }

    public static class QuickDropPartner implements DeliveryPartner {
        public String name() { return "QuickDrop"; }
        public long quotePaise(int km) { return 2500 + 400L * km; }
    }

    public static class ParcelGoPartner implements DeliveryPartner {
        public String name() { return "ParcelGo"; }
        public long quotePaise(int km) { return 2800 + 350L * km; }
    }

    public static class BrokenPartner implements DeliveryPartner {
        public BrokenPartner() { throw new IllegalStateException("missing API key"); }
        public String name() { return "Broken"; }
        public long quotePaise(int km) { return 0; }
    }

    public static void main(String[] args) throws Exception {
        Path jarLike = Files.createTempDirectory("spi");
        Path services = jarLike.resolve("META-INF/services/" + DeliveryPartner.class.getName());
        Files.createDirectories(services.getParent());
        Files.writeString(services, """
                # one provider class per line, as shipped inside each partner's jar
                ServiceLoaderDemo$QuickDropPartner
                ServiceLoaderDemo$ParcelGoPartner
                ServiceLoaderDemo$BrokenPartner
                """);
        System.out.println("META-INF/services/" + DeliveryPartner.class.getName());

        try (URLClassLoader loader = new URLClassLoader(new URL[] {jarLike.toUri().toURL()}, ServiceLoaderDemo.class.getClassLoader())) {
            ServiceLoader<DeliveryPartner> partners = ServiceLoader.load(DeliveryPartner.class, loader);
            partners.stream().forEach(p -> System.out.println("found provider type " + p.type().getSimpleName() + " (not instantiated yet)"));

            for (var it = partners.iterator(); ; ) {
                try {
                    if (!it.hasNext()) break;
                    DeliveryPartner p = it.next();
                    System.out.println(p.name() + " quote for 5 km: Rs " + p.quotePaise(5) / 100.0);
                } catch (ServiceConfigurationError e) {
                    System.out.println("skipped a provider: " + e.getCause().getMessage());
                }
            }
        }
    }
}
```

1. The provider configuration file, one fully qualified class name per line, exactly as it sits in a jar.
2. `stream()` exposes provider **types** without instantiating them, useful for filtering by annotation.
3. A provider that fails to construct surfaces as `ServiceConfigurationError`; the loader keeps going.

**Run it:** `java ServiceLoaderDemo.java`

```text
META-INF/services/ServiceLoaderDemo$DeliveryPartner
found provider type QuickDropPartner (not instantiated yet)
found provider type ParcelGoPartner (not instantiated yet)
found provider type BrokenPartner (not instantiated yet)
QuickDrop quote for 5 km: Rs 45.0
ParcelGo quote for 5 km: Rs 45.5
skipped a provider: missing API key
```

| Aspect | ServiceLoader | DI container (Spring) |
|--------|---------------|-----------------------|
| Dependency | JDK only | Framework |
| Provider needs | Public no-arg constructor or `provider()` method | Bean definition |
| Ordering | Classpath order (unreliable) | `@Order`, `@Priority` |
| Config / injection | None | Full |
| Best for | Libraries, drivers, plugins | Applications |

**Use** an SPI when third parties add implementations you can't know at compile time. **Avoid** relying on classpath order for priority; add an explicit `priority()` to the SPI.
**Real-world Java:** JDBC 4 `DriverManager`, `java.nio.file.spi.FileSystemProvider`, SLF4J 2 providers, Micrometer and OpenTelemetry exporters.
**Best practice:** keep SPIs tiny and separate from the caller API. **Gotcha:** shading or fat-jar plugins that merge jars can overwrite `META-INF/services` files; use the merging transformer (Maven Shade `ServicesResourceTransformer`).

### Plugin architectures

**Description.** A plugin system adds a **host** with a lifecycle, named **extension points** (hooks), and a **contract version**. The host decides which plugins load, in what order, with which permissions, and how failures are contained. Discovery can be ServiceLoader, a folder of jars with separate class loaders, or configuration.

**Advantages / features.** Teams and partners ship features independently; the core stays small; features can be enabled per customer or region.

**PluginArchitectureDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * Plugin architecture: the host owns the lifecycle and the extension points; plugins declare which API version they
 * were built for and contribute to named hooks. The host rejects incompatible plugins, isolates failures, and stops
 * plugins in reverse start order.
 */
public class PluginArchitectureDemo {

    static final int HOST_API_MAJOR = 2;

    interface Plugin {
        String id();
        int apiMajor();
        void start(Host host);
        default void stop() {}
    }

    interface BasketHook { long adjust(long basketPaise); }

    static final class Host {
        private final List<BasketHook> basketHooks = new ArrayList<>();
        private final List<Plugin> started = new ArrayList<>();

        void registerBasketHook(BasketHook hook) { basketHooks.add(hook); }

        void install(Plugin plugin) {
            if (plugin.apiMajor() != HOST_API_MAJOR) {
                System.out.println("rejected " + plugin.id() + ": built for API " + plugin.apiMajor() + ", host is " + HOST_API_MAJOR);
                return;
            }
            try {
                plugin.start(this);
                started.add(plugin);
                System.out.println("started " + plugin.id());
            } catch (RuntimeException e) {
                System.out.println("failed to start " + plugin.id() + ": " + e.getMessage() + " (host keeps running)");
            }
        }

        long priceBasket(long paise) {
            for (BasketHook hook : basketHooks) paise = hook.adjust(paise);
            return paise;
        }

        void shutdown() {
            for (int i = started.size() - 1; i >= 0; i--) {
                started.get(i).stop();
                System.out.println("stopped " + started.get(i).id());
            }
        }
    }

    record SimplePlugin(String id, int apiMajor, BasketHook hook, boolean failOnStart) implements Plugin {
        public void start(Host host) {
            if (failOnStart) throw new IllegalStateException("cannot reach config service");
            host.registerBasketHook(hook);
        }
    }

    public static void main(String[] args) {
        Host host = new Host();
        host.install(new SimplePlugin("greenvalley-organic-discount", 2, p -> p * 95 / 100, false));
        host.install(new SimplePlugin("dairyday-subscription", 1, p -> p - 2000, false));
        host.install(new SimplePlugin("festival-banner", 2, p -> p, true));
        host.install(new SimplePlugin("small-basket-fee", 2, p -> p < 50000 ? p + 2900 : p, false));
        System.out.println("basket Rs 400.00 -> Rs " + host.priceBasket(40000) / 100.0);
        host.shutdown();
    }
}
```

1. Each plugin declares the API major version it was built against.
2. A plugin that fails to start is isolated; the host and other plugins keep running.
3. Plugins stop in reverse start order, so later plugins can depend on earlier ones.

**Run it:** `java PluginArchitectureDemo.java`

```text
started greenvalley-organic-discount
rejected dairyday-subscription: built for API 1, host is 2
failed to start festival-banner: cannot reach config service (host keeps running)
started small-basket-fee
basket Rs 400.00 -> Rs 409.0
stopped small-basket-fee
stopped greenvalley-organic-discount
```

| Decision | Options |
|----------|---------|
| Discovery | ServiceLoader, folder scan, config, registry service |
| Isolation | Same class loader, class loader per plugin, separate process |
| Contract | Interfaces + API version, manifest with capabilities |
| Failure handling | Fail host, skip plugin, disable after N errors |
| Ordering | Explicit priority, dependency graph |

**Use** plugins when extension is a product feature (many contributors, optional features). **Avoid** them for code one team owns and deploys together; plain modules are simpler.
**Real-world Java:** Jenkins, IntelliJ Platform, Eclipse (OSGi), Elasticsearch and Keycloak SPIs, Gradle plugins.
**Best practice:** version the plugin API independently and check compatibility at load time. **Gotcha:** sharing library classes between host and plugins with separate class loaders causes `ClassCastException` ("X cannot be cast to X") when both bundle the same library.

### Inversion of control in your own framework

**Description.** "Don't call us, we'll call you." The framework owns the main flow and calls user code at defined points: callbacks, template methods, annotated handlers, or dependency injection where the framework constructs objects and supplies their dependencies. The user declares *what*; the framework decides *when* and *how*.

**Advantages / features.** Consistent cross-cutting behaviour (transactions, metrics, retries) in one place; users write less wiring; the framework can change orchestration without user changes.

**InversionOfControlDemo.java**

```java
import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Inversion of control in your own framework: user code declares what it needs (constructor parameters) and the
 * framework decides when and how objects are built and called. This tiny container resolves constructor dependencies
 * recursively, caches singletons and reports dependency cycles with the full path.
 */
public class InversionOfControlDemo {

    static final class Container {
        private final Map<Class<?>, Object> singletons = new HashMap<>();
        private final Deque<Class<?>> resolving = new ArrayDeque<>();

        <T> T get(Class<T> type) {
            Object existing = singletons.get(type);
            if (existing != null) return type.cast(existing);
            if (resolving.contains(type)) {
                StringBuilder path = new StringBuilder();
                resolving.descendingIterator().forEachRemaining(c -> path.append(c.getSimpleName()).append(" -> "));
                throw new IllegalStateException("dependency cycle: " + path + type.getSimpleName());
            }
            resolving.push(type);
            try {
                Constructor<?> ctor = type.getDeclaredConstructors()[0];
                Object[] deps = new Object[ctor.getParameterCount()];
                for (int i = 0; i < deps.length; i++) deps[i] = get(ctor.getParameterTypes()[i]);
                Object created = ctor.newInstance(deps);
                System.out.println("created " + type.getSimpleName());
                singletons.put(type, created);
                return type.cast(created);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            } finally {
                resolving.pop();
            }
        }
    }

    record PriceRepository() {}
    record TaxRules() {}
    record PricingService(PriceRepository prices, TaxRules tax) {}
    record CheckoutService(PricingService pricing, PriceRepository prices) {}

    record AService(BService b) {}
    record BService(AService a) {}

    public static void main(String[] args) {
        Container container = new Container();
        CheckoutService checkout = container.get(CheckoutService.class);
        System.out.println("same PriceRepository shared: " + (checkout.prices() == checkout.pricing().prices()));
        try {
            container.get(AService.class);
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }
    }
}
```

1. Cycle detection reports the full path, which is what users need to fix it.
2. Constructor parameters declare dependencies; the container resolves them recursively.
3. Instances are cached, so every consumer shares one `PriceRepository` (singleton scope).

**Run it:** `java InversionOfControlDemo.java`

```text
created PriceRepository
created TaxRules
created PricingService
created CheckoutService
same PriceRepository shared: true
dependency cycle: AService -> BService -> AService
```

| IoC style | Example | Trade-off |
|-----------|---------|-----------|
| Callback / functional | `executor.submit(task)` | Simple, explicit |
| Template method | `AbstractController.handle()` | Inheritance coupling |
| Annotations + scanning | `@Step`, `@EventListener` | Magic; needs good errors |
| Dependency injection | Spring, Guice, Dagger | Startup cost (runtime DI) or build complexity (compile-time DI) |

**Use** IoC when the framework must guarantee ordering, lifecycle or cross-cutting concerns. **Avoid** writing your own DI container for applications; use an existing one and keep your framework DI-agnostic (accept objects, don't create them).
**Real-world Java:** Spring, Jakarta CDI, Micronaut and Dagger (compile-time DI), JUnit 5 extensions.
**Best practice:** make every framework decision observable (startup report, clear error messages with names). **Gotcha:** too much magic: when users can't predict which code runs, they stop trusting the framework.

### Hiding internals with modules

**Description.** Anything public is API by default, whether you meant it or not. Hide implementation with package-private classes, interfaces plus static factories, and, across jars, **JPMS**: `exports` only API packages, `exports ... to` for friend modules (test kits), `opens` only for packages that need reflection, and `provides ... with` to supply SPI implementations from hidden packages.

**Advantages / features.** Enforced by compiler and runtime; makes "internal" a fact rather than a naming convention.

**ModuleEncapsulationDemo.java**

```java
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Hiding internals with JPMS: a library module exports only its API package, exports an internal package to one
 * named friend module (a test kit), and provides an SPI implementation from a package nobody can import. The demo
 * writes the modules to a temp folder and compiles each consumer with the real javac.
 */
public class ModuleEncapsulationDemo {

    static final Map<String, String> LIBRARY = new TreeMap<>(Map.of(
            "module-info.java", """
                    module freshcart.pricing {
                        exports com.freshcart.pricing.api;
                        exports com.freshcart.pricing.internal to freshcart.testkit;
                        provides com.freshcart.pricing.api.DiscountRule with com.freshcart.pricing.internal.FestiveRule;
                    }""",
            "com/freshcart/pricing/api/DiscountRule.java", "package com.freshcart.pricing.api; public interface DiscountRule { long apply(long paise); }",
            "com/freshcart/pricing/internal/FestiveRule.java", "package com.freshcart.pricing.internal; public class FestiveRule implements com.freshcart.pricing.api.DiscountRule { public long apply(long p) { return p * 90 / 100; } }"));

    public static void main(String[] args) throws IOException {
        String usesApi = "import com.freshcart.pricing.api.DiscountRule; class Cart { long total(DiscountRule r) { return r.apply(10000); } }";
        String usesInternal = "import com.freshcart.pricing.internal.FestiveRule; class Cart { long total() { return new FestiveRule().apply(10000); } }";

        attempt("freshcart.ordering", "uses the exported API", usesApi);
        attempt("freshcart.ordering", "imports the internal package", usesInternal);
        attempt("freshcart.testkit", "imports the internal package (qualified export)", usesInternal);
    }

    static void attempt(String module, String what, String body) throws IOException {
        Path root = Files.createTempDirectory("modules");
        write(root.resolve("freshcart.pricing"), LIBRARY);
        String pkg = module.replace("freshcart.", "com.freshcart.");
        write(root.resolve(module), Map.of(
                "module-info.java", "module " + module + " { requires freshcart.pricing; }",
                pkg.replace('.', '/') + "/Cart.java", "package " + pkg + "; " + body));
        StringWriter diagnostics = new StringWriter();
        boolean ok = compile(root, diagnostics);
        System.out.println(module + " " + what + ": " + (ok ? "compiles" : "compile error"));
        diagnostics.toString().lines().filter(l -> l.contains("error:") || l.contains("warning:"))
                .map(l -> l.substring(l.indexOf(l.contains("error:") ? "error:" : "warning:"))).distinct()
                .forEach(l -> System.out.println("  " + l));
    }

    static void write(Path dir, Map<String, String> files) throws IOException {
        for (var e : files.entrySet()) {
            Path p = dir.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
    }

    static boolean compile(Path root, StringWriter out) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> sources;
        try (var walk = Files.walk(root)) { sources = walk.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList(); }
        List<String> options = new ArrayList<>(List.of("--module-source-path", root.toString(), "-d", root.resolve("out").toString()));
        return javac.getTask(out, null, null, options, null, javac.getStandardFileManager(null, null, null).getJavaFileObjectsFromStrings(sources)).call();
    }
}
```

1. The library exports its API, exports internals only to a named friend module, and provides an SPI implementation from a hidden package.
2. A normal consumer that imports the internal package fails to compile.
3. The friend module named in the qualified export can use the internal package.

**Run it:** `java ModuleEncapsulationDemo.java`

```text
freshcart.ordering uses the exported API: compiles
  warning: [module] module not found: freshcart.testkit
freshcart.ordering imports the internal package: compile error
  error: package com.freshcart.pricing.internal is not visible
  warning: [module] module not found: freshcart.testkit
freshcart.testkit imports the internal package (qualified export): compiles
```

| Technique | Scope | Strength |
|-----------|-------|----------|
| Package-private | Within one package | Compiler-enforced |
| `internal` package naming | Convention | Weak (people import it anyway) |
| JPMS `exports` | Across modules | Compiler + runtime |
| `exports ... to` | Named friends | Compiler + runtime |
| `opens` | Reflection only | Runtime |
| ArchUnit rules | Classpath projects | Build-time |

**Use** JPMS for libraries with real external consumers; ArchUnit or package-private for classpath-only projects. **Avoid** exporting a package "temporarily"; someone will depend on it.
**Real-world Java:** the JDK itself since Java 9 (`jdk.internal.*` hidden), Jackson and Hibernate modules, Helidon.
**Best practice:** start with nothing exported and add exports deliberately. **Gotcha:** on the classpath (not module path), module rules aren't enforced; consumers of a modular jar on the classpath still see everything.

### API evolution and binary compatibility

**Description.** Callers usually upgrade a library through transitive dependencies, **without recompiling** their own code. So what matters is **binary compatibility** (JLS chapter 13): does already-compiled code still link? Adding methods, classes and default methods is safe. Removing or renaming anything, changing a parameter or return type (even widening `int` to `long`), adding an abstract method to an interface others implement, or making a class final breaks binaries, even if callers' source would still compile.

**Advantages / features.** Understanding the rules lets you ship minor versions safely and save breaking changes for planned major releases.

**BinaryCompatibilityDemo.java**

```java
import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Source compatibility is not binary compatibility. A partner app is compiled once against pricing-lib 1.0, then the
 * library jar is upgraded underneath it without recompiling the app (which is what happens with transitive
 * dependencies). Some changes keep working; widening a parameter from int to long compiles fine for callers but
 * breaks the already-compiled app with NoSuchMethodError.
 */
public class BinaryCompatibilityDemo {

    static final String V1_0 = "package lib; public class Pricing { public static long total(String sku, int qty) { return 6800L * qty; } }";
    static final String V1_1 = "package lib; public class Pricing { public static long total(String sku, int qty) { return 6800L * qty; } "
            + "public static long total(String sku, int qty, int discountPercent) { return 6800L * qty * (100 - discountPercent) / 100; } }";
    static final String V2_WIDENED = "package lib; public class Pricing { public static long total(String sku, long qty) { return 6800L * qty; } }";
    static final String APP = "package app; public class PartnerApp { public static void main(String[] a) { System.out.println(\"total \" + lib.Pricing.total(\"MILK-1L\", 3)); } }";

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("binary");
        Path libDir = root.resolve("lib-classes"), appDir = root.resolve("app-classes");
        compile(root, libDir, "lib/Pricing.java", V1_0, null);
        compile(root, appDir, "app/PartnerApp.java", APP, libDir);
        System.out.println("app compiled once against lib 1.0");

        for (String[] release : new String[][] {{"1.0", V1_0}, {"1.1 (adds an overload)", V1_1}, {"2.0 (int qty -> long qty)", V2_WIDENED}}) {
            compile(root, libDir, "lib/Pricing.java", release[1], null);                          // swap the jar, keep the app binary
            System.out.println("run with lib " + release[0] + ": " + run(libDir, appDir));
        }
        compile(root, appDir, "app/PartnerApp.java", APP, libDir);
        System.out.println("after recompiling the app against 2.0: " + run(libDir, appDir));
    }

    static String run(Path libDir, Path appDir) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {appDir.toUri().toURL(), libDir.toUri().toURL()}, null)) {
            PrintStream original = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            System.setOut(new PrintStream(captured, true));
            try {
                loader.loadClass("app.PartnerApp").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
                return captured.toString().strip();
            } catch (InvocationTargetException e) {
                return e.getCause().getClass().getSimpleName() + " " + e.getCause().getMessage();
            } finally {
                System.setOut(original);
            }
        }
    }

    static void compile(Path root, Path out, String file, String source, Path classpath) throws IOException {
        Path src = root.resolve("src").resolve(file);
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> options = classpath == null ? List.of("-d", out.toString()) : List.of("-d", out.toString(), "-cp", classpath.toString());
        boolean ok = javac.getTask(null, null, null, options, null, javac.getStandardFileManager(null, null, null).getJavaFileObjects(src)).call();
        if (!ok) throw new IllegalStateException("compile failed: " + file);
    }
}
```

1. Adding an overload is binary compatible; old call sites still link to the old method.
2. Widening `int` to `long` keeps callers' **source** valid but changes the method descriptor.
3. Recompiling fixes it, which is exactly what downstream users of a transitive dependency can't do.

**Run it:** `java BinaryCompatibilityDemo.java`

```text
app compiled once against lib 1.0
run with lib 1.0: total 20400
run with lib 1.1 (adds an overload): total 20400
run with lib 2.0 (int qty -> long qty): NoSuchMethodError 'long lib.Pricing.total(java.lang.String, int)'
after recompiling the app against 2.0: total 20400
```

| Change | Source compatible | Binary compatible |
|--------|-------------------|-------------------|
| Add a method / class / overload | Usually | Yes |
| Add a default method | Yes (rare conflicts) | Yes |
| Add an abstract method to an interface | No for implementers | No for implementers (`AbstractMethodError`) |
| Widen a parameter (int → long) | Yes for callers | **No** |
| Change return type | No | No |
| Remove or rename | No | No |
| Make a class or method final | No for subclasses | No (`VerifyError`/`IncompatibleClassChangeError`) |
| Change a `static final` constant | Yes | Old value stays inlined in callers |

**Use** semantic versioning: patch for fixes, minor for compatible additions, major for breaking changes. **Avoid** breaking changes in minor releases, even "tiny" ones.
**Real-world Java:** Guava's `@Beta` and deprecation policy, Jackson's 2.x compatibility, the JDK's `@Deprecated(forRemoval = true)` process.
**Best practice:** deprecate first, keep the old method delegating to the new one for at least one minor release, then remove in the next major. **Gotcha:** constants (`static final` primitives and strings) are inlined at compile time, so changing them in the library doesn't affect already-compiled callers.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's delivery-fee library exposes an interface and a factory, and later adds member pricing without breaking a partner's implementation.

**DeliveryFeeApiDemo.java**

```java
/**
 * A small library API designed to evolve: callers see an interface and a static factory, never the implementation
 * class, so the implementation can change freely. New behaviour arrives as a default method, so existing
 * third-party implementations keep compiling and running.
 */
public class DeliveryFeeApiDemo {

    /** Public API (version 1.1). */
    public interface DeliveryFees {
        long feePaise(long basketPaise);

        static DeliveryFees standard() {
            return new TieredFees(50000, 2900);
        }

        default long feePaise(long basketPaise, boolean member) {
            return member ? 0 : feePaise(basketPaise);
        }
    }

    /** Hidden implementation: package-private in a real library, so callers can't depend on it. */
    record TieredFees(long freeAbovePaise, long feePaise) implements DeliveryFees {
        public long feePaise(long basketPaise) { return basketPaise >= freeAbovePaise ? 0 : feePaise; }
    }

    /** A partner's implementation written against version 1.0, before the default method existed. */
    static final class QuickDropFlatFee implements DeliveryFees {
        public long feePaise(long basketPaise) { return 3500; }
    }

    public static void main(String[] args) {
        DeliveryFees fees = DeliveryFees.standard();
        System.out.println("standard fee on Rs 300: Rs " + fees.feePaise(30000) / 100.0);
        System.out.println("standard fee on Rs 600: Rs " + fees.feePaise(60000) / 100.0);
        System.out.println("member fee on Rs 300: Rs " + fees.feePaise(30000, true) / 100.0);
        DeliveryFees partner = new QuickDropFlatFee();
        System.out.println("QuickDrop 1.0 plugin, member call still works: Rs " + partner.feePaise(30000, true) / 100.0);
    }
}
```

1. The static factory returns the interface, so the implementation class never appears in callers' code.
2. New behaviour as a default method: old implementations inherit it automatically.

**Run it:** `java DeliveryFeeApiDemo.java`

**Output:**

```text
standard fee on Rs 300: Rs 29.0
standard fee on Rs 600: Rs 0.0
member fee on Rs 300: Rs 0.0
QuickDrop 1.0 plugin, member call still works: Rs 0.0
```

**What to notice:**

- QuickDrop's plugin, written for 1.0, gets member pricing in 1.1 without a code change or recompilation.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** five product teams add behaviour to checkout. Instead of editing one growing `CheckoutService`, they contribute annotated steps that FreshCart's internal framework discovers and runs.

**CheckoutStepsFrameworkDemo.java**

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FreshCart's internal checkout framework: product teams contribute steps by annotating a class; the framework
 * discovers, orders and runs them (inversion of control). A failing step is reported with its owner so the right
 * team is paged, and optional steps can fail without blocking the order.
 */
public class CheckoutStepsFrameworkDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @interface Step {
        int order();
        String owner();
        boolean optional() default false;
    }

    interface CheckoutStep { void run(Order order); }

    static final class Order {
        long totalPaise = 40000;
        final List<String> notes = new ArrayList<>();
    }

    @Step(order = 10, owner = "pricing-team")
    static final class ApplyCoupon implements CheckoutStep {
        public void run(Order o) { o.totalPaise -= 5000; o.notes.add("coupon FRESH50"); }
    }

    @Step(order = 30, owner = "growth-team", optional = true)
    static final class AwardLoyaltyPoints implements CheckoutStep {
        public void run(Order o) { throw new IllegalStateException("loyalty service timeout"); }
    }

    @Step(order = 20, owner = "logistics-team")
    static final class AddDeliveryFee implements CheckoutStep {
        public void run(Order o) { o.totalPaise += 2900; o.notes.add("QuickDrop fee"); }
    }

    static final class Framework {
        private final List<CheckoutStep> steps;

        Framework(Class<?> scanRoot) throws ReflectiveOperationException {
            List<CheckoutStep> found = new ArrayList<>();
            for (Class<?> c : scanRoot.getDeclaredClasses()) {
                if (c.isAnnotationPresent(Step.class) && CheckoutStep.class.isAssignableFrom(c)) {
                    found.add((CheckoutStep) c.getDeclaredConstructor().newInstance());
                }
            }
            found.sort(Comparator.comparingInt(s -> s.getClass().getAnnotation(Step.class).order()));
            steps = List.copyOf(found);
        }

        void checkout(Order order) {
            for (CheckoutStep step : steps) {
                Step meta = step.getClass().getAnnotation(Step.class);
                try {
                    step.run(order);
                    System.out.println("  " + meta.order() + " " + step.getClass().getSimpleName() + " ok");
                } catch (RuntimeException e) {
                    System.out.println("  " + meta.order() + " " + step.getClass().getSimpleName() + " FAILED (" + e.getMessage() + "), owner " + meta.owner());
                    if (!meta.optional()) throw e;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Framework framework = new Framework(CheckoutStepsFrameworkDemo.class);
        Order order = new Order();
        System.out.println("checkout:");
        framework.checkout(order);
        System.out.println("total Rs " + order.totalPaise / 100.0 + ", notes " + order.notes);
    }
}
```

1. The annotation is the extension contract: order, owning team, and whether failure blocks checkout.
2. The framework, not the teams, decides execution order.
3. Failures carry the step name and owning team, so alerts route to the right people.

**Run it:** `java CheckoutStepsFrameworkDemo.java`

**Output:**

```text
checkout:
  10 ApplyCoupon ok
  20 AddDeliveryFee ok
  30 AwardLoyaltyPoints FAILED (loyalty service timeout), owner growth-team
total Rs 379.0, notes [coupon FRESH50, QuickDrop fee]
```

**What to notice:**

- Steps are declared out of order in the source but run by their `order` value.
- The optional loyalty step failed and the order still completed; a required step failing would stop checkout.
- In production the scan would come from a classpath index or Spring beans, not `getDeclaredClasses`.

### Example 3: Production-grade

**Scenario:** the FreshCart Partner SDK is used by QuickDrop and ParcelGo. A previous "minor" release broke a partner's integration. Now CI snapshots the public API, classifies changes and fails the build when the version number understates the change.

**ApiCompatibilityCheckDemo.java**

```java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A semantic-versioning gate for the FreshCart Partner SDK (used by QuickDrop and ParcelGo). It snapshots the public
 * API of each release with reflection, classifies the differences (in the spirit of japicmp or revapi) and fails the
 * build when the proposed version number is smaller than the change requires.
 */
public class ApiCompatibilityCheckDemo {

    enum Bump { PATCH, MINOR, MAJOR }

    record Report(Bump required, Set<String> removed, Set<String> added, Set<String> breakingAdditions) {}

    static Set<String> snapshot(Class<?>... types) {
        Set<String> api = new TreeSet<>();
        for (Class<?> type : types) {
            for (Method m : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) continue;
                String params = Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(","));
                String kind = type.isInterface() && Modifier.isAbstract(m.getModifiers()) ? " [abstract]" : "";
                api.add(type.getSimpleName() + "#" + m.getName() + "(" + params + "):" + m.getReturnType().getSimpleName() + kind);
            }
        }
        return api;
    }

    static Report compare(Set<String> before, Set<String> after) {
        Set<String> removed = new TreeSet<>(before);
        removed.removeAll(after);
        Set<String> added = new TreeSet<>(after);
        added.removeAll(before);
        Set<String> breakingAdditions = added.stream().filter(s -> s.endsWith("[abstract]")).collect(Collectors.toCollection(TreeSet::new));
        Bump required = !removed.isEmpty() || !breakingAdditions.isEmpty() ? Bump.MAJOR
                : !added.isEmpty() ? Bump.MINOR
                : Bump.PATCH;
        return new Report(required, removed, added, breakingAdditions);
    }

    static Bump proposedBump(String from, String to) {
        int[] a = Arrays.stream(from.split("\\.")).mapToInt(Integer::parseInt).toArray();
        int[] b = Arrays.stream(to.split("\\.")).mapToInt(Integer::parseInt).toArray();
        return b[0] > a[0] ? Bump.MAJOR : b[1] > a[1] ? Bump.MINOR : Bump.PATCH;
    }

    static boolean gate(String from, String to, Report report) {
        return proposedBump(from, to).compareTo(report.required()) >= 0;
    }

    // ---- the SDK as published in each release (normally read from the previous jar) ----
    static final class R100 {
        public interface Transport { String post(String path, String json); }
        public static final class PartnerClient {
            public long quote(String pincode, int weightGrams) { return 0; }
            @Deprecated(since = "1.0", forRemoval = true) public long quote(String pincode) { return 0; }
        }
    }
    static final class R110 {
        public interface Transport {
            String post(String path, String json);
            default String get(String path) { return post(path, ""); }
        }
        public static final class PartnerClient {
            public long quote(String pincode, int weightGrams) { return 0; }
            @Deprecated(since = "1.0", forRemoval = true) public long quote(String pincode) { return 0; }
            public String track(String orderId) { return ""; }
        }
    }
    static final class R120 {
        public interface Transport {
            String post(String path, String json);
            String get(String path);
        }
        public static final class PartnerClient {
            public long quote(String pincode, long weightGrams) { return 0; }
            public String track(String orderId) { return ""; }
        }
    }

    public static void main(String[] args) {
        Set<String> v100 = snapshot(R100.Transport.class, R100.PartnerClient.class);
        Set<String> v110 = snapshot(R110.Transport.class, R110.PartnerClient.class);
        Set<String> v120 = snapshot(R120.Transport.class, R120.PartnerClient.class);
        System.out.println("API 1.0.0:");
        v100.forEach(s -> System.out.println("  " + s));

        check("1.0.0", "1.1.0", compare(v100, v110), true);
        Report r = compare(v110, v120);
        check("1.1.0", "1.2.0", r, true);
        check("1.1.0", "2.0.0", r, false);
    }

    static void check(String from, String to, Report r, boolean details) {
        System.out.println(from + " -> " + to + ": change needs " + r.required() + ", version says " + proposedBump(from, to)
                + " => " + (gate(from, to, r) ? "PASS" : "FAIL the build"));
        if (!details) return;
        r.removed().forEach(s -> System.out.println("  removed  " + s));
        r.breakingAdditions().forEach(s -> System.out.println("  breaks implementers  " + s));
        r.added().stream().filter(s -> !r.breakingAdditions().contains(s)).forEach(s -> System.out.println("  added    " + s));
    }
}
```

1. The API snapshot: every public method with parameter and return types, and whether interface methods are abstract.
2. Removing or changing anything, or adding an abstract interface method, requires a MAJOR version.
3. Pure additions require a MINOR version.
4. The gate compares the proposed version bump with the required one.
5. Turning a default method into an abstract one breaks every partner that implements `Transport`.

**Run it:** `java ApiCompatibilityCheckDemo.java`

**Output:**

```text
API 1.0.0:
  PartnerClient#quote(String):long
  PartnerClient#quote(String,int):long
  Transport#post(String,String):String [abstract]
1.0.0 -> 1.1.0: change needs MINOR, version says MINOR => PASS
  added    PartnerClient#track(String):String
  added    Transport#get(String):String
1.1.0 -> 1.2.0: change needs MAJOR, version says MINOR => FAIL the build
  removed  PartnerClient#quote(String):long
  removed  PartnerClient#quote(String,int):long
  removed  Transport#get(String):String
  breaks implementers  Transport#get(String):String [abstract]
  added    PartnerClient#quote(String,long):long
1.1.0 -> 2.0.0: change needs MAJOR, version says MAJOR => PASS
```

**What to notice:**

- The `int` to `long` widening shows up as a removal plus an addition: binary breaking, as the Binary Compatibility demo proved.
- 1.2.0 fails the build; the same change released as 2.0.0 passes.
- Real projects use japicmp, revapi or the Gradle/Maven plugins built on them, which also check fields, constructors, class modifiers and generics.

## 7. Pros, Cons & Trade-offs

| Approach | Pros | Cons |
|----------|------|------|
| Small, closed API | Easy to evolve | Users ask for more extension points |
| Rich extension points (SPI, plugins) | Flexible, decoupled teams | Every hook is a promise to keep |
| IoC framework | Consistency, less boilerplate | Magic, harder debugging |
| JPMS encapsulation | Enforced boundaries | Tooling friction, classpath users unaffected |
| Strict semantic versioning | Predictable upgrades | Slower to remove mistakes |

## 8. When to Use / When NOT to Use

**Invest in framework design when:**

- several teams or external partners depend on the code;
- the code outlives individual features;
- consumers upgrade on their own schedule.

**Don't when:**

- one team owns all callers and deploys them together (just refactor);
- the "framework" has one user (build the application, extract later with the rule of three);
- an established library already does it.

## 9. Best Practices

1. **Minimise the public surface; everything public is forever.** *(Consensus; Bloch, "How to Design a Good API".)*
2. **Separate API, SPI and implementation packages.** *(Consensus.)*
3. **Prefer interfaces + static factories over public constructors.** *(Effective Java, items 1 and 64.)*
4. **Evolve with default methods and overloads; deprecate before removing.** *(Consensus.)*
5. **Enforce binary compatibility and semantic versioning in CI.** *(Widely used: japicmp, revapi.)*
6. **Give every extension point a version and a failure policy.** *(Consensus.)*
7. **Write the docs and examples before freezing an API.** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Minor release, major outage (production failure mode)**  
> **Symptom:** after a routine dependency bump, QuickDrop's integration fails at runtime with `NoSuchMethodError`; their code didn't change.
> **Root cause:** the SDK's 1.4 release widened a parameter from `int` to `long`; the partner's code was compiled against 1.3.
> **Fix:** restore the old method delegating to the new one, release 1.4.1, and add an API compatibility gate (Example 3).

> 🚨 **Accidental API**  
> **Symptom:** a refactor of an "internal" helper breaks three teams.
> **Root cause:** the helper was public, in an unexported-by-convention package, and teams used it.
> **Fix:** JPMS exports or ArchUnit rules; move helpers to package-private.

> 🚨 **Framework that hides failures**  
> **Symptom:** a plugin silently didn't load, and a discount wasn't applied for a week.
> **Root cause:** `ServiceConfigurationError` caught and ignored.
> **Fix:** log and expose loaded/rejected plugins in a startup report and a health endpoint.

> 🚨 **Speculative framework**  
> **Symptom:** a generic workflow engine used by one service, harder to change than the code it replaced.
> **Root cause:** extracted before there were real variations.
> **Fix:** wait for three real users; inline it back if they don't appear.

## 11. Configuration & Tuning

| Option | Default | What it does | When to change it | Risk of changing |
|--------|---------|--------------|-------------------|------------------|
| japicmp `breakBuildOnBinaryIncompatibleModifications` | false | Fails build on binary breaks | Always for published libraries | None |
| japicmp `breakBuildBasedOnSemanticVersioning` | false | Checks version bump vs change | Libraries using semver | None |
| Maven Shade `ServicesResourceTransformer` | off | Merges `META-INF/services` | Fat jars with SPIs | None |
| `@Deprecated(since, forRemoval)` | n/a | Signals removal plan | Before removing API | Warnings for users |
| `--add-opens` / `opens` | closed | Allows reflection into packages | Frameworks needing field access | Breaks encapsulation |
| Plugin API version | n/a | Load-time compatibility check | Each breaking SPI change | Rejects old plugins |

## 12. Security Considerations

- Plugins and ServiceLoader providers run with full privileges in your JVM; only load signed or allow-listed artifacts.
- Annotation scanning and reflection-driven frameworks must not instantiate classes named by untrusted input (deserialization-style gadget risk).
- Keep `opens` narrow; opening packages exposes private state to any reflective caller.
- Publish SBOMs and sign releases (Sigstore, Maven Central signing) so users can verify your library.

## 13. Performance & Scalability

Classpath scanning at startup is a common slow-start cause; prefer build-time indexes (Spring's AOT processing, Micronaut, Dagger, annotation processors). ServiceLoader instantiates providers lazily as you iterate; cache the results. Reflection-based IoC costs startup time and memory; compile-time DI (Dagger, Micronaut) and GraalVM native images move that cost to the build. Plugin class loaders each hold their own metaspace.

## 14. Testing It

Test the API contract, not only the implementation:

**ApiCompatibilityCheckTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiCompatibilityCheckTest {

    interface OldPort { String post(String body); }
    interface PortWithDefault { String post(String body); default String get() { return ""; } }
    interface PortWithAbstract { String post(String body); String get(); }
    static final class OldClient { public long quote(String pin, int grams) { return 0; } }
    static final class WidenedClient { public long quote(String pin, long grams) { return 0; } }

    @Test
    void identicalApiNeedsOnlyAPatch() {
        Set<String> api = ApiCompatibilityCheckDemo.snapshot(OldClient.class);
        assertEquals(ApiCompatibilityCheckDemo.Bump.PATCH, ApiCompatibilityCheckDemo.compare(api, api).required());
    }

    @Test
    void defaultInterfaceMethodIsAMinorChange() {
        var report = compareRenamed(OldPort.class, PortWithDefault.class, "OldPort", "PortWithDefault");
        assertEquals(ApiCompatibilityCheckDemo.Bump.MINOR, report.required());
    }

    @Test
    void abstractInterfaceMethodBreaksImplementers() {
        var report = compareRenamed(OldPort.class, PortWithAbstract.class, "OldPort", "PortWithAbstract");
        assertEquals(ApiCompatibilityCheckDemo.Bump.MAJOR, report.required());
        assertEquals(Set.of("X#get():String [abstract]"), report.breakingAdditions());
    }

    @Test
    void wideningAParameterIsBinaryBreaking() {
        var report = compareRenamed(OldClient.class, WidenedClient.class, "OldClient", "WidenedClient");
        assertEquals(ApiCompatibilityCheckDemo.Bump.MAJOR, report.required());
        assertEquals(Set.of("X#quote(String,int):long"), report.removed());
    }

    @Test
    void gateRejectsAnUnderBumpedVersion() {
        var major = new ApiCompatibilityCheckDemo.Report(ApiCompatibilityCheckDemo.Bump.MAJOR, Set.of("x"), Set.of(), Set.of());
        assertFalse(ApiCompatibilityCheckDemo.gate("1.4.2", "1.5.0", major));
        assertTrue(ApiCompatibilityCheckDemo.gate("1.4.2", "2.0.0", major));
        assertEquals(ApiCompatibilityCheckDemo.Bump.PATCH, ApiCompatibilityCheckDemo.proposedBump("1.4.2", "1.4.3"));
    }

    /** Snapshots two differently named test types as if they were the same type in two releases. */
    private static ApiCompatibilityCheckDemo.Report compareRenamed(Class<?> before, Class<?> after, String beforeName, String afterName) {
        Set<String> a = rename(ApiCompatibilityCheckDemo.snapshot(before), beforeName);
        Set<String> b = rename(ApiCompatibilityCheckDemo.snapshot(after), afterName);
        return ApiCompatibilityCheckDemo.compare(a, b);
    }

    private static Set<String> rename(Set<String> api, String name) {
        return new java.util.TreeSet<>(api.stream().map(s -> s.replaceFirst("^" + name + "#", "X#")).toList());
    }
}
```

Also: publish a **TCK** (technology compatibility kit), an abstract test class that every SPI implementer extends, so partners verify their providers the same way you do.

## 15. Observability & Operations

Log a startup report: framework version, loaded plugins with versions, rejected plugins with reasons, and detected SPI providers. Expose it on a health or info endpoint. Track library version adoption across services (from SBOMs or dependency reports) so you know when an old major can be retired.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2006 | Java 6: public `java.util.ServiceLoader` |
| 2008 | Effective Java 2nd ed. and Bloch's API design talk popularise API design rules |
| 2013 | Semantic Versioning 2.0.0 |
| 2014 | Java 8: default and static interface methods enable API evolution |
| 2017 | Java 9: JPMS (`exports`, `opens`, `provides/uses`), `ServiceLoader.stream()` |
| 2021 | Java 17: strong encapsulation of JDK internals |
| 2022 | Spring Framework 6: AOT processing reduces runtime scanning |

## 17. Expert Deep Dive

- **API vs SPI evolution are opposite.** Adding a method to an API interface is safe for callers but breaks implementers; for SPIs, prefer abstract classes or default methods so you can add operations.
- **Tolerant readers and parameter objects.** Methods taking a request object (`QuoteRequest`) evolve by adding fields; long parameter lists can't.
- **Hyrum's law.** With enough users, every observable behaviour (ordering, timing, error messages) will be depended on; document what is guaranteed and randomise what isn't (like `Map.of` iteration order).
- **Multi-release jars and baselines.** Compile against the oldest supported Java (`--release`) and use multi-release jars for newer APIs.
- **Compile-time frameworks.** Annotation processors generate the wiring (Dagger, MapStruct, Micronaut), trading build complexity for startup speed and better errors.

> 📖 **War story: the SDK patch that broke a partner on a Saturday**  
> FreshCart's Partner SDK 1.4.0 changed `quote(String pincode, int weightGrams)` to take `long` "for bulk orders". All FreshCart services recompiled and passed tests. ParcelGo's integration pulled the new SDK transitively through another library, without recompiling, and failed at runtime with `NoSuchMethodError` during peak weekend deliveries. **Fixes:** 1.4.1 restored the `int` overload delegating to the `long` one; the `long` version moved to an additive method; japicmp now runs in CI with semver enforcement; partners get a 6-month notice and migration guide for every major. **Lesson:** your users' binaries are part of your API.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's an SPI?** An **interface for others to implement**, discovered at run time.
2. **What does ServiceLoader read?** **`META-INF/services/<interface>`** files (or module `provides`).
3. **What is inversion of control?** **The framework calls your code.**
4. **Why use static factories?** **Hide the implementation class**, return interfaces.
5. **What does semantic versioning encode?** **MAJOR breaking, MINOR additive, PATCH fixes.**

**▸ 🟡 L3**

1. **API vs SPI?** **Callers use** the API; **extenders implement** the SPI.
2. **Is adding a default method safe?** **Yes, binary compatible** (rare source conflicts).
3. **What does `exports ... to` do?** **Exports a package only to named modules.**
4. **Why do plugins declare an API version?** So the host can **reject incompatible plugins at load time**.
5. **Why is classpath order a bad priority mechanism?** **It's not guaranteed**; add explicit priorities.

**▸ 🔴 L4–L5**

1. **Source vs binary compatibility?** Whether callers **recompile** fine vs whether **compiled callers still link**.
2. **Why does widening int to long break?** **The method descriptor changes**; old binaries look for the old one.
3. **Why are changed constants dangerous?** **Inlined into callers at compile time.**
4. **How do you evolve an SPI interface?** **Default methods, abstract base classes, or a new versioned interface.**
5. **What's Hyrum's law?** **All observable behaviour gets depended on.**

**▸ 💻 Hands-on**

**Task:** Make `ServiceLoaderDemo` choose the cheapest partner, with a deterministic tie-break, instead of relying on file order.

> ✅ **Solution**  
> ```java
> DeliveryPartner best = null;
> for (var it = partners.iterator(); ; ) {
>     try {
>         if (!it.hasNext()) break;
>         DeliveryPartner p = it.next();
>         if (best == null
>                 || p.quotePaise(5) < best.quotePaise(5)
>                 || (p.quotePaise(5) == best.quotePaise(5) && p.name().compareTo(best.name()) < 0)) best = p;
>     } catch (ServiceConfigurationError e) { /* log and skip */ }
> }
> System.out.println("cheapest: " + best.name());
> ```

## 19. Practice

- [ ] Package `ServiceLoaderDemo`'s providers as a real jar and load it with `java -cp`.
    > 💡 **Hint**  
    > Put the services file under `META-INF/services` inside the jar.

- [ ] Add japicmp to one of your libraries and compare it with the last release.
    > 💡 **Hint**  
    > `japicmp-maven-plugin` with `oldVersion` set to the previous release.

- [ ] Add a startup report to `PluginArchitectureDemo` listing loaded and rejected plugins.
    > 💡 **Hint**  
    > Record each outcome in the host and print it after installation.

- [ ] Write a TCK abstract test class for `DeliveryPartner` implementations.
    > 💡 **Hint**  
    > An abstract `createPartner()` method plus shared tests for non-negative quotes.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - API (callers), SPI (implementers), implementation (hidden).
> - ServiceLoader: `META-INF/services` or `provides`; handle `ServiceConfigurationError`.
> - Plugins: versioned contract, isolation, explicit order, startup report.
> - IoC: the framework owns the flow; errors must name the user's code.
> - Binary compatibility: adding is safe; changing or removing anything is MAJOR.
>
> ```java
> ServiceLoader.load(DeliveryPartner.class).stream()
>     .map(ServiceLoader.Provider::get)
>     .forEach(p -> System.out.println(p.name()));
> ```

## 21. Further Reading

- Joshua Bloch, *Effective Java*, 3rd ed. (2018), items 1, 15–17, 20–21, 64.
- Jaroslav Tulach, *Practical API Design: Confessions of a Java Framework Architect* (2008).
- *The Java Language Specification*, chapter 13 "Binary Compatibility".
- [Semantic Versioning 2.0.0](https://semver.org/) and [japicmp](https://siom79.github.io/japicmp/).

---

[← Previous: JVM Internals Behind Patterns](jvm-internals-behind-patterns.md) · [Next: Governing Patterns Across an Organisation →](governing-patterns-at-scale.md)
