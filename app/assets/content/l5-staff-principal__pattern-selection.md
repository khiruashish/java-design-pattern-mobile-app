# Choosing Patterns and Trade-offs

L5 🔴 Staff / Principal · ⏱ 30 min read · Prerequisites: [Anti-Patterns and Code Smells](../l3-professional/anti-patterns-and-code-smells.md), [Architectural Patterns](../l4-senior/architectural-patterns.md)

> 📌 **TL;DR**  
> At staff level the question is rarely "how does this pattern work?" and usually "is it worth it here, and how will we know if we were wrong?" Every pattern adds **indirection**: more types and more jumps for every future reader, paid daily, in exchange for flexibility that may never be used. Make the trade-off explicit with **decision frameworks**: name the forces (variants, change rate, teams, runtime needs), apply thresholds, and check how sensitive the outcome is. Record significant decisions as **Architecture Decision Records** so the reasoning survives the people. Spend most care on **irreversible** decisions (one-way doors) and decide reversible ones quickly, buying reversibility with seams where it's cheap.

## 1. What It Is

**Plain English.** Knowing 40 patterns makes it tempting to use them. This page is about restraint and judgement: estimating what a pattern costs, deciding with the team in a way you can explain later, writing the decision down, and keeping the option to change your mind.

**Definition.**

| Practice | Purpose |
|----------|---------|
| Cost of indirection | Weigh reader and maintenance cost against flexibility gained |
| Decision frameworks | Make forces and thresholds explicit; test sensitivity |
| Architecture Decision Records (ADRs) | Short, immutable records of significant decisions and their context |
| Reversibility | Classify decisions by cost to undo; add seams where undoing matters |

## 2. Why It Exists

