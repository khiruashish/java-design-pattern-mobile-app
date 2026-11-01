# Evolutionary Architecture

L5 🔴 Staff / Principal · ⏱ 30 min read · Prerequisites: [Choosing Patterns and Trade-offs](pattern-selection.md), [Architectural Patterns](../l4-senior/architectural-patterns.md)

> 📌 **TL;DR**  
> Architecture decays one reasonable shortcut at a time unless something checks it. **Fitness functions** are executable checks of architectural characteristics (latency budgets, dependency rules, module size, cycles, dependency hygiene) that run in CI or production. **ArchUnit** expresses structural rules (layers, internal packages, cycles) as JUnit tests over compiled classes. **JPMS** (`module-info.java`) makes module boundaries a compiler-enforced fact rather than a convention. **Coupling metrics** (afferent/efferent coupling, instability, abstractness) show where change will hurt. Combine them with a **ratchet**: record today's violations as a baseline that may only shrink, so you can adopt rules on a legacy codebase without stopping delivery.

## 1. What It Is

**Plain English.** FreshCart's architecture diagram says "web calls ordering, ordering calls pricing, nobody touches pricing's internals". Two years later, it's only true if something checks it on every commit. Evolutionary architecture means designing for change and protecting the characteristics you care about with automated checks, the way tests protect behaviour.

**Definition.**

| Practice | Purpose |
|----------|---------|
| Fitness function | An objective, automated check of an architectural characteristic |
| ArchUnit rules | Structural constraints on Java code as unit tests |
| Modularity with JPMS | Compiler- and runtime-enforced module boundaries |
| Measuring coupling | Quantify dependencies to find fragile or rigid areas |

## 2. Why It Exists

Architecture documents describe intent; code describes reality, and the two drift. Code review catches some violations but not reliably across hundreds of PRs and many teams. Neal Ford, Rebecca Parsons and Patrick Kua's *Building Evolutionary Architectures* (2017) borrowed "fitness functions" from evolutionary computing: if a characteristic matters, measure it continuously and fail the build when it regresses. Tools like ArchUnit (2017) and the Java Platform Module System (Java 9, 2017) made this practical for Java.

## 3. Mental Model / Analogy

**Analogy.** A building's safety inspections. Instead of trusting that the builders followed the plan, inspectors check load limits, fire exits and wiring at defined points (fitness functions). Some rooms are locked from the corridor (JPMS: non-exported packages). A structural engineer measures which walls carry the most load (coupling metrics), because changing those is expensive. Old buildings get a list of known issues that must not grow (ratchet), rather than being condemned overnight.

**Technical model.** A set of automated checks over code structure (classes, packages, modules), build metadata (dependencies) and runtime metrics (latency, error rates), wired into CI and monitoring with thresholds and baselines.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Fitness functions

**Description.** A fitness function is any mechanism that objectively assesses an architectural characteristic: a test, a metric threshold, a static analysis rule, a production monitor. They can be **atomic** (one characteristic, e.g. no cycles) or **holistic** (a combination, e.g. p95 checkout latency under load), and **triggered** (run on commit or release) or **continual** (run in production).

**Advantages / features.** Architecture becomes testable; regressions surface on the PR that caused them; trade-offs become explicit thresholds.

**FitnessFunctionsDemo.java**

```java
import java.util.List;
import java.util.function.Predicate;

/**
 * Fitness functions: executable checks that an architectural characteristic still holds (latency, coupling, size,
 * dependency rules). They run in CI (triggered) or in production (continual), so architecture can't silently drift.
 */
public class FitnessFunctionsDemo {

    record Snapshot(int checkoutP95Ms, int dependencyCycles, int largestModuleKloc, double testCoverage, int criticalCves) {}
    record FitnessFunction(String name, String kind, Predicate<Snapshot> holds, String threshold) {}

    static final List<FitnessFunction> SUITE = List.of(
            new FitnessFunction("checkout p95 latency", "continual, holistic", s -> s.checkoutP95Ms() <= 300, "<= 300 ms"),
            new FitnessFunction("no module cycles", "triggered, atomic", s -> s.dependencyCycles() == 0, "0 cycles"),
            new FitnessFunction("module size", "triggered, atomic", s -> s.largestModuleKloc() <= 40, "<= 40 kloc"),
            new FitnessFunction("domain test coverage", "triggered, atomic", s -> s.testCoverage() >= 0.80, ">= 80%"),
            new FitnessFunction("no critical CVEs", "triggered + nightly", s -> s.criticalCves() == 0, "0"));

    static boolean evaluate(String label, Snapshot s) {
        System.out.println(label);
        boolean allPass = true;
        for (FitnessFunction f : SUITE) {
            boolean ok = f.holds().test(s);
            allPass &= ok;
            System.out.printf("  %-4s %-22s %-20s (%s)%n", ok ? "ok" : "FAIL", f.name(), f.threshold(), f.kind());
        }
        return allPass;
    }

    public static void main(String[] args) {
        evaluate("release 41:", new Snapshot(240, 0, 31, 0.84, 0));
        boolean ok = evaluate("release 42 candidate:", new Snapshot(260, 1, 44, 0.83, 0));
        System.out.println("release 42 " + (ok ? "can ship" : "blocked until the fitness functions pass"));
    }
}
```

1. Each fitness function has a name, a kind, an executable check and a stated threshold.
2. The suite evaluates every function against a snapshot of the system.
3. A failing function blocks the release, just like a failing unit test.