Overengineering and underengineering both cost money, but overengineering is harder to see: a Strategy-Factory-Registry for one rule looks professional and slows every change for years. Meanwhile, important decisions get made in meetings, and two years later nobody remembers why the system uses a rules engine or which constraints have since changed. Practices such as ADRs (Michael Nygard, 2011), the rule of three (Don Roberts, via Fowler's *Refactoring*), and "one-way vs two-way doors" (Amazon) exist to make judgement repeatable and visible.

## 3. Mental Model / Analogy

**Analogy.** Building a house. Adding a door between two rooms (indirection) is useful if people walk through it, but each door is also a draught and a thing to maintain. Load-bearing walls and foundations (one-way doors) deserve an architect and a written plan; paint colours (two-way doors) don't. The plan's margin notes ("we put the kitchen here because the gas line is on this side") are ADRs: they stop the next owner from moving the kitchen without knowing why.

**Technical model.** A decision is a choice under forces with consequences. Make the forces explicit, prefer the simplest option that satisfies them, estimate the cost of reversing it, and record it where code reviewers will find it.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Cost of indirection

**Description.** Each abstraction (interface, factory, registry, proxy) adds types and navigation steps between a reader and the behaviour they're looking for. The runtime cost is usually negligible; the **cognitive** cost is paid every time someone reads, debugs or changes the code. A pattern earns its keep when the variation it enables is real and frequent.

**Advantages / features.** Thinking about cost makes designs proportionate: direct code where things are stable, patterns where change concentrates.

**IndirectionCostDemo.java**

```java
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cost of indirection: the same discount rule written directly and through Strategy + Factory + Registry. The
 * patterned version costs more types and a deeper call path for every reader; it pays off only when variants and
 * change make it worth it.
 */
public class IndirectionCostDemo {

    static int depthAtCalculation;
    static void markDepth() { depthAtCalculation = Thread.currentThread().getStackTrace().length; }

    // ----- direct: one method -----
    static long directDiscount(String tier, long totalPaise) {
        markDepth();
        return tier.equals("GOLD") ? totalPaise / 10 : 0;
    }

    // ----- patterned: interface, implementations, factory, registry -----
    interface DiscountStrategy { long discount(long totalPaise); }
    static final class GoldDiscount implements DiscountStrategy { public long discount(long t) { markDepth(); return t / 10; } }
    static final class NoDiscount implements DiscountStrategy { public long discount(long t) { markDepth(); return 0; } }
    static final class DiscountRegistry {
        private final Map<String, Supplier<DiscountStrategy>> byTier = new HashMap<>();
        DiscountRegistry register(String tier, Supplier<DiscountStrategy> s) { byTier.put(tier, s); return this; }
        DiscountStrategy forTier(String tier) { return byTier.getOrDefault(tier, NoDiscount::new).get(); }
    }
    static final class DiscountService {
        private final DiscountRegistry registry;
        DiscountService(DiscountRegistry registry) { this.registry = registry; }
        long discount(String tier, long total) { return lookup(tier).discount(total); }
        private DiscountStrategy lookup(String tier) { return registry.forTier(tier); }
    }

    public static void main(String[] args) {
        long d1 = directDiscount("GOLD", 50_000);
        int directDepth = depthAtCalculation;
        DiscountService service = new DiscountService(new DiscountRegistry().register("GOLD", GoldDiscount::new));
        long d2 = service.discount("GOLD", 50_000);
        int patternedDepth = depthAtCalculation;

        System.out.println("same answer: " + (d1 == d2) + " (Rs " + d1 / 100 + ")");
        System.out.println("types to read: direct 0, patterned 5 (interface, 2 strategies, registry, service)");
        System.out.println("runtime cost: " + (patternedDepth - directDepth) + " extra stack frame (negligible)");
        System.out.println("reader cost: direct 1 jump to the rule; patterned 5 (service, lookup, registry, registration, strategy)");
        System.out.println("to add a PLATINUM tier: direct = edit 1 method; patterned = add 1 class + 1 registration");
    }
}
```

1. The direct version: the rule is where you'd look for it.
2. The patterned version routes through a service, a lookup, a registry and a strategy.
3. The benefit appears only when variants are added: a new class instead of editing a method.

**Run it:** `java IndirectionCostDemo.java`

```text
same answer: true (Rs 50)
types to read: direct 0, patterned 5 (interface, 2 strategies, registry, service)
runtime cost: 1 extra stack frame (negligible)
reader cost: direct 1 jump to the rule; patterned 5 (service, lookup, registry, registration, strategy)
to add a PLATINUM tier: direct = edit 1 method; patterned = add 1 class + 1 registration
```

| Indirection pays off when | It doesn't when |
|---------------------------|-----------------|
| Several variants exist now | "We might need it one day" |
| Variants change independently, often | The code changes once a year |
| Different teams own variants | One person owns all of it |
| Variants need isolated tests | A single table test covers everything |
| A seam is needed for a volatile dependency | The dependency is stable (the JDK) |

**Use** patterns where change concentrates. **Avoid** speculative generality ("YAGNI").
**Real-world Java:** Spring's own code is heavily layered because it's a framework with thousands of extension needs; your application usually isn't.
**Best practice:** state the variation a pattern absorbs in its Javadoc or ADR. **Gotcha:** removing a pattern later is also a cost; prefer adding it late over adding it early.

### Decision frameworks

**Description.** Structured ways to choose: list options, agree the criteria and their weights with the team before scoring, score, and then test **sensitivity** (does a small weight change flip the result?). For recurring decisions, write explicit thresholds (e.g. "three or more variants changing monthly → Strategy").

**Advantages / features.** Disagreements move from taste to forces and weights; close calls are visible as close; decisions are explainable later.

**DecisionMatrixDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A weighted decision matrix: score options against criteria the team agreed on, then check sensitivity: if a
 * small change in weights flips the winner, the decision is close and the trade-off needs discussion, not maths.
 */
public class DecisionMatrixDemo {

    record Option(String name, Map<String, Integer> scores) {}                                     // scores 1..5

    static String winner(List<Option> options, Map<String, Integer> weights, Map<String, Integer> totalsOut) {
        totalsOut.clear();
        for (Option o : options) {
            int total = 0;
            for (var w : weights.entrySet()) total += w.getValue() * o.scores().get(w.getKey());
            totalsOut.put(o.name(), total);
        }
        return totalsOut.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    }

    public static void main(String[] args) {
        List<Option> options = List.of(
                new Option("enum + switch", Map.of("simplicity", 5, "add-a-rule", 2, "testability", 3, "runtime-config", 1)),
                new Option("Strategy beans", Map.of("simplicity", 3, "add-a-rule", 4, "testability", 5, "runtime-config", 2)),
                new Option("rules engine", Map.of("simplicity", 1, "add-a-rule", 5, "testability", 3, "runtime-config", 5)));
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("simplicity", 3); weights.put("add-a-rule", 3); weights.put("testability", 2); weights.put("runtime-config", 1);

        Map<String, Integer> totals = new LinkedHashMap<>();
        System.out.println("baseline winner: " + winner(options, weights, totals) + " " + totals);
        for (String criterion : weights.keySet()) {
            Map<String, Integer> nudged = new LinkedHashMap<>(weights);
            nudged.merge(criterion, 2, Integer::sum);
            String w = winner(options, nudged, totals);
            System.out.println("  if " + criterion + " weighs +2: " + w + (w.equals("Strategy beans") ? "" : "  <- flips"));
        }
    }
}
```

1. Each option's score is the weighted sum over the agreed criteria.
2. Weights are agreed before scoring, so they can't be tuned to a favourite.
3. Nudging each weight shows which criterion the decision hinges on.

**Run it:** `java DecisionMatrixDemo.java`

```text
baseline winner: Strategy beans {enum + switch=28, Strategy beans=33, rules engine=29}
  if simplicity weighs +2: Strategy beans
  if add-a-rule weighs +2: Strategy beans
  if testability weighs +2: Strategy beans
  if runtime-config weighs +2: rules engine  <- flips
```

| Framework | Good for |
|-----------|----------|
| Weighted decision matrix | Comparing 2–5 options on several criteria |
| Explicit thresholds / heuristics | Recurring choices (when to extract a Strategy) |
| ATAM-style quality scenarios | Architecture-level trade-offs (latency vs consistency) |
| Cost of delay / reversibility | How long to spend deciding |
| Spike / prototype | When scores are guesses |

**Use** for decisions with several plausible options and real consequences. **Avoid** false precision: scores are opinions; the value is in the discussion and the sensitivity check.
**Real-world Java:** choosing between Spring Data JPA, jOOQ and plain JDBC; between Kafka and RabbitMQ; between Strategy beans and a rules engine.
**Best practice:** write the losing options and why they lost into the ADR. **Gotcha:** a decision that flips on one weight isn't decided by the matrix; it's decided by whoever sets that weight, so discuss that criterion explicitly.

### Architecture Decision Records

**Description.** An ADR is a short markdown file in the repository: number, title, status (Proposed, Accepted, Deprecated, Superseded), context (forces), decision, consequences (good and bad). ADRs are immutable once accepted; when a decision changes, a new ADR supersedes the old one, and both remain.

**Advantages / features.** New team members learn why, not just what; decisions are reviewed like code; superseding preserves history.

**AdrDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * Architecture Decision Records: short, numbered, immutable documents (context, decision, consequences, status).
 * Decisions aren't edited when they change; a new ADR supersedes the old one, so the history stays readable.
 */
public class AdrDemo {

    record Adr(int number, String title, String status, String context, String decision, String consequences, Integer supersedes) {
        String toMarkdown() {
            return "# ADR-" + String.format("%04d", number) + ": " + title + "\n\n"
                    + "Status: " + status + (supersedes == null ? "" : " (supersedes ADR-" + String.format("%04d", supersedes) + ")") + "\n\n"
                    + "## Context\n" + context + "\n\n## Decision\n" + decision + "\n\n## Consequences\n" + consequences + "\n";
        }
    }

    static List<Adr> supersede(List<Adr> log, int old, Adr replacement) {
        List<Adr> next = new ArrayList<>();
        for (Adr a : log)
            next.add(a.number() == old ? new Adr(a.number(), a.title(), "Superseded by ADR-" + String.format("%04d", replacement.number()),
                    a.context(), a.decision(), a.consequences(), a.supersedes()) : a);
        next.add(replacement);
        return next;
    }

    public static void main(String[] args) {
        List<Adr> log = List.of(new Adr(7, "Delivery fees as an enum switch", "Accepted",
                "Three fee rules, changed twice a year.", "Use an enum with a switch in DeliveryFeeCalculator.",
                "Simple; adding a rule needs a deploy.", null));
        Adr adr12 = new Adr(12, "Delivery fees as Strategy beans", "Accepted",
                "Eleven fee rules, city teams change them weekly.", "One Strategy bean per rule, selected by city and slot.",
                "Rules testable in isolation; one more indirection; enum removed.", 7);
        log = supersede(log, 7, adr12);
        System.out.print(adr12.toMarkdown());
        System.out.println();
        log.forEach(a -> System.out.println("ADR-" + String.format("%04d", a.number()) + " " + a.title() + " [" + a.status() + "]"));
    }
}
```

1. Nygard's template: title, status, context, decision, consequences.
2. Changing a decision means a new ADR and a status change on the old one, never editing it.
3. The log shows the whole history at a glance.

**Run it:** `java AdrDemo.java`

```text
# ADR-0012: Delivery fees as Strategy beans

Status: Accepted (supersedes ADR-0007)

## Context
Eleven fee rules, city teams change them weekly.

## Decision
One Strategy bean per rule, selected by city and slot.

## Consequences
Rules testable in isolation; one more indirection; enum removed.

ADR-0007 Delivery fees as an enum switch [Superseded by ADR-0012]
ADR-0012 Delivery fees as Strategy beans [Accepted]
```

| ADR practice | Why |
|--------------|-----|
| One decision per ADR | Easy to supersede precisely |
| Keep in the repo (`docs/adr/`) | Reviewed in PRs; versioned with code |
| Consequences include the downsides | Honest trade-offs build trust |
| Optional `Review-by` date | Forces a revisit when assumptions may expire |
| Cite ADR numbers in code comments | Readers find the reasoning |

**Use** for decisions that are costly to reverse, cross team boundaries, or will surprise future readers. **Avoid** ADRs for every library bump; that buries the important ones.
**Real-world Java:** adr-tools, Log4brains, MADR template; ADR folders in many open-source Java projects.
**Best practice:** write the ADR before or with the code change, in the same PR. **Gotcha:** editing an accepted ADR rewrites history; supersede it instead.

### Reversibility

**Description.** Estimate how expensive it would be to undo a decision: code touched, teams involved, data migrations, external contracts. **Two-way doors** (cheap to reverse) should be decided quickly and revisited with data; **one-way doors** (published schemas, data models, vendor lock-in) deserve prototypes, ADRs and review. Seams (ports, adapters, feature flags) convert some one-way doors into two-way ones at small cost.

**Advantages / features.** Effort goes where mistakes are expensive; teams move fast on everything else.

**ReversibilityDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reversibility: classify decisions as one-way or two-way doors by what it would cost to undo them, and buy
 * reversibility cheaply where it matters with a seam (an interface) at the boundary.
 */
public class ReversibilityDemo {

    record Decision(String name, int filesTouchedToUndo, int teamsInvolved, boolean dataMigration, boolean externalContract) {
        int reversalCost() { return filesTouchedToUndo * teamsInvolved * (dataMigration ? 5 : 1) * (externalContract ? 3 : 1); }
        String door() { return reversalCost() >= 100 ? "one-way door: slow down, write an ADR, prototype" : "two-way door: decide fast, revisit later"; }
    }

    public static void main(String[] args) {
        List<String> sources = List.of(
                "CheckoutService: PayLinkClient.charge(...)", "RefundService: PayLinkClient.refund(...)",
                "SubscriptionBilling: PayLinkClient.mandate(...)", "WalletTopUp: PayLinkClient.charge(...)",
                "AdminTools: PayLinkClient.lookup(...)", "Reconciliation: PayLinkClient.settlements(...)");
        System.out.println("PayLink calls spread through the code: " + sources.size() + " files to change to switch provider");
        System.out.println("behind a PaymentGateway port: 1 adapter file to change");

        Map<String, String> verdicts = new TreeMap<>();
        for (Decision d : List.of(
                new Decision("Caffeine vs Guava cache inside pricing", 2, 1, false, false),
                new Decision("PayLink SDK called directly everywhere", 6, 4, true, false),
                new Decision("Event schema published to 4 teams", 8, 5, true, true),
                new Decision("Payment provider behind a port", 1, 1, true, false)))
            verdicts.put(d.name(), "cost " + d.reversalCost() + ": " + d.door());
        verdicts.forEach((k, v) -> System.out.println(k + " -> " + v));
    }
}
```

1. A rough reversal-cost model: files, teams, data migration and external contracts multiply.
2. A port concentrates the vendor dependency in one adapter.
3. The same choice (PayLink) is a one-way door when spread through the code and a two-way door behind a port.

**Run it:** `java ReversibilityDemo.java`

```text
PayLink calls spread through the code: 6 files to change to switch provider
behind a PaymentGateway port: 1 adapter file to change
Caffeine vs Guava cache inside pricing -> cost 2: two-way door: decide fast, revisit later
Event schema published to 4 teams -> cost 600: one-way door: slow down, write an ADR, prototype
PayLink SDK called directly everywhere -> cost 120: one-way door: slow down, write an ADR, prototype
Payment provider behind a port -> cost 5: two-way door: decide fast, revisit later
```

| Usually one-way | Usually two-way |
|-----------------|-----------------|
| Published API and event schemas | Internal class structure |
| Database schema with data | In-process library choice behind a facade |
| Choice of primary datastore | Cache provider |
| Public URLs, identifiers | Feature implementations behind flags |
| Contracts with vendors | Build tooling |

**Use** reversibility to decide how much process a decision needs. **Avoid** treating everything as one-way (paralysis) or everything as two-way (expensive surprises).
**Real-world Java:** hexagonal ports for payment and messaging providers, feature flags, expand-and-contract schema migrations.
**Best practice:** for one-way doors, look for a way to make them two-way first. **Gotcha:** seams have a cost too; don't wrap stable dependencies just in case.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's delivery-fee rules grew one at a time over a year.

**RuleOfThreeDemo.java**

```java
/** Minimal: don't introduce a pattern for one case; tolerate two; extract when the third variant arrives. */
public class RuleOfThreeDemo {

    static String advice(int variants) {
        if (variants <= 1) return "write it inline; no abstraction yet";
        if (variants == 2) return "an if/else is fine; note the duplication";
        return "extract an abstraction (e.g. Strategy) now that the variation is real";
    }

    public static void main(String[] args) {
        String[] history = {"Mon: flat Rs 25 delivery fee", "Mar: free above Rs 199", "Jun: Rs 10 off for DairyDay subscribers"};
        for (int i = 0; i < history.length; i++) System.out.println(history[i] + " -> " + advice(i + 1));
    }
}
```

1. One case: no abstraction.
2. The third variant is the signal that the variation is real.

**Run it:** `java RuleOfThreeDemo.java`

**Output:**

```text
Mon: flat Rs 25 delivery fee -> write it inline; no abstraction yet
Mar: free above Rs 199 -> an if/else is fine; note the duplication
Jun: Rs 10 off for DairyDay subscribers -> extract an abstraction (e.g. Strategy) now that the variation is real
```

**What to notice:**

- Extracting at the third case means the abstraction is shaped by three real examples, not one guess.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the same delivery-fee design was reviewed three times as the business grew. The reviewer recorded forces, not opinions.

**DeliveryFeeReviewDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * Realistic: a design review for FreshCart's delivery-fee rules. Instead of arguing taste, the reviewer records the
 * forces (variants, change rate, who changes them, need for runtime changes) and applies explicit thresholds, so
 * the recommendation and its reasons can go straight into an ADR.
 */
public class DeliveryFeeReviewDemo {

    record Forces(int variants, int changesPerMonth, int teamsEditing, boolean businessEditsWithoutDeploy) {}
    record Recommendation(String design, List<String> reasons) {}

    static Recommendation review(Forces f) {
        List<String> reasons = new ArrayList<>();
        if (f.businessEditsWithoutDeploy()) {
            reasons.add("business must change rules without a deploy");
            if (f.variants() > 20) reasons.add(f.variants() + " rules: a table-driven or rules-engine approach scales better than classes");
            return new Recommendation(f.variants() > 20 ? "rules engine / decision table" : "rules as data in config", reasons);
        }
        if (f.variants() >= 3 && (f.changesPerMonth() >= 2 || f.teamsEditing() > 1)) {
            reasons.add(f.variants() + " variants changing " + f.changesPerMonth() + "x a month");
            if (f.teamsEditing() > 1) reasons.add(f.teamsEditing() + " teams edit rules: separate classes avoid merge conflicts");
            return new Recommendation("Strategy per rule, selected by a small registry", reasons);
        }
        reasons.add("few variants (" + f.variants() + "), rarely changed (" + f.changesPerMonth() + "x a month)");
        return new Recommendation("keep the enum + switch; revisit at the next variant", reasons);
    }

    public static void main(String[] args) {
        Forces[] snapshots = {
                new Forces(3, 0, 1, false),
                new Forces(11, 4, 3, false),
                new Forces(40, 30, 1, true)};
        String[] when = {"2025 Q1", "2026 Q2", "2026 Q4 proposal"};
        for (int i = 0; i < snapshots.length; i++) {
            Recommendation r = review(snapshots[i]);
            System.out.println(when[i] + ": " + r.design());
            r.reasons().forEach(reason -> System.out.println("    because " + reason));
        }
    }
}
```

1. Who changes the rules, and whether they need a deploy, is checked first: it changes the whole category of solution.
2. Thresholds for moving to Strategy: enough variants and enough change or ownership spread.
3. Below the thresholds, the simple design stays, with a stated trigger to revisit.

**Run it:** `java DeliveryFeeReviewDemo.java`

**Output:**

```text
2025 Q1: keep the enum + switch; revisit at the next variant
    because few variants (3), rarely changed (0x a month)
2026 Q2: Strategy per rule, selected by a small registry
    because 11 variants changing 4x a month
    because 3 teams edit rules: separate classes avoid merge conflicts
2026 Q4 proposal: rules engine / decision table
    because business must change rules without a deploy
    because 40 rules: a table-driven or rules-engine approach scales better than classes
```

**What to notice:**

- The right answer changed twice, and each answer was right at its time.
- The reasons print in a form that drops straight into an ADR's Context section.

### Example 3: Production-grade

**Scenario:** FreshCart keeps ADRs in `docs/adr/`. A CI step keeps them trustworthy: valid structure, valid links, reviews not forgotten, and no code citing decisions that have been replaced.

**AdrLogDemo.java**

```java
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade: a CI check for FreshCart's ADR folder. It parses ADR files, validates their structure and links,
 * flags decisions past their review date, finds code that still cites superseded ADRs, and prints an index.
 */
public class AdrLogDemo {

    record Adr(int number, String title, String status, Integer supersededBy, LocalDate reviewBy, Set<String> sections) {}

    static final Pattern HEADER = Pattern.compile("^# ADR-(\\d{4}): (.+)$", Pattern.MULTILINE);
    static final Pattern STATUS = Pattern.compile("^Status: (Proposed|Accepted|Deprecated|Superseded by ADR-(\\d{4}))$", Pattern.MULTILINE);
    static final Pattern REVIEW = Pattern.compile("^Review-by: (\\d{4}-\\d{2}-\\d{2})$", Pattern.MULTILINE);
    static final Pattern SECTION = Pattern.compile("^## (\\w+)$", Pattern.MULTILINE);
    static final Set<String> REQUIRED = Set.of("Context", "Decision", "Consequences");

    static Adr parse(String file, String text, List<String> problems) {
        Matcher h = HEADER.matcher(text), s = STATUS.matcher(text), r = REVIEW.matcher(text), sec = SECTION.matcher(text);
        if (!h.find()) { problems.add(file + ": missing '# ADR-NNNN: title' header"); return null; }
        if (!s.find()) { problems.add(file + ": missing or unknown Status"); return null; }
        Set<String> sections = new java.util.TreeSet<>();
        while (sec.find()) sections.add(sec.group(1));
        for (String required : REQUIRED) if (!sections.contains(required)) problems.add(file + ": missing section '" + required + "'");
        return new Adr(Integer.parseInt(h.group(1)), h.group(2), s.group(1).startsWith("Superseded") ? "Superseded" : s.group(1),
                s.group(2) == null ? null : Integer.parseInt(s.group(2)), r.find() ? LocalDate.parse(r.group(1)) : null, sections);
    }

    static List<String> check(Map<String, String> adrFiles, Map<String, String> sourceFiles, LocalDate today, Map<Integer, Adr> indexOut) {
        List<String> problems = new ArrayList<>();
        adrFiles.forEach((file, text) -> { Adr a = parse(file, text, problems); if (a != null) indexOut.put(a.number(), a); });
        for (Adr a : indexOut.values()) {
            if (a.supersededBy() != null && !indexOut.containsKey(a.supersededBy()))
                problems.add("ADR-" + id(a.number()) + ": superseded by missing ADR-" + id(a.supersededBy()));
            if (a.status().equals("Accepted") && a.reviewBy() != null && a.reviewBy().isBefore(today))
                problems.add("ADR-" + id(a.number()) + ": review was due " + a.reviewBy());
        }
        Pattern cite = Pattern.compile("ADR-(\\d{4})");
        sourceFiles.forEach((file, code) -> {
            Matcher m = cite.matcher(code);
            while (m.find()) {
                Adr cited = indexOut.get(Integer.parseInt(m.group(1)));
                if (cited == null) problems.add(file + ": cites unknown ADR-" + m.group(1));
                else if (cited.status().equals("Superseded"))
                    problems.add(file + ": cites superseded ADR-" + m.group(1) + "; see ADR-" + id(cited.supersededBy()));
            }
        });
        return problems;
    }

    static String id(int n) { return String.format("%04d", n); }

    public static void main(String[] args) {
        Map<String, String> adrs = new TreeMap<>();
        adrs.put("0007-delivery-fee-enum.md", "# ADR-0007: Delivery fees as an enum switch\nStatus: Superseded by ADR-0012\n## Context\n.\n## Decision\n.\n## Consequences\n.\n");
        adrs.put("0009-outbox.md", "# ADR-0009: Transactional outbox for order events\nStatus: Accepted\nReview-by: 2026-06-30\n## Context\n.\n## Decision\n.\n## Consequences\n.\n");
        adrs.put("0012-delivery-fee-strategies.md", "# ADR-0012: Delivery fees as Strategy beans\nStatus: Accepted\nReview-by: 2027-06-30\n## Context\n.\n## Decision\n.\n## Consequences\n.\n");
        adrs.put("0013-cache-provider.md", "# ADR-0013: Caffeine as L1 cache\nStatus: Proposed\n## Context\n.\n## Decision\n.\n");
        adrs.put("0014-sms.md", "# ADR-0014: TextBlast as SMS provider\nStatus: Superseded by ADR-0015\n## Context\n.\n## Decision\n.\n## Consequences\n.\n");
        Map<String, String> sources = new TreeMap<>(Map.of(
                "DeliveryFeeCalculator.java", "// Rules live in an enum, see ADR-0007\nenum FeeRule { FLAT, FREE_ABOVE_199 }",
                "OrderEventsPublisher.java", "// Outbox relay per ADR-0009",
                "OtpSender.java", "// Provider choice: ADR-0021"));

        Map<Integer, Adr> index = new TreeMap<>();
        List<String> problems = check(adrs, sources, LocalDate.of(2026, 11, 1), index);
        System.out.println("index:");
        index.values().forEach(a -> System.out.println("  ADR-" + id(a.number()) + " " + a.title() + " [" + a.status() + "]"));
        System.out.println(problems.size() + " problems:");
        problems.forEach(p -> System.out.println("  " + p));
    }
}
```

1. Parse each ADR file and check required sections and a known status.
2. A superseded ADR must point to an ADR that exists.
3. Accepted decisions with a passed review date are flagged.
4. Code comments citing superseded or unknown ADRs are flagged with the replacement.
5. An index of all decisions and statuses is printed for the docs site.

**Run it:** `java AdrLogDemo.java`

**Output:**

```text
index:
  ADR-0007 Delivery fees as an enum switch [Superseded]
  ADR-0009 Transactional outbox for order events [Accepted]
  ADR-0012 Delivery fees as Strategy beans [Accepted]
  ADR-0013 Caffeine as L1 cache [Proposed]
  ADR-0014 TextBlast as SMS provider [Superseded]
5 problems:
  0013-cache-provider.md: missing section 'Consequences'
  ADR-0009: review was due 2026-06-30
  ADR-0014: superseded by missing ADR-0015
  DeliveryFeeCalculator.java: cites superseded ADR-0007; see ADR-0012
  OtpSender.java: cites unknown ADR-0021
```

**What to notice:**

- `DeliveryFeeCalculator` still claims to follow ADR-0007, which was replaced; the check tells the reader where the current decision is.
- The outbox decision's review date passed in June; the check prompts a revisit rather than letting assumptions silently expire.
- In CI, a non-empty problem list fails the build (or posts a warning, for review dates).

## 7. Pros, Cons & Trade-offs

| Practice | Pros | Cons |
|----------|------|------|
| Counting indirection cost | Proportionate designs | Hard to quantify precisely |
| Decision matrices | Explicit trade-offs, sensitivity | False precision if misused |
| Explicit thresholds | Fast, consistent recurring decisions | Thresholds need revisiting |
| ADRs | Durable reasoning, onboarding | Writing discipline; can become bureaucracy |
| Reversibility analysis | Effort where it matters | Estimates are rough |

## 8. When to Use / When NOT to Use

**Use these practices when:**

- a decision affects several teams or is expensive to reverse;
- the team disagrees and needs a shared basis;
- you're reviewing designs for many teams.

**Don't when:**

- the decision is cheap to reverse and local (just decide);
- the matrix would be built to justify a decision already made;
- ADRs would document trivia.

## 9. Best Practices

1. **Prefer the simplest design that handles today's real variation.** *(Consensus.)*
2. **Extract patterns at the third real variant (rule of three).** *(Widely used heuristic.)*
3. **Agree criteria and weights before scoring options.** *(Consensus.)*
4. **Record significant decisions as ADRs in the repo, with downsides.** *(Consensus.)*
5. **Supersede, don't edit, accepted ADRs.** *(Consensus.)*
6. **Match process to reversibility.** *(Consensus.)*
7. **Add seams around volatile external dependencies, not stable ones.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Pattern-driven design (production failure mode)**  
> **Symptom:** a simple delivery-fee change took a week and touched 9 files; incident fixes were slow because nobody could find the rule.
> **Root cause:** Strategy, Factory, Registry and Decorator layers added for a rule set that had one variant and changed once a year.
> **Fix:** inline the abstraction, keep one seam at the module boundary, and set thresholds for when to reintroduce patterns.

> 🚨 **Decisions without records**  
> **Symptom:** a team removed a "pointless" outbox table and lost events in production.
> **Root cause:** the reason for the outbox lived only in a departed engineer's head.
> **Fix:** ADRs cited from code; CI flags citations of superseded or unknown ADRs.

> 🚨 **Matrix theatre**  
> **Symptom:** every evaluation recommends the architect's favourite technology.
> **Root cause:** weights adjusted after scoring.
> **Fix:** fix weights first, publish the sensitivity check, include dissent in the ADR.

> 🚨 **Everything is a one-way door**  
> **Symptom:** months of design reviews for a cache library choice.
> **Root cause:** no distinction between reversible and irreversible decisions.
> **Fix:** time-box two-way doors; reserve heavy process for one-way doors.

## 11. Configuration & Tuning

| Setting | Typical value | What it does | When to change it | Risk of changing |
|---------|---------------|--------------|-------------------|------------------|
| ADR folder | `docs/adr/` | Where ADRs live | Monorepos: per service | Hard to find if scattered |
| ADR template | Nygard or MADR | Required sections | Add "Options considered" | Longer ADRs get skipped |
| Review-by period | 12–24 months | Prompts a revisit | Fast-moving areas: shorter | Review fatigue |
| Extraction threshold | 3 variants | When to introduce Strategy | Higher for stable domains | Duplication lingers |
| Reversal-cost threshold | Team-defined | One-way door process | Calibrate with past incidents | Too low: paralysis |

## 12. Security Considerations

- Security-relevant decisions (crypto choices, auth flows, data retention) should always get ADRs with explicit threat assumptions and review dates.
- ADRs must not contain secrets, internal hostnames or vulnerability details that shouldn't be in the repo.
- Reversibility matters for security: a hard-coded crypto algorithm is a one-way door; design for crypto agility.

## 13. Performance & Scalability

Indirection rarely matters for runtime performance (the JIT inlines monomorphic calls), but it matters for team throughput: time to understand, change and debug. Decision practices scale an organisation: explicit thresholds and ADRs let many teams make consistent choices without a central bottleneck.

## 14. Testing It

Test decision tooling like any other code, especially when it runs in CI:

**AdrLogTest.java**

```java
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class AdrLogTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    static final String BODY = "## Context\n.\n## Decision\n.\n## Consequences\n.\n";

    static List<String> check(Map<String, String> adrs, Map<String, String> sources) {
        return AdrLogDemo.check(new TreeMap<>(adrs), new TreeMap<>(sources), TODAY, new TreeMap<>());
    }

    @Test
    void wellFormedLogHasNoProblems() {
        var adrs = Map.of("1.md", "# ADR-0001: Use Java 21\nStatus: Accepted\nReview-by: 2027-01-01\n" + BODY);
        assertEquals(List.of(), check(adrs, Map.of("A.java", "// see ADR-0001")));
    }

    @Test
    void missingRequiredSectionIsReported() {
        var problems = check(Map.of("1.md", "# ADR-0001: X\nStatus: Accepted\n## Context\n.\n## Decision\n.\n"), Map.of());
        assertEquals(List.of("1.md: missing section 'Consequences'"), problems);
    }

    @Test
    void unknownStatusIsRejected() {
        var problems = check(Map.of("1.md", "# ADR-0001: X\nStatus: Maybe\n" + BODY), Map.of());
        assertTrue(problems.get(0).contains("missing or unknown Status"));
    }

    @Test
    void danglingSupersededLinkIsReported() {
        var problems = check(Map.of("1.md", "# ADR-0001: X\nStatus: Superseded by ADR-0009\n" + BODY), Map.of());
        assertEquals(List.of("ADR-0001: superseded by missing ADR-0009"), problems);
    }

    @Test
    void overdueReviewOnlyForAcceptedDecisions() {
        var problems = check(Map.of(
                "1.md", "# ADR-0001: X\nStatus: Accepted\nReview-by: 2026-01-01\n" + BODY,
                "2.md", "# ADR-0002: Y\nStatus: Deprecated\nReview-by: 2026-01-01\n" + BODY), Map.of());
        assertEquals(List.of("ADR-0001: review was due 2026-01-01"), problems);
    }

    @Test
    void codeCitingSupersededAdrPointsToReplacement() {
        var problems = check(Map.of(
                "1.md", "# ADR-0001: Old\nStatus: Superseded by ADR-0002\n" + BODY,
                "2.md", "# ADR-0002: New\nStatus: Accepted\n" + BODY), Map.of("Fee.java", "// ADR-0001"));
        assertEquals(List.of("Fee.java: cites superseded ADR-0001; see ADR-0002"), problems);
    }
}
```

## 15. Observability & Operations

Track ADRs by status and age, overdue reviews, and code citations of superseded ADRs. Watch delivery metrics (lead time for changes, change failure rate) for areas with heavy abstraction; slow changes in a module are a signal to revisit its patterns.

## 16. Version Notes

| Year | Change |
|------|--------|
| 1999 | Fowler's *Refactoring*: rule of three, speculative generality smell |
| 2000 | SEI's ATAM for architecture trade-off analysis |
| 2011 | Michael Nygard, "Documenting Architecture Decisions" |
| 2016 | Bezos letter: "Type 1 and Type 2 decisions" (one-way and two-way doors) |
| 2017 | MADR (Markdown ADR) template |
| 2020s | ADRs adopted by many engineering orgs; tooling like Log4brains |

## 17. Expert Deep Dive

- **Options thinking.** Some abstractions are options: you pay a small premium (a seam) for the right to change later. Value them by volatility and cost of change.
- **Last responsible moment.** Delay decisions until the cost of not deciding exceeds the value of waiting for more information.
- **Architecture as a set of decisions.** Kruchten and Bosch framed architecture as decisions plus rationale; ADRs operationalise that view.
- **Socio-technical forces.** Team boundaries and ownership often decide patterns more than technical forces do (Conway).
- **Kill criteria.** For experiments, decide upfront what result would make you reverse the decision.

> 📖 **War story: the nine-file fee change**  
> A well-meaning FreshCart engineer designed delivery fees with Strategy, Abstract Factory, a registry and a decorator chain "for future flexibility" when there was one fee rule. Two years later the rule had changed twice, both times by people unfamiliar with the design, and each change took a week. During a pricing incident, on-call engineers needed 40 minutes to find where the fee was actually computed. **Fixes:** the layers were collapsed into a single calculator with a table test; an ADR recorded the thresholds for reintroducing Strategy; when the third and fourth rules arrived a year later, the team extracted Strategy in a day, shaped by real cases. **Lesson:** flexibility you don't use is pure cost; add it when the variation shows up.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Is using more patterns better?** **No**; each adds cost.
2. **What's YAGNI?** **"You aren't gonna need it"**: don't build for speculative needs.
3. **What's an ADR?** A **short record of a decision** and its reasons.
4. **What's the rule of three?** **Abstract at the third occurrence.**
5. **What's a trade-off?** **Gaining one quality at the cost of another.**

**▸ 🟡 L3**

1. **How do you decide whether to introduce Strategy?** **Variants, change frequency, ownership, testing needs.**
2. **What goes in an ADR?** **Context, decision, consequences, status.**
3. **How do you change an accepted ADR?** **Supersede it with a new one.**
4. **What's a decision matrix's main risk?** **False precision; tuned weights.**
5. **Runtime cost of indirection?** **Usually negligible**; cognitive cost dominates.

**▸ 🔴 L4–L5**

1. **One-way vs two-way doors?** **Irreversible vs cheap to reverse**; match process to reversibility.
2. **How do you make a one-way door two-way?** **Seams, flags, expand-and-contract, abstractions at volatile boundaries.**
3. **How do you keep ADRs useful at scale?** **In repo, cited from code, validated in CI, review dates.**
4. **How do you handle a close decision?** **Find the criterion it hinges on; discuss that; prototype.**
5. **When do you remove a pattern?** **When its variation never materialised and it slows change.**

**▸ 💻 Hands-on**

**Task:** Extend `AdrLogDemo` to flag two Accepted ADRs whose titles both start with "Delivery fees" (likely conflicting decisions).

> ✅ **Solution**  
> ```java
> Map<String, List<Integer>> byTopic = new TreeMap<>();
> for (Adr a : index.values())
>     if (a.status().equals("Accepted"))
>         byTopic.computeIfAbsent(a.title().split(" as ")[0], k -> new ArrayList<>()).add(a.number());
> byTopic.forEach((topic, ids) -> { if (ids.size() > 1) problems.add("possible conflict on '" + topic + "': " + ids); });
> ```

## 19. Practice

- [ ] Find one abstraction in your codebase with a single implementation and decide whether it's a seam worth keeping.
    > 💡 **Hint**  
    > Is the dependency behind it volatile or external?

- [ ] Write an ADR for a decision your team made last month.
    > 💡 **Hint**  
    > Include the options you rejected and the downsides you accepted.

- [ ] Run a decision matrix with your team and do the sensitivity check together.
    > 💡 **Hint**  
    > Agree weights before anyone scores.

- [ ] Classify your last five architecture decisions as one-way or two-way doors.
    > 💡 **Hint**  
    > Count teams, data and external contracts affected.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Patterns cost readers every day; use them where variation is real.
> - Rule of three; no speculative generality.
> - Agree criteria and weights first; check sensitivity.
> - ADRs: context, decision, consequences, status; supersede, don't edit; cite from code.
> - One-way doors get care; two-way doors get speed; seams make doors two-way.
>
> ```java
> if (variants >= 3 && (changesPerMonth >= 2 || teamsEditing > 1)) return "Strategy";
> return "keep it simple; revisit at the next variant";
> ```

## 21. Further Reading

- Michael Nygard, [Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) (2011).
- Neal Ford, Mark Richards, Pramod Sadalage, Zhamak Dehghani, *Software Architecture: The Hard Parts* (2021).
- Martin Fowler, *Refactoring* (2nd ed., 2018), "Speculative Generality".
- [MADR: Markdown Architectural Decision Records](https://adr.github.io/madr/).

---

[← Previous: L5 Staff / Principal](index.md) · [Next: Evolutionary Architecture →](evolutionary-architecture.md)