**Run it:** `java FitnessFunctionsDemo.java`

```text
release 41:
  ok   checkout p95 latency   <= 300 ms            (continual, holistic)
  ok   no module cycles       0 cycles             (triggered, atomic)
  ok   module size            <= 40 kloc           (triggered, atomic)
  ok   domain test coverage   >= 80%               (triggered, atomic)
  ok   no critical CVEs       0                    (triggered + nightly)
release 42 candidate:
  ok   checkout p95 latency   <= 300 ms            (continual, holistic)
  FAIL no module cycles       0 cycles             (triggered, atomic)
  FAIL module size            <= 40 kloc           (triggered, atomic)
  ok   domain test coverage   >= 80%               (triggered, atomic)
  ok   no critical CVEs       0                    (triggered + nightly)
release 42 blocked until the fitness functions pass
```

| Characteristic | Example fitness function |
|----------------|--------------------------|
| Modularity | ArchUnit layer and cycle rules; module size limits |
| Performance | p95 latency under a load test in CI; production SLO alerts |
| Security | No critical CVEs (OWASP Dependency-Check); no secrets in repo |
| Resilience | Chaos experiment passes (instance kill, latency injection) |
| Evolvability | Dependency freshness; deprecated API usage trending down |
| Data | Schema changes are backward compatible (oasdiff, Avro compatibility) |

**Use** for characteristics the business actually depends on. **Avoid** dozens of vanity metrics nobody acts on.
**Real-world Java:** ArchUnit, jQAssistant, Gatling/JMH thresholds in CI, OWASP Dependency-Check, Sonar quality gates.
**Best practice:** every fitness function names an owner and a reason (link the ADR). **Gotcha:** flaky fitness functions (noisy performance tests) get disabled; make them robust or report "inconclusive".

### ArchUnit rules

**Description.** ArchUnit imports compiled classes and lets you assert structural rules in Java: layer dependencies, package access, naming, annotations, cycles between slices. Rules run as ordinary JUnit tests and print readable violations with source locations.

**Advantages / features.** Rules live next to the code, in Java, reviewed in PRs; violations are caught in seconds; supports freezing existing violations.

This example is a small Maven project using the real library: [`examples/l5-staff-principal/evolutionary-architecture/archunit`](https://github.com/khiruashish/java-design-pattern-mobile-app/tree/claude/add-playbook-master-prompt/examples/l5-staff-principal/evolutionary-architecture/archunit).

**ArchUnitDemo.java**

```java
package com.freshcart.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real ArchUnit: architecture rules as code, evaluated against FreshCart's compiled classes. */
public class ArchUnitDemo {

    static final Map<String, ArchRule> RULES = new LinkedHashMap<>();
    static {
        RULES.put("internal packages are private to their module", noClasses()
                .that().resideOutsideOfPackage("com.freshcart.pricing..")
                .should().dependOnClassesThat().resideInAPackage("com.freshcart.pricing.internal.."));
        RULES.put("layers: web -> ordering -> pricing", layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Web").definedBy("com.freshcart.web..")
                .layer("Ordering").definedBy("com.freshcart.ordering..")
                .layer("Pricing").definedBy("com.freshcart.pricing..")
                .whereLayer("Web").mayNotBeAccessedByAnyLayer()
                .whereLayer("Ordering").mayOnlyBeAccessedByLayers("Web")
                .whereLayer("Pricing").mayOnlyBeAccessedByLayers("Ordering"));
        RULES.put("no cycles between modules", slices().matching("com.freshcart.(*)..").should().beFreeOfCycles());
    }

    public static JavaClasses freshcartClasses() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.freshcart.web", "com.freshcart.ordering", "com.freshcart.pricing");
    }

    public static void main(String[] args) {
        JavaClasses classes = freshcartClasses();
        RULES.forEach((name, rule) -> {
            EvaluationResult result = rule.evaluate(classes);
            System.out.println((result.hasViolation() ? "FAIL " : "ok   ") + name);
            result.getFailureReport().getDetails().stream().sorted()
                    .forEach(d -> System.out.println("     " + d.replaceAll(" in \\(.*\\)$", "")));
        });
    }
}
```

1. Only the pricing module may use `pricing.internal`.
2. A layered architecture: each layer states who may access it.
3. Module slices must be free of cycles.
4. Rules are evaluated against the imported classes; in tests you'd call `rule.check(classes)`.

**Run it:** `mvn -q compile exec:java -Dexec.mainClass=com.freshcart.architecture.ArchUnitDemo` (or `mvn verify`, which runs the rules as tests)

```text
FAIL internal packages are private to their module
     Method <com.freshcart.web.OrderController.post(java.lang.String, long)> calls method <com.freshcart.pricing.internal.DiscountTable.percentFor(java.lang.String)>
FAIL layers: web -> ordering -> pricing
     Method <com.freshcart.web.OrderController.post(java.lang.String, long)> calls method <com.freshcart.pricing.internal.DiscountTable.percentFor(java.lang.String)>
ok   no cycles between modules
```

| ArchUnit feature | Use |
|------------------|-----|
| `layeredArchitecture()` | Layer access rules |
| `onionArchitecture()` | Hexagonal / clean architecture rules |
| `slices().should().beFreeOfCycles()` | No cycles between modules |
| `FreezingArchRule.freeze(rule)` | Ratchet existing violations |
| `@AnalyzeClasses` + `@ArchTest` | Declarative JUnit 5 integration |
| `GeneralCodingRules` | No field injection, no `System.out`, no generic exceptions |

**Use** ArchUnit for any codebase with more than one module or team. **Avoid** rules so strict that teams add exceptions everywhere.
**Real-world Java:** Spring Modulith's `ApplicationModules.verify()` builds on ArchUnit; many teams keep an `ArchitectureTest` per service.
**Best practice:** start with three rules (layers, internal packages, cycles) and freeze existing violations. **Gotcha:** rules only see classes you import; make sure the importer covers the right packages and excludes tests where intended.

### Modularity with JPMS

**Description.** The Java Platform Module System lets a JAR declare a module in `module-info.java`: its name, the packages it **exports** and the modules it **requires**. Non-exported packages are inaccessible to other modules, at compile time and run time (including reflection unless `opens`). It turns "please don't use internal packages" into a compiler error.

**Advantages / features.** Strong encapsulation; explicit dependencies; reliable configuration (missing modules fail at startup); smaller runtime images with `jlink`.

**JpmsModulesDemo.java**

```java
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Modularity with JPMS: module-info.java declares what a module exports and requires. Packages that aren't exported
 * are inaccessible to other modules at compile time and run time, so internals stay internal. This demo writes two
 * small modules to a temp folder and compiles them with the real javac.
 */
public class JpmsModulesDemo {

    static final Map<String, String> PRICING = new TreeMap<>(Map.of(
            "module-info.java", "module freshcart.pricing { exports com.freshcart.pricing.api; }",
            "com/freshcart/pricing/api/PriceQuote.java", "package com.freshcart.pricing.api; public record PriceQuote(String sku, long paise) {}",
            "com/freshcart/pricing/internal/DiscountTable.java", "package com.freshcart.pricing.internal; public class DiscountTable { public static int percent(String sku) { return 10; } }"));

    static String ordering(String body) {
        return "package com.freshcart.ordering; " + body;
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("jpms");
        write(root.resolve("freshcart.pricing"), PRICING);
        String good = ordering("import com.freshcart.pricing.api.PriceQuote; public class Cart { PriceQuote q = new PriceQuote(\"MILK-1L\", 6800); }");
        String bad = ordering("import com.freshcart.pricing.internal.DiscountTable; public class Cart { int p = DiscountTable.percent(\"MILK-1L\"); }");

        for (String[] attempt : new String[][] {{"uses the exported API", good}, {"reaches into internals", bad}}) {
            Path orderingDir = root.resolve("freshcart.ordering");
            write(orderingDir, Map.of("module-info.java", "module freshcart.ordering { requires freshcart.pricing; }",
                    "com/freshcart/ordering/Cart.java", attempt[1]));
            String errors = compile(root);
            System.out.println("ordering " + attempt[0] + ": " + (errors.isEmpty() ? "compiles" : "compile error"));
            errors.lines().filter(l -> l.contains("error:")).map(l -> l.substring(l.indexOf("error:"))).distinct()
                    .forEach(l -> System.out.println("  " + l));
        }
    }

    static void write(Path dir, Map<String, String> files) throws IOException {
        for (var e : files.entrySet()) {
            Path p = dir.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
    }

    static String compile(Path root) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> sources;
        try (var walk = Files.walk(root)) { sources = walk.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList(); }
        StringWriter out = new StringWriter();
        List<String> options = new java.util.ArrayList<>(List.of("--module-source-path", root.toString(), "-d", root.resolve("out").toString()));
        javac.getTask(out, null, null, options, null, javac.getStandardFileManager(null, null, null).getJavaFileObjectsFromStrings(sources)).call();
        return out.toString();
    }
}
```

1. Pricing exports only its API package; `internal` stays private.
2. Ordering declares that it requires pricing.
3. javac rejects access to the non-exported package.

**Run it:** `java JpmsModulesDemo.java`

```text
ordering uses the exported API: compiles
ordering reaches into internals: compile error
  error: package com.freshcart.pricing.internal is not visible
```

| Directive | Meaning |
|-----------|---------|
| `exports p` | Other modules can use public types in `p` |
| `exports p to m` | Qualified export to specific modules |
| `requires m` / `requires transitive m` | Dependency; transitive re-exports it to your consumers |
| `opens p` | Deep reflection allowed (frameworks like Hibernate, Jackson) |
| `uses S` / `provides S with Impl` | ServiceLoader-based plugins |

**Use** JPMS for libraries and platforms where encapsulation matters across teams or releases. **Avoid** forcing it onto Spring Boot applications where frameworks need broad reflection and the payoff is small; Spring Modulith or ArchUnit give most of the benefit there.
**Real-world Java:** the JDK itself (since 9), libraries publishing `module-info` (Jackson, SLF4J 2, JUnit 5), `jlink` images.
**Best practice:** export API packages only; name modules like their root package. **Gotcha:** split packages (the same package in two modules) aren't allowed; legacy JARs often need to stay on the classpath as automatic or unnamed modules.

### Measuring coupling

**Description.** Robert C. Martin's package metrics: **Ca** (afferent coupling: how many modules depend on this one), **Ce** (efferent: how many this one depends on), **instability I = Ce / (Ca + Ce)** (0 = stable, 1 = unstable), **abstractness A** (abstract types / all types) and **distance D = |A + I - 1|** from the "main sequence". Stable modules should be abstract (easy to extend without changing); unstable ones can be concrete.

**Advantages / features.** Objective signals for where changes ripple; identifies "zone of pain" (stable and concrete) and "zone of uselessness" (unstable and abstract).

**CouplingMetricsDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Measuring coupling (Robert C. Martin's package metrics): afferent coupling Ca (who depends on me), efferent
 * coupling Ce (whom I depend on), instability I = Ce / (Ca + Ce), abstractness A, and distance from the main
 * sequence D = |A + I - 1|. Stable modules should be abstract; unstable ones concrete.
 */
public class CouplingMetricsDemo {

    record Module(String name, int abstractTypes, int totalTypes, Set<String> dependsOn) {}

    public static void main(String[] args) {
        List<Module> modules = List.of(
                new Module("shared-kernel", 6, 8, Set.of()),
                new Module("pricing", 2, 20, Set.of("shared-kernel")),
                new Module("ordering", 3, 30, Set.of("shared-kernel", "pricing", "inventory")),
                new Module("inventory", 1, 25, Set.of("shared-kernel")),
                new Module("util", 0, 40, Set.of()),
                new Module("checkout-api", 0, 12, Set.of("ordering", "pricing", "util")));
        Map<String, Integer> ca = new TreeMap<>();
        modules.forEach(m -> ca.putIfAbsent(m.name(), 0));
        modules.forEach(m -> m.dependsOn().forEach(d -> ca.merge(d, 1, Integer::sum)));

        System.out.println("module         Ca Ce    I     A     D");
        Set<String> concerns = new TreeSet<>();
        for (Module m : modules) {
            int a = ca.get(m.name()), e = m.dependsOn().size();
            double instability = a + e == 0 ? 0 : (double) e / (a + e);
            double abstractness = (double) m.abstractTypes() / m.totalTypes();
            double distance = Math.abs(abstractness + instability - 1);
            System.out.printf("%-14s %2d %2d  %.2f  %.2f  %.2f%n", m.name(), a, e, instability, abstractness, distance);
            if (distance > 0.7) concerns.add(m.name() + (instability < 0.5 ? " (stable and concrete: painful to change)" : " (unstable and abstract: useless abstractions)"));
        }
        System.out.println("far from the main sequence: " + concerns);
    }
}
```

1. Afferent coupling is counted from everyone else's dependencies.
2. Instability: the share of a module's couplings that point outward.
3. Distance from the main sequence: far from 0 means the module is in a zone of pain or uselessness.

**Run it:** `java CouplingMetricsDemo.java`

```text
module         Ca Ce    I     A     D
shared-kernel   3  0  0.00  0.75  0.25
pricing         2  1  0.33  0.10  0.57
ordering        1  3  0.75  0.10  0.15
inventory       1  1  0.50  0.04  0.46
util            1  0  0.00  0.00  1.00
checkout-api    0  3  1.00  0.00  0.00
far from the main sequence: [util (stable and concrete: painful to change)]
```

| Metric | Watch for |
|--------|-----------|
| High Ca + concrete | Hard to change, many dependants (`util`, `common`) |
| High Ce | Fragile; breaks when dependencies change |
| D near 1 | Zone of pain or uselessness |
| Cycles | Modules that can't be built, tested or released separately |
| Change coupling (git) | Files that always change together across modules |

**Use** metrics to direct attention, then look at the code. **Avoid** turning metrics into targets that teams game.
**Real-world Java:** JDepend, jQAssistant, Structure101, Sonargraph, CodeScene (change coupling from git history).
**Best practice:** track trends per module over releases rather than absolute values. **Gotcha:** a stable "shared" module full of concrete helpers (`util`) quietly becomes the most expensive code to change.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's controllers should go through services; one controller called a repository directly to save time.

**LayerRuleDemo.java**

```java
import java.util.List;
import java.util.Map;

/** Minimal: one fitness function. Controllers may call services, never repositories directly. */
public class LayerRuleDemo {

    public static void main(String[] args) {
        Map<String, List<String>> calls = Map.of(
                "OrderController", List.of("OrderService"),
                "SlotController", List.of("SlotService", "SlotRepository"));               // a shortcut someone took
        calls.keySet().stream().sorted().forEach(caller -> {
            List<String> bad = calls.get(caller).stream().filter(c -> c.endsWith("Repository")).toList();
            System.out.println(caller + (bad.isEmpty() ? ": ok" : ": FAIL calls " + bad + " directly"));
        });
    }
}
```

1. The rule: no controller calls a repository.
2. The check names the offender and what it called.

**Run it:** `java LayerRuleDemo.java`

**Output:**

```text
OrderController: ok
SlotController: FAIL calls [SlotRepository] directly
```

**What to notice:**

- A one-line rule, run on every build, is worth more than a page of architecture guidelines.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** FreshCart's checkout service build checks its dependencies: banned libraries, snapshots in releases and versions far behind the approved baseline.

**DependencyPolicyDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: a build-time fitness function for FreshCart's dependencies. Banned libraries, snapshot versions in a
 * release and libraries too many major versions behind the approved baseline fail the build; minor lag only warns.
 */
public class DependencyPolicyDemo {

    record Dependency(String coordinates, String version) {
        int major() { return Integer.parseInt(version.split("[.-]")[0]); }
    }

    static final Map<String, String> BANNED = new TreeMap<>(Map.of(
            "log4j:log4j", "Log4j 1.x is end-of-life; use Log4j 2 or Logback",
            "com.netflix.hystrix:hystrix-core", "Hystrix is in maintenance mode; use Resilience4j"));
    static final Map<String, Integer> APPROVED_MAJOR = Map.of(
            "org.springframework.boot:spring-boot", 3, "com.fasterxml.jackson.core:jackson-databind", 2, "org.flywaydb:flyway-core", 10);

    static List<String> check(List<Dependency> deps, boolean release) {
        List<String> findings = new ArrayList<>();
        for (Dependency d : deps) {
            if (BANNED.containsKey(d.coordinates())) findings.add("FAIL " + d.coordinates() + ": " + BANNED.get(d.coordinates()));
            if (release && d.version().endsWith("-SNAPSHOT")) findings.add("FAIL " + d.coordinates() + ":" + d.version() + " is a snapshot in a release build");
            Integer approved = APPROVED_MAJOR.get(d.coordinates());
            if (approved != null && d.major() < approved - 1) findings.add("FAIL " + d.coordinates() + " " + d.version() + " is 2+ majors behind " + approved);
            else if (approved != null && d.major() < approved) findings.add("WARN " + d.coordinates() + " " + d.version() + " is behind approved major " + approved);
        }
        return findings;
    }

    public static void main(String[] args) {
        List<Dependency> checkout = List.of(
                new Dependency("org.springframework.boot:spring-boot", "1.5.22.RELEASE"),
                new Dependency("com.fasterxml.jackson.core:jackson-databind", "2.18.2"),
                new Dependency("com.netflix.hystrix:hystrix-core", "1.5.18"),
                new Dependency("com.freshcart:pricing-client", "4.2.0-SNAPSHOT"),
                new Dependency("org.flywaydb:flyway-core", "9.22.3"));
        List<String> findings = check(checkout, true);
        findings.forEach(System.out::println);
        System.out.println(findings.stream().anyMatch(f -> f.startsWith("FAIL")) ? "build FAILED" : "build passed with warnings");
    }
}
```

1. Banned libraries carry the reason and the replacement.
2. Snapshot versions aren't reproducible, so they can't ship.
3. One major version behind warns; two or more fails.

**Run it:** `java DependencyPolicyDemo.java`

**Output:**

```text
FAIL org.springframework.boot:spring-boot 1.5.22.RELEASE is 2+ majors behind 3
FAIL com.netflix.hystrix:hystrix-core: Hystrix is in maintenance mode; use Resilience4j
FAIL com.freshcart:pricing-client:4.2.0-SNAPSHOT is a snapshot in a release build
WARN org.flywaydb:flyway-core 9.22.3 is behind approved major 10
build FAILED
```

**What to notice:**

- Warnings give teams time to upgrade; failures stop the riskiest cases.
- In Maven, the same policy can be expressed with the Enforcer plugin (`bannedDependencies`, `requireReleaseDeps`).

### Example 3: Production-grade

**Scenario:** FreshCart's modular monolith has known violations from its early days. The team wants to enforce module rules now without fixing everything first: new violations fail the build, and fixed ones must be removed from the baseline.

**ArchitectureFitnessSuiteDemo.java**

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade: FreshCart's architecture fitness suite for a modular monolith. It derives the module graph from
 * source imports, checks allowed dependencies, internal-package access and cycles, and applies a ratchet: existing
 * violations are recorded in a baseline and may only go down, so legacy debt doesn't block delivery but can't grow.
 */
public class ArchitectureFitnessSuiteDemo {

    static final Pattern PACKAGE = Pattern.compile("package com\\.freshcart\\.(\\w+)([.\\w]*);");
    static final Pattern IMPORT = Pattern.compile("import com\\.freshcart\\.(\\w+)((?:\\.\\w+)*)\\.\\w+;");

    record Violation(String rule, String detail) implements Comparable<Violation> {
        public int compareTo(Violation o) { return (rule + detail).compareTo(o.rule + o.detail); }
        public String toString() { return rule + ": " + detail; }
    }

    static Map<String, Set<String>> moduleGraph(Map<String, String> sources, List<Violation> violations) {
        Map<String, Set<String>> graph = new TreeMap<>();
        sources.forEach((file, code) -> {
            Matcher p = PACKAGE.matcher(code);
            if (!p.find()) return;
            String module = p.group(1);
            graph.computeIfAbsent(module, k -> new TreeSet<>());
            Matcher i = IMPORT.matcher(code);
            while (i.find()) {
                String target = i.group(1);
                if (target.equals(module)) continue;
                graph.get(module).add(target);
                if (i.group(2).startsWith(".internal"))
                    violations.add(new Violation("internal-access", file + " imports " + target + i.group(2)));
            }
        });
        return graph;
    }

    static List<Violation> evaluate(Map<String, String> sources, Map<String, Set<String>> allowed) {
        List<Violation> violations = new ArrayList<>();
        Map<String, Set<String>> graph = moduleGraph(sources, violations);
        graph.forEach((from, tos) -> tos.forEach(to -> {
            if (!allowed.getOrDefault(from, Set.of()).contains(to))
                violations.add(new Violation("allowed-dependencies", from + " -> " + to));
        }));
        for (List<String> cycle : cycles(graph)) violations.add(new Violation("no-cycles", String.join(" -> ", cycle)));
        violations.sort(null);
        return violations;
    }

    /** Depth-first search; reports each cycle once, starting from its alphabetically smallest module. */
    static List<List<String>> cycles(Map<String, Set<String>> graph) {
        Set<String> seen = new TreeSet<>();
        List<List<String>> found = new ArrayList<>();
        for (String start : graph.keySet()) dfs(graph, start, start, new ArrayList<>(List.of(start)), seen, found);
        return found;
    }
    static void dfs(Map<String, Set<String>> g, String start, String at, List<String> path, Set<String> seen, List<List<String>> found) {
        for (String next : g.getOrDefault(at, Set.of())) {
            if (next.equals(start)) {
                List<String> cycle = new ArrayList<>(path); cycle.add(start);
                if (path.stream().allMatch(m -> m.compareTo(start) >= 0) && seen.add(String.join(",", cycle))) found.add(cycle);
            } else if (!path.contains(next) && next.compareTo(start) > 0) {
                path.add(next); dfs(g, start, next, path, seen, found); path.remove(path.size() - 1);
            }
        }
    }

    static String ratchet(List<Violation> current, Set<String> baseline) {
        List<String> fresh = current.stream().map(Violation::toString).filter(v -> !baseline.contains(v)).toList();
        long fixed = baseline.stream().filter(b -> current.stream().noneMatch(v -> v.toString().equals(b))).count();
        String progress = fixed == 0 ? "" : ", " + fixed + " fixed (remove them from the baseline)";
        if (!fresh.isEmpty()) return "FAIL: " + fresh.size() + " new violation(s) " + fresh + progress;
        return "PASS: " + current.size() + " known violation(s) remain" + progress;
    }

    public static void main(String[] args) {
        Map<String, Set<String>> allowed = new HashMap<>(Map.of(
                "web", Set.of("ordering", "catalog"), "ordering", Set.of("pricing", "inventory", "shared"),
                "pricing", Set.of("shared"), "inventory", Set.of("shared"), "catalog", Set.of("shared")));
        Map<String, String> sources = new TreeMap<>(Map.of(
                "web/OrderController.java", "package com.freshcart.web; import com.freshcart.ordering.OrderService;",
                "ordering/OrderService.java", "package com.freshcart.ordering; import com.freshcart.pricing.PriceCalculator; import com.freshcart.inventory.StockService;",
                "pricing/PriceCalculator.java", "package com.freshcart.pricing; import com.freshcart.shared.Money;",
                "inventory/StockService.java", "package com.freshcart.inventory; import com.freshcart.ordering.OrderPlaced;",
                "catalog/ProductPage.java", "package com.freshcart.catalog; import com.freshcart.pricing.internal.DiscountTable;"));
        Set<String> baseline = Set.of(
                "allowed-dependencies: inventory -> ordering",
                "no-cycles: inventory -> ordering -> inventory",
                "allowed-dependencies: catalog -> pricing",
                "internal-access: catalog/ProductPage.java imports pricing.internal");

        List<Violation> v1 = evaluate(sources, allowed);
        v1.forEach(v -> System.out.println("  " + v));
        System.out.println("main branch: " + ratchet(v1, baseline));

        Map<String, String> pr = new TreeMap<>(sources);
        pr.put("catalog/ProductPage.java", "package com.freshcart.catalog; import com.freshcart.shared.Money;");        // fixes catalog
        pr.put("pricing/PromoBanner.java", "package com.freshcart.pricing; import com.freshcart.catalog.ProductPage;");  // new violation
        System.out.println("pull request: " + ratchet(evaluate(pr, allowed), baseline));
    }
}
```

1. The module graph is derived from source imports, so it's always current.
2. Imports of another module's `internal` packages are violations.
3. Each module's dependencies must be in the allowed list.
4. Cycle detection reports each cycle once.
5. The ratchet compares with the baseline: new violations fail; fixed ones are reported so the baseline shrinks.

**Run it:** `java ArchitectureFitnessSuiteDemo.java`

**Output:**

```text
  allowed-dependencies: catalog -> pricing
  allowed-dependencies: inventory -> ordering
  internal-access: catalog/ProductPage.java imports pricing.internal
  no-cycles: inventory -> ordering -> inventory
main branch: PASS: 4 known violation(s) remain
pull request: FAIL: 1 new violation(s) [allowed-dependencies: pricing -> catalog], 2 fixed (remove them from the baseline)
```

**What to notice:**

- The main branch passes with four known violations, so adopting the rules didn't stop delivery.
- The pull request fixed two violations but added a new dependency from pricing to catalog, and fails for that alone.
- ArchUnit's `FreezingArchRule` implements the same ratchet with a stored violation file.

## 7. Pros, Cons & Trade-offs

| Practice | Pros | Cons |
|----------|------|------|
| Fitness functions | Architecture is tested continuously | Need maintenance; can be noisy |
| ArchUnit | Fast, expressive, in Java | Only structural; needs good package conventions |
| JPMS | Compiler-enforced encapsulation | Framework friction; split packages; migration cost |
| Coupling metrics | Objective hotspots | Can be gamed; need interpretation |
| Ratchet baselines | Adoptable on legacy code | Baseline files need curation |

## 8. When to Use / When NOT to Use

**Use them when:**

- several teams work in one codebase or on shared libraries;
- architectural characteristics (latency, modularity) are business-critical;
- you're preventing a modular monolith from becoming a big ball of mud.

**Don't overdo them when:**

- a small team owns a small service and reviews every change together;
- rules would encode preferences rather than characteristics that matter;
- you can't act on a metric (don't measure it).

## 9. Best Practices

1. **Express key architecture rules as code that runs in CI.** *(Consensus.)*
2. **Start small: layers, internal packages, cycles.** *(Consensus.)*
3. **Ratchet existing violations instead of exempting them forever.** *(Consensus.)*
4. **Link each fitness function to its ADR and owner.** *(Widely used.)*
5. **Prefer trends over absolute metric targets.** *(Consensus.)*
6. **Use JPMS for libraries; ArchUnit or Spring Modulith for applications.** *(Widely used.)*
7. **Fail fast and clearly: name the rule, the offender and the fix.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Architecture by diagram only (production failure mode)**  
> **Symptom:** a pricing change broke checkout, search and the warehouse app at once.
> **Root cause:** three modules had imported `pricing.internal` classes over two years; nothing enforced the boundary in the diagram.
> **Fix:** ArchUnit rule for internal packages, frozen baseline, then remove violations module by module.

> 🚨 **Exemption creep**  
> **Symptom:** the architecture test has 140 `@ArchIgnore` and allow-list entries.
> **Root cause:** rules added without a ratchet; every violation got a permanent exemption.
> **Fix:** replace exemptions with a frozen baseline that only shrinks; review its size monthly.

> 🚨 **Metric targets**  
> **Symptom:** instability numbers looked perfect while the code got worse.
> **Root cause:** teams split modules artificially to hit metric targets.
> **Fix:** use metrics to start conversations, not as goals.

> 🚨 **Flaky performance gates**  
> **Symptom:** the latency fitness function failed randomly; the team disabled it, and a real regression shipped.
> **Root cause:** single-run timing on shared CI runners.
> **Fix:** repeated measurements, robust statistics, an "inconclusive" outcome, and dedicated runners for performance checks.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| ArchUnit `archunit.properties` `freeze.store.default.allowStoreCreation` | false | Allows creating the frozen-violation store | true on first adoption | Accidental re-baselining |
| ArchUnit `freeze.refreeze` | false | Re-records all violations | Never in CI | Hides new violations |
| ArchUnit `ImportOption` | all classes | Which classes are analysed | Exclude tests, generated code | Missing violations |
| javac `--add-exports` / `--add-opens` | none | Breaks encapsulation selectively | Legacy frameworks | Silent coupling to internals |
| Maven Enforcer `requireReleaseDeps` | off | Bans snapshots | Release builds | Blocks dev builds if global |

## 12. Security Considerations

- Dependency fitness functions (CVE scanning, banned libraries) are a first line of supply-chain defence.
- JPMS strong encapsulation limits reflective access to internals, reducing some exploitation paths; `--add-opens` undoes it.
- Architecture rules can enforce security boundaries: only the `security` module may call the crypto library; controllers must have authorisation annotations.

## 13. Performance & Scalability

ArchUnit imports thousands of classes in seconds; cache the import across tests with `@AnalyzeClasses`. Fitness suites should run in minutes to stay in the PR loop; put slow holistic checks (load tests) on a schedule or pre-release stage. Coupling analysis over git history (change coupling) can be expensive on large repos; run nightly.

## 14. Testing It

Fitness tooling is code too; test its rules, cycle detection and ratchet:

**ArchitectureFitnessSuiteTest.java**

```java
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureFitnessSuiteTest {

    static final Map<String, Set<String>> ALLOWED = Map.of("web", Set.of("ordering"), "ordering", Set.of("shared"), "shared", Set.of());

    static List<String> violations(Map<String, String> sources) {
        return ArchitectureFitnessSuiteDemo.evaluate(new TreeMap<>(sources), ALLOWED).stream().map(Object::toString).toList();
    }

    @Test
    void cleanCodebaseHasNoViolations() {
        assertEquals(List.of(), violations(Map.of(
                "a", "package com.freshcart.web; import com.freshcart.ordering.OrderService;",
                "b", "package com.freshcart.ordering; import com.freshcart.shared.Money;")));
    }

    @Test
    void disallowedDependencyIsReported() {
        assertEquals(List.of("allowed-dependencies: shared -> web"),
                violations(Map.of("a", "package com.freshcart.shared; import com.freshcart.web.Controller;")));
    }

    @Test
    void importsInsideTheSameModuleAreIgnored() {
        assertEquals(List.of(), violations(Map.of("a", "package com.freshcart.ordering; import com.freshcart.ordering.internal.Mapper;")));
    }

    @Test
    void internalPackageAccessAcrossModulesIsReported() {
        var v = violations(Map.of("a", "package com.freshcart.web; import com.freshcart.ordering.internal.Mapper;"));
        assertEquals(List.of("internal-access: a imports ordering.internal"), v);
    }

    @Test
    void cycleIsReportedOnce() {
        var graph = new TreeMap<String, Set<String>>(Map.of("a", Set.of("b"), "b", Set.of("c"), "c", Set.of("a")));
        assertEquals(List.of(List.of("a", "b", "c", "a")), ArchitectureFitnessSuiteDemo.cycles(graph));
    }

    @Test
    void ratchetFailsOnlyOnNewViolations() {
        var known = List.of(new ArchitectureFitnessSuiteDemo.Violation("r", "x"));
        assertTrue(ArchitectureFitnessSuiteDemo.ratchet(known, Set.of("r: x")).startsWith("PASS"));
        var worse = List.of(new ArchitectureFitnessSuiteDemo.Violation("r", "x"), new ArchitectureFitnessSuiteDemo.Violation("r", "y"));
        assertTrue(ArchitectureFitnessSuiteDemo.ratchet(worse, Set.of("r: x")).startsWith("FAIL: 1 new"));
        assertTrue(ArchitectureFitnessSuiteDemo.ratchet(List.of(), Set.of("r: x")).contains("1 fixed"));
    }
}
```

## 15. Observability & Operations

Publish fitness results as metrics: violations per rule over time, baseline size, dependency age, CVE counts, latency budgets. Show them on an architecture dashboard per module, and review trends in architecture forums. Continual fitness functions in production (SLO burn rates, error budgets) feed back into design priorities.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2002 | Robert C. Martin publishes package coupling metrics (*Agile Software Development*) |
| 2017 | Java 9: JPMS; ArchUnit first release; *Building Evolutionary Architectures* |
| 2019 | ArchUnit `FreezingArchRule` (0.x) |
| 2022 | ArchUnit 1.0; *Building Evolutionary Architectures* 2nd ed. |
| 2023 | Spring Modulith 1.0 (module verification built on ArchUnit) |

## 17. Expert Deep Dive

- **Architecture quanta.** Ford et al. define a quantum as an independently deployable unit with high functional cohesion; fitness functions can be scoped per quantum.
- **Change coupling.** Files that change together in git history reveal hidden dependencies static analysis misses (Adam Tornhill's *Your Code as a Crime Scene*).
- **Conway-aware rules.** Map modules to teams (CODEOWNERS) and flag dependencies that cross team boundaries without an agreed interface.
- **Runtime architecture checks.** Distributed tracing can verify allowed service-to-service calls in production.
- **jdeps.** The JDK's `jdeps` analyses class-level dependencies and JDK internal API use, useful before adopting JPMS.

> 📖 **War story: the pricing change that broke three apps**  
> FreshCart's pricing team renamed a method in `pricing.internal.DiscountTable`, expecting no impact outside their module. Checkout, search and the warehouse app had all imported it over two years, and the deploy broke all three. **Fixes:** an ArchUnit rule for internal packages with `FreezingArchRule` capturing the 23 existing violations; a public `PricingApi` for the legitimate needs; a monthly target to shrink the frozen store; after four months, zero violations and the freeze file deleted. **Lesson:** a boundary that isn't checked isn't a boundary.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's a fitness function?** An **automated check** of an architectural characteristic.
2. **What's ArchUnit?** A library for **architecture rules as unit tests**.
3. **What does `exports` do in module-info?** Makes a **package visible** to other modules.
4. **What's coupling?** **How much modules depend on each other.**
5. **Why avoid cycles between modules?** They **can't be changed or released independently**.

**▸ 🟡 L3**

1. **Atomic vs holistic fitness functions?** **One characteristic** vs **a combination**.
2. **How do you adopt rules on legacy code?** **Freeze existing violations; ratchet down.**
3. **What's instability?** **Ce / (Ca + Ce).**
4. **`requires transitive`?** **Re-exports a dependency** to your consumers.
5. **ArchUnit vs JPMS?** **Test-time rules** vs **compiler/runtime enforcement**.

**▸ 🔴 L4–L5**

1. **Zone of pain?** **Stable and concrete**: many dependants, hard to change.
2. **How do you keep performance fitness functions reliable?** **Repeated runs, robust stats, inconclusive outcomes, dedicated runners.**
3. **Why not JPMS for every Spring Boot app?** **Reflection-heavy frameworks, migration cost; ArchUnit/Modulith give most value.**
4. **What's change coupling?** **Files that change together** in history.
5. **How do fitness functions relate to ADRs?** **ADR records the decision; fitness function enforces it.**

**▸ 💻 Hands-on**

**Task:** Add a rule to `ArchitectureFitnessSuiteDemo` that fails when any module has more than 3 outgoing dependencies.

> ✅ **Solution**  
> ```java
> graph.forEach((module, deps) -> {
>     if (deps.size() > 3) violations.add(new Violation("max-dependencies", module + " depends on " + deps.size() + " modules"));
> });
> ```

## 19. Practice

- [ ] Add ArchUnit to a service and write the three starter rules.
    > 💡 **Hint**  
    > Layers, internal packages, slices free of cycles.

- [ ] Freeze the current violations and remove one per week.
    > 💡 **Hint**  
    > `FreezingArchRule.freeze(rule)` and commit the store.

- [ ] Run `jdeps --jdk-internals` on a legacy JAR.
    > 💡 **Hint**  
    > It lists uses of JDK internal APIs that JPMS will block.

- [ ] Compute instability for your project's modules and discuss the outliers.
    > 💡 **Hint**  
    > Start with the module everyone calls `common`.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - If a characteristic matters, make it an automated fitness function.
> - ArchUnit: layers, internal packages, cycles; freeze legacy violations.
> - JPMS: `exports` the API only; internals are compile errors for others.
> - I = Ce / (Ca + Ce); D = |A + I - 1|; beware stable concrete `util`.
> - Ratchet: new violations fail, fixed ones leave the baseline.
>
> ```java
> noClasses().that().resideOutsideOfPackage("..pricing..")
>         .should().dependOnClassesThat().resideInAPackage("..pricing.internal..");
> ```

## 21. Further Reading

- Neal Ford, Rebecca Parsons, Patrick Kua, Pramod Sadalage, *Building Evolutionary Architectures* (2nd ed., 2022).
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html).
- Nicolai Parlog, *The Java Module System* (2019).
- Robert C. Martin, *Agile Software Development: Principles, Patterns, and Practices* (2002), package metrics.

---

[← Previous: Choosing Patterns and Trade-offs](pattern-selection.md) · [Next: Legacy Modernisation Patterns →](legacy-modernisation.md)
