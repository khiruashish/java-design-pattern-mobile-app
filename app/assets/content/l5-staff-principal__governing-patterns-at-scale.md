# Governing Patterns Across an Organisation

L5 🔴 Staff / Principal · ⏱ 35 min read · Prerequisites: [Pattern Selection](pattern-selection.md), [Evolutionary Architecture](evolutionary-architecture.md), [Anti-Patterns and Code Smells](../l3-professional/anti-patterns-and-code-smells.md)

> 📌 **TL;DR**  
> With 50 engineers, patterns stop being individual choices and become organisational habits. Govern them with **light, automated, help-oriented** tools: a **pattern catalogue** with ADOPT/TRIAL/HOLD status enforced by lint; **risk-based design reviews** so small changes move fast; **reference implementations** that show the golden path and measure drift; a **technical debt strategy** that budgets capacity by payback; and **deliberate teaching** through skills matrices, pairing and katas. Measure health, but guard against gamed metrics, and use results to offer help, not to rank teams.

## 1. What It Is

**Plain English.** Governance is how an engineering organisation makes good design the easy default: what patterns we use, how designs get reviewed, where the examples live, how we pay down debt, and how people learn. Done well, it feels like paved roads; done badly, it feels like a committee.

**Definition.**

| Practice | Question it answers |
|----------|---------------------|
| Coding standards and pattern catalogues | Which patterns do we use here, and which do we avoid? |
| Design review checklists | How much review does this change need? |
| Reference implementations | What does "good" look like, concretely? |
| Technical debt strategy | What do we fix, when, and with whose time? |
| Teaching patterns to teams | How do people learn the above? |

## 2. Why It Exists

At FreshCart, eight teams solved retries in eight ways, two services used a service locator, and one team adopted event sourcing for a CRUD feature. None of these were bad engineers; there was simply no shared memory of decisions. Without governance, quality depends on who happens to review a PR. With heavy governance, teams route around it. Staff engineers design the middle path: decisions recorded once, enforced by tools, explained by examples, and changed when they stop serving the teams.

## 3. Mental Model / Analogy

**Analogy.** City planning. You don't approve every house; you publish building codes (catalogue), require inspections only for big or risky buildings (risk-based review), build model homes people can copy (reference implementations), budget for road repairs every year (debt strategy), and run apprenticeships (teaching). Codes evolve as materials improve.

**Technical model.** Governance is a feedback loop: **decide** (catalogue, ADRs) → **enable** (references, starters, teaching) → **check** (lint, review, fitness functions) → **measure** (scorecard) → **adjust** the decisions.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Coding standards and pattern catalogues

**Description.** A pattern catalogue lists the patterns an organisation uses, with a **status** (ADOPT: default; TRIAL: allowed with review; HOLD: don't start new uses; RETIRE: actively remove), an **owner**, a **rationale**, and a link to the reference implementation. Standards that matter are encoded as **lint rules** (Checkstyle, PMD, Error Prone, ArchUnit, OpenRewrite recipes) so they're checked on every PR, not remembered.

**Advantages / features.** Consistent solutions across teams; fewer bikeshedding debates; easier movement of engineers between teams.

**PatternCatalogueDemo.java**

```java
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A pattern catalogue as data, in the style of a technology radar: each entry has a status (ADOPT, TRIAL, HOLD), an
 * owner and the preferred alternative. A lint rule per HOLD entry scans code and points developers to the guidance,
 * so the catalogue is enforced by tooling instead of by memory.
 */
public class PatternCatalogueDemo {

    enum Status { ADOPT, TRIAL, HOLD }

    record Entry(String pattern, Status status, String owner, String guidance, Pattern detector) {}

    static final List<Entry> CATALOGUE = List.of(
            new Entry("Constructor injection", Status.ADOPT, "platform-guild", "default for all Spring beans", null),
            new Entry("Transactional outbox", Status.ADOPT, "data-guild", "use freshcart-outbox starter", null),
            new Entry("Event sourcing", Status.TRIAL, "data-guild", "only with an approved design review", null),
            new Entry("Service locator", Status.HOLD, "platform-guild", "use constructor injection instead",
                    Pattern.compile("\\.getBean\\(")),
            new Entry("Mutable static singleton", Status.HOLD, "platform-guild", "inject a bean, or use an immutable enum",
                    Pattern.compile("static\\s+(?!final)\\w+(<[^>]*>)?\\s+\\w+\\s*=\\s*new\\s")));

    public static void main(String[] args) {
        System.out.println("catalogue:");
        for (Entry e : CATALOGUE) System.out.printf("  %-6s %-26s owner %s%n", e.status(), e.pattern(), e.owner());

        Map<String, String> sources = new TreeMap<>();
        sources.put("ordering/OrderService.java", "class OrderService { Pricing p = ctx.getBean(Pricing.class); }");
        sources.put("pricing/PriceCache.java", "class PriceCache { static Map<String, Long> CACHE = new HashMap<>(); }");
        sources.put("pricing/PricingService.java", "class PricingService { PricingService(PriceRepository repo) {} }");

        System.out.println("lint:");
        sources.forEach((file, code) -> CATALOGUE.stream()
                .filter(e -> e.status() == Status.HOLD && e.detector().matcher(code).find())
                .forEach(e -> System.out.println("  " + file + ": " + e.pattern() + " is HOLD, " + e.guidance() + " (ask " + e.owner() + ")")));
    }
}
```

1. Each catalogue entry is data: status, owner and guidance, so it can drive tooling.
2. A HOLD entry carries a detector, here a regex; real projects use ArchUnit or Error Prone.
3. The lint message tells developers what to use instead and whom to ask.

**Run it:** `java PatternCatalogueDemo.java`

```text
catalogue:
  ADOPT  Constructor injection      owner platform-guild
  ADOPT  Transactional outbox       owner data-guild
  TRIAL  Event sourcing             owner data-guild
  HOLD   Service locator            owner platform-guild
  HOLD   Mutable static singleton   owner platform-guild
lint:
  ordering/OrderService.java: Service locator is HOLD, use constructor injection instead (ask platform-guild)
  pricing/PriceCache.java: Mutable static singleton is HOLD, inject a bean, or use an immutable enum (ask platform-guild)
```

| Status | Meaning | Enforcement |
|--------|---------|-------------|
| ADOPT | Default choice | Starters and templates use it |
| TRIAL | Allowed with design review | Review checklist question |
| HOLD | No new uses | Lint warning or error on new code |
| RETIRE | Remove existing uses | Migration tracked as debt |

**Use** a catalogue once several teams share a codebase or platform. **Avoid** catalogues without owners or rationale; they rot into rules nobody can explain.
**Real-world Java:** ThoughtWorks Technology Radar format, Google's Java style guide plus Error Prone, Spotify's golden paths.
**Best practice:** every entry links to an ADR and a reference example. **Gotcha:** HOLD rules applied to existing code create thousands of warnings; ratchet (fail only on new violations) as in [Evolutionary Architecture](evolutionary-architecture.md).

### Design review checklists

**Description.** A design review checklist turns senior judgement into questions anyone can answer: does this change data ownership, touch money, change a partner API, introduce a TRIAL pattern, make rollback hard? Answers produce a **risk score** that routes the design to self-serve, peer review, or architecture review. Unanswered questions count as risk.

**Advantages / features.** Fast path for low-risk work; senior attention reserved for decisions that are hard to reverse; consistent questions regardless of reviewer.

**DesignReviewChecklistDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A risk-based design review checklist: each question has a weight, and some answers trigger a mandatory
 * architecture review. Unanswered questions count as risk. The total decides how much review a design needs, so
 * small changes move fast and risky ones get senior eyes.
 */
public class DesignReviewChecklistDemo {

    record Question(String id, String text, int weight, boolean escalatesWhenYes) {}

    static final List<Question> QUESTIONS = List.of(
            new Question("data", "Does it change who owns a database or data contract?", 3, true),
            new Question("money", "Does it touch payments, refunds or pricing?", 3, false),
            new Question("partner", "Does it change an API used by partners (QuickDrop, ParcelGo, PayLink)?", 3, true),
            new Question("new-pattern", "Does it introduce a pattern marked TRIAL or HOLD?", 2, false),
            new Question("rollback", "Is rollback harder than a redeploy?", 2, false),
            new Question("traffic", "Will it handle more than 1000 requests per second?", 1, false));

    enum Level { SELF_SERVE, PEER_REVIEW, ARCHITECTURE_REVIEW }

    static void review(String design, Map<String, Boolean> answers) {
        int risk = 0;
        boolean escalate = false;
        List<String> open = new ArrayList<>();
        for (Question q : QUESTIONS) {
            Boolean yes = answers.get(q.id());
            if (yes == null) {
                risk += q.weight();
                open.add(q.id());
            } else if (yes) {
                risk += q.weight();
                escalate |= q.escalatesWhenYes();
            }
        }
        Level level = escalate || risk >= 6 ? Level.ARCHITECTURE_REVIEW : risk >= 3 ? Level.PEER_REVIEW : Level.SELF_SERVE;
        System.out.println(design + ": risk " + risk + " -> " + level + (open.isEmpty() ? "" : ", unanswered " + open));
    }

    public static void main(String[] args) {
        review("Add banner text to home screen", Map.of("data", false, "money", false, "partner", false,
                "new-pattern", false, "rollback", false, "traffic", true));
        review("Cache delivery slots with a TRIAL pattern", Map.of("data", false, "money", false, "partner", false,
                "new-pattern", true, "rollback", false, "traffic", true));
        review("Split loyalty points into its own service", Map.of("data", true, "money", true, "partner", false,
                "new-pattern", false, "rollback", true));
    }
}
```

1. Each question has a weight, and some answers escalate regardless of total score.
2. An unanswered question is treated as a "yes", which encourages people to answer.
3. The score and escalations choose the review level.

**Run it:** `java DesignReviewChecklistDemo.java`

```text
Add banner text to home screen: risk 1 -> SELF_SERVE
Cache delivery slots with a TRIAL pattern: risk 3 -> PEER_REVIEW
Split loyalty points into its own service: risk 9 -> ARCHITECTURE_REVIEW, unanswered [traffic]
```

| Review level | Typical trigger | Turnaround |
|--------------|-----------------|------------|
| Self-serve | Low risk, reversible | Immediate |
| Peer review | Moderate risk | 1 to 2 days |
| Architecture review | One-way doors: data ownership, partner APIs, money | Weekly forum |

**Use** checklists for design docs and PR templates. **Avoid** making every change go through the architecture forum; it becomes a queue and people stop writing designs.
**Real-world Java:** RFC processes at Uber and Spotify, Google design docs, AWS operational readiness reviews.
**Best practice:** review the checklist itself every quarter using incidents as input. **Gotcha:** checklists become box-ticking; keep them short and ask "why" in reviews.

### Reference implementations

**Description.** A **reference implementation** is a real, running, tested service that shows the approved way to combine patterns: health checks, structured logging, timeouts and retries, outbox, contract tests, feature flags. It's paired with a project template or starter (Spring Boot starter, Backstage software template). Comparing services with the reference gives a **drift** measure.

**Advantages / features.** Examples teach better than rules; new services start correct; upgrades are demonstrated once and copied.

**ReferenceImplementationDemo.java**

```java
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A reference implementation (golden path) is a real, running service that shows the approved way to combine
 * patterns. Each capability has a version; services are compared with the reference to measure drift, and the most
 * drifted services get help first. The reference teaches by example; the drift report shows where examples didn't land.
 */
public class ReferenceImplementationDemo {

    record Capability(String name, int version) {}

    static final Set<Capability> REFERENCE = Set.of(
            new Capability("health-and-readiness", 1),
            new Capability("structured-logging", 2),
            new Capability("timeouts-and-retries", 2),
            new Capability("transactional-outbox", 1),
            new Capability("contract-tests", 1),
            new Capability("feature-flags", 1));

    record Service(String name, Set<Capability> has) {
        int driftPercent() {
            long missing = REFERENCE.stream().filter(c -> !has.contains(c)).count();
            return (int) (missing * 100 / REFERENCE.size());
        }
        Set<String> gaps() {
            Set<String> gaps = new TreeSet<>();
            for (Capability c : REFERENCE) {
                if (has.contains(c)) continue;
                boolean older = has.stream().anyMatch(h -> h.name().equals(c.name()));
                gaps.add(c.name() + (older ? " (old version)" : ""));
            }
            return gaps;
        }
    }

    public static void main(String[] args) {
        List<Service> services = List.of(
                new Service("checkout", Set.of(new Capability("health-and-readiness", 1), new Capability("structured-logging", 2),
                        new Capability("timeouts-and-retries", 2), new Capability("transactional-outbox", 1),
                        new Capability("contract-tests", 1), new Capability("feature-flags", 1))),
                new Service("catalogue", Set.of(new Capability("health-and-readiness", 1), new Capability("structured-logging", 1),
                        new Capability("timeouts-and-retries", 2), new Capability("feature-flags", 1))),
                new Service("loyalty", Set.of(new Capability("health-and-readiness", 1), new Capability("timeouts-and-retries", 1))));

        Map<String, Integer> drift = new LinkedHashMap<>();
        services.stream().sorted(Comparator.comparingInt(Service::driftPercent).reversed().thenComparing(Service::name))
                .forEach(s -> {
                    drift.put(s.name(), s.driftPercent());
                    System.out.println(s.name() + ": drift " + s.driftPercent() + "%" + (s.gaps().isEmpty() ? "" : ", gaps " + s.gaps()));
                });
        System.out.println("help first: " + drift.keySet().iterator().next());
    }
}
```

1. The reference capabilities, versioned so improvements (structured logging v2) are visible.
2. Drift is the share of reference capabilities a service lacks or has in an older version.
3. Services are ranked so the platform team helps the most drifted first.

**Run it:** `java ReferenceImplementationDemo.java`

```text
loyalty: drift 83%, gaps [contract-tests, feature-flags, structured-logging, timeouts-and-retries (old version), transactional-outbox]
catalogue: drift 50%, gaps [contract-tests, structured-logging (old version), transactional-outbox]
checkout: drift 0%
help first: loyalty
```

| Form | Strength | Weakness |
|------|----------|----------|
| Reference service | Real, runnable, tested | Must be maintained like product code |
| Project template | Fast start | Drifts after generation |
| Shared starter library | Upgrades centrally | Coupling, version management |
| Docs only | Cheap | Goes stale fastest |

**Use** a reference service plus a starter library for cross-cutting concerns. **Avoid** a reference nobody runs in production; it drifts from reality.
**Real-world Java:** Spring Boot starters, Backstage software templates, internal "golden path" services.
**Best practice:** run the reference in production with real traffic (an internal tool). **Gotcha:** templates are copied once; fixes don't propagate, so put shared behaviour in libraries.

### Technical debt strategy

**Description.** Treat debt like a loan: **principal** is the effort to fix it, **interest** is the time lost every month while it stays (slow reviews, incidents, workarounds). Rank items by payback period (principal ÷ interest) and fund them from a fixed share of capacity (commonly 15 to 25%), rather than "when we have time". Keep unfunded items visible with their running cost.

**Advantages / features.** Debt becomes a business conversation with numbers; teams stop asking permission for each fix; the most expensive debt gets paid first.

**TechDebtStrategyDemo.java**

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A technical debt strategy treats debt like a loan: principal (effort to fix) and interest (time lost every month
 * while it stays). Items are ranked by payback period and funded from a fixed share of team capacity, so debt work is
 * planned and visible instead of heroic. Unfunded items stay on the register with their running cost.
 */
public class TechDebtStrategyDemo {

    record Debt(String item, int principalDays, double interestDaysPerMonth) {
        double paybackMonths() { return principalDays / interestDaysPerMonth; }
    }

    public static void main(String[] args) {
        List<Debt> register = List.of(
                new Debt("God class CheckoutService (merge conflicts, slow reviews)", 15, 4.0),
                new Debt("Hand-rolled retry loops in 9 clients", 6, 3.0),
                new Debt("Service locator in ordering", 4, 0.5),
                new Debt("Flaky end-to-end suite", 8, 5.0),
                new Debt("Legacy XML config for pricing", 20, 1.0));

        int teamDaysPerQuarter = 3 * 60;
        int budget = teamDaysPerQuarter * 20 / 100;
        System.out.println("quarter capacity " + teamDaysPerQuarter + " days, debt budget " + budget + " days (20%)");

        List<Debt> ranked = new ArrayList<>(register);
        ranked.sort(Comparator.comparingDouble(Debt::paybackMonths));
        int spent = 0;
        double monthlyInterestLeft = 0;
        for (Debt d : ranked) {
            boolean funded = spent + d.principalDays() <= budget;
            if (funded) spent += d.principalDays(); else monthlyInterestLeft += d.interestDaysPerMonth();
            System.out.printf("  %-58s payback %4.1f months  %s%n", d.item(), d.paybackMonths(), funded ? "FUNDED" : "register");
        }
        System.out.printf("spent %d of %d days; still paying %.1f days per month on unfunded debt%n", spent, budget, monthlyInterestLeft);
    }
}
```

1. Each item records principal and interest, estimated roughly; rough numbers beat none.
2. A fixed share of quarterly capacity funds debt work.
3. Items with the shortest payback are funded first; the rest stay on the register.

**Run it:** `java TechDebtStrategyDemo.java`

```text
quarter capacity 180 days, debt budget 36 days (20%)
  Flaky end-to-end suite                                     payback  1.6 months  FUNDED
  Hand-rolled retry loops in 9 clients                       payback  2.0 months  FUNDED
  God class CheckoutService (merge conflicts, slow reviews)  payback  3.8 months  FUNDED
  Service locator in ordering                                payback  8.0 months  FUNDED
  Legacy XML config for pricing                              payback 20.0 months  register
spent 33 of 36 days; still paying 1.0 days per month on unfunded debt
```

| Strategy | When it fits |
|----------|--------------|
| Fixed capacity share (15–25%) | Steady product teams |
| Boy scout rule | Small, local cleanups |
| Debt sprints / fix-it weeks | Cross-team or tooling debt |
| Tie debt to feature work | Debt in code about to change anyway |
| Strangler migration | Large legacy systems |

**Use** a register with interest estimates for anything bigger than a day. **Avoid** "debt sprints" as the only mechanism; debt grows continuously.
**Real-world Java:** Martin Fowler's technical debt quadrant, Google's fix-it weeks, SonarQube debt estimates (as one input).
**Best practice:** prioritise debt in code that changes often (hotspots: churn × complexity). **Gotcha:** tool-estimated debt (lint counts) ignores interest; old ugly code that never changes costs little.

### Teaching patterns to teams

**Description.** Patterns spread through people, not documents. Use a **skills matrix** (self-rated 0–4 per area) to find gaps, **pair** learners with mentors, assign **katas** (small practice problems), run **guilds** or communities of practice, and do **design walkthroughs** of real code. Areas with no mentor are an organisational risk to fix with hiring or external training.

**Advantages / features.** Reduces bus factor; makes standards understood rather than obeyed; grows the next staff engineers.

**TeachingPatternsDemo.java**

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Teaching patterns to teams with a skills matrix: each engineer rates themselves 0 to 4 per pattern area. Gaps
 * become pairing sessions with a mentor (level 3 or more, spread so nobody mentors too many people) and a kata
 * from the playbook. Areas with no mentor at all are the real organisational risk.
 */
public class TeachingPatternsDemo {

    static final List<String> AREAS = List.of("resilience", "messaging", "caching", "testing");

    public static void main(String[] args) {
        Map<String, int[]> matrix = new TreeMap<>();
        matrix.put("Asha",  new int[] {4, 1, 3, 2});
        matrix.put("Bilal", new int[] {1, 1, 2, 4});
        matrix.put("Chen",  new int[] {2, 1, 4, 1});
        matrix.put("Dev",   new int[] {0, 1, 1, 1});
        Map<String, String> kata = Map.of("resilience", "resilience-patterns practice 1", "messaging", "messaging-patterns practice 2",
                "caching", "caching-patterns practice 1", "testing", "testing-patterns practice 3");

        Map<String, Integer> mentorLoad = new HashMap<>();
        for (int a = 0; a < AREAS.size(); a++) {
            String area = AREAS.get(a);
            List<String> mentors = new ArrayList<>(), learners = new ArrayList<>();
            for (var e : matrix.entrySet()) {
                if (e.getValue()[a] >= 3) mentors.add(e.getKey()); else if (e.getValue()[a] <= 1) learners.add(e.getKey());
            }
            if (mentors.isEmpty()) {
                System.out.println(area + ": NO MENTOR for " + learners + ", bring in a guild expert or an external course");
                continue;
            }
            for (String learner : learners) {
                String mentor = mentors.stream().min(Comparator.comparingInt((String m) -> mentorLoad.getOrDefault(m, 0))
                        .thenComparing(Comparator.naturalOrder())).orElseThrow();
                mentorLoad.merge(mentor, 1, Integer::sum);
                System.out.println(area + ": " + learner + " pairs with " + mentor + ", kata: " + kata.get(area));
            }
        }
        System.out.println("mentor load: " + new TreeMap<>(mentorLoad));
    }
}
```

1. The skills matrix: one row per engineer, one column per pattern area.
2. An area with no mentor is reported as a risk instead of silently skipped.
3. Mentors are chosen by current load, then by name, so nobody gets every learner.

**Run it:** `java TeachingPatternsDemo.java`

```text
resilience: Bilal pairs with Asha, kata: resilience-patterns practice 1
resilience: Dev pairs with Asha, kata: resilience-patterns practice 1
messaging: NO MENTOR for [Asha, Bilal, Chen, Dev], bring in a guild expert or an external course
caching: Dev pairs with Chen, kata: caching-patterns practice 1
testing: Chen pairs with Bilal, kata: testing-patterns practice 3
testing: Dev pairs with Bilal, kata: testing-patterns practice 3
mentor load: {Asha=2, Bilal=2, Chen=1}
```

| Technique | Good for | Cost |
|-----------|----------|------|
| Pair programming | Tacit knowledge | Two people's time |
| Katas | Practising a pattern safely | Prep time |
| Guild / community of practice | Cross-team consistency | Regular time slot |
| Design walkthroughs | Applying patterns to real systems | Senior time |
| Playbooks like this one | Self-study reference | Maintenance |

**Use** a mix: playbook for reference, katas for practice, pairing for transfer. **Avoid** one-off training days with no follow-up.
**Real-world Java:** Spotify guilds, internal "Java university" programmes, code retreats.
**Best practice:** teach with your own code and incidents, not textbook examples. **Gotcha:** self-ratings drift (people under- or over-rate); calibrate with short practical exercises.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** FreshCart's PR template has a checklist; CI blocks merging while required items are unchecked.

**PrChecklistGateDemo.java**

```java
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The smallest useful governance tool: a pull request template with a checklist, and a CI step that blocks the
 * merge while a required item is unchecked. Optional items are reminders only.
 */
public class PrChecklistGateDemo {

    static final List<String> REQUIRED = List.of("Tests added or updated", "No new HOLD patterns from the catalogue");

    static void gate(String pr, String description) {
        Pattern item = Pattern.compile("^- \\[( |x)\\] (.+)$", Pattern.MULTILINE);
        Matcher m = item.matcher(description);
        java.util.Set<String> checked = new java.util.TreeSet<>();
        while (m.find()) if (m.group(1).equals("x")) checked.add(m.group(2).strip());
        List<String> missing = REQUIRED.stream().filter(r -> !checked.contains(r)).toList();
        System.out.println(pr + ": " + (missing.isEmpty() ? "ready to merge" : "BLOCKED, unchecked " + missing));
    }

    public static void main(String[] args) {
        gate("PR 412 add slot cache", """
                Adds a cache for delivery slots.
                - [x] Tests added or updated
                - [x] No new HOLD patterns from the catalogue
                - [ ] Design review linked (optional)
                """);
        gate("PR 413 quick fix", """
                Hotfix for coupon rounding.
                - [x] Tests added or updated
                - [ ] No new HOLD patterns from the catalogue
                """);
    }
}
```

1. Markdown checkboxes are parsed from the PR description.
2. Only required items block the merge; optional ones stay reminders.

**Run it:** `java PrChecklistGateDemo.java`

**Output:**

```text
PR 412 add slot cache: ready to merge
PR 413 quick fix: BLOCKED, unchecked [No new HOLD patterns from the catalogue]
```

**What to notice:**

- The gate checks that someone made a conscious statement; the lint rules check the code itself.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** anonymous TODOs pile up and nobody owns them. FreshCart replaces them with structured debt comments that have an owner and an expiry date, and a CI scanner reports them per team.

**DebtCommentScannerDemo.java**

```java
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Making technical debt visible where it lives: a structured comment format with owner and expiry date replaces
 * anonymous TODOs. A scanner (run in CI) builds a per-team register and flags expired items and untracked TODOs.
 */
public class DebtCommentScannerDemo {

    static final Pattern DEBT = Pattern.compile("//\\s*DEBT\\(owner=([\\w-]+), expires=(\\d{4}-\\d{2}-\\d{2})\\):\\s*(.+)");
    static final Pattern TODO = Pattern.compile("//\\s*(TODO|FIXME)\\b");

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 11, 1);
        Map<String, String> sources = new TreeMap<>();
        sources.put("checkout/CheckoutService.java", """
                // DEBT(owner=checkout-team, expires=2026-09-30): split pricing out of CheckoutService
                // DEBT(owner=checkout-team, expires=2027-03-31): replace manual retries with resilience4j
                """);
        sources.put("loyalty/PointsClient.java", """
                // TODO make this faster
                // DEBT(owner=growth-team, expires=2027-01-15): remove ServiceLocator lookup
                """);

        Map<String, int[]> perTeam = new TreeMap<>();                                              // owner -> {open, expired}
        sources.forEach((file, code) -> code.lines().forEach(line -> {
            Matcher d = DEBT.matcher(line);
            if (d.find()) {
                boolean expired = LocalDate.parse(d.group(2)).isBefore(today);
                int[] counts = perTeam.computeIfAbsent(d.group(1), k -> new int[2]);
                counts[0]++;
                if (expired) {
                    counts[1]++;
                    System.out.println("EXPIRED " + file + " (" + d.group(1) + ", " + d.group(2) + "): " + d.group(3));
                }
            } else if (TODO.matcher(line).find()) {
                System.out.println("UNTRACKED " + file + ": " + line.strip() + " -> add owner and expiry");
            }
        }));
        perTeam.forEach((team, c) -> System.out.println(team + ": open debt " + c[0] + ", expired " + c[1]));
    }
}
```

1. The structured format: owner team, expiry date and description.
2. A fixed date keeps the output deterministic; CI would use the build date.
3. A per-team summary feeds the debt register and the scorecard.

**Run it:** `java DebtCommentScannerDemo.java`

**Output:**

```text
EXPIRED checkout/CheckoutService.java (checkout-team, 2026-09-30): split pricing out of CheckoutService
UNTRACKED loyalty/PointsClient.java: // TODO make this faster -> add owner and expiry
checkout-team: open debt 2, expired 1
growth-team: open debt 1, expired 0
```

**What to notice:**

- Expired items are reported, not blocked: the team decides to fix, extend with a reason, or accept.
- Untracked TODOs get a nudge to add an owner, so debt stops being anonymous.

### Example 3: Production-grade

**Scenario:** each quarter, FreshCart's staff engineers review engineering health across teams. The scorecard combines signals from the other tools, compares with last quarter, detects a gamed metric, and proposes supportive actions.

**GovernanceScorecardDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FreshCart's quarterly engineering health scorecard. It combines signals from the governance tools (catalogue lint,
 * reference drift, debt register, code review data) into one score per team, compares it with last quarter, guards
 * against gamed metrics, and turns results into offers of help rather than rankings.
 */
public class GovernanceScorecardDemo {

    record TeamSignals(String team, int holdViolations, int driftPercent, double debtInterestDaysPerMonth,
                       int reviewCoveragePercent, double medianReviewMinutes, int lastQuarterScore) {}

    record Result(String team, int score, int trend, List<String> flags, String action) {}

    static final Map<String, Integer> WEIGHTS = new LinkedHashMap<>();
    static {
        WEIGHTS.put("catalogue", 30);
        WEIGHTS.put("reference", 25);
        WEIGHTS.put("debt", 25);
        WEIGHTS.put("review", 20);
    }

    static Map<String, Integer> normalise(TeamSignals s, List<String> flags) {
        Map<String, Integer> n = new LinkedHashMap<>();
        n.put("catalogue", Math.max(0, 100 - 20 * s.holdViolations()));
        n.put("reference", 100 - s.driftPercent());
        n.put("debt", (int) Math.max(0, Math.round(100 - 10 * s.debtInterestDaysPerMonth())));
        int review = s.reviewCoveragePercent();
        if (review >= 90 && s.medianReviewMinutes() < 3) {
            flags.add("rubber-stamp reviews suspected (median " + s.medianReviewMinutes() + " min)");
            review = Math.min(review, 50);
        }
        n.put("review", review);
        return n;
    }

    static Result evaluate(TeamSignals s) {
        List<String> flags = new ArrayList<>();
        Map<String, Integer> n = normalise(s, flags);
        int total = 0;
        for (var w : WEIGHTS.entrySet()) total += w.getValue() * n.get(w.getKey());
        int score = Math.round(total / 100f);
        int trend = score - s.lastQuarterScore();
        String action = score < 60 ? "offer guild pairing and a reference migration sprint"
                : trend <= -10 ? "tech lead check-in: what changed this quarter?"
                : !flags.isEmpty() ? "review practices workshop"
                : "share what works at the guild demo";
        return new Result(s.team(), score, trend, flags, action);
    }

    public static void main(String[] args) {
        List<TeamSignals> teams = List.of(
                new TeamSignals("checkout", 0, 0, 1.0, 95, 24, 92),
                new TeamSignals("catalogue", 1, 50, 2.5, 88, 18, 85),
                new TeamSignals("loyalty", 2, 83, 4.0, 100, 1.5, 49));
        System.out.println("weights " + WEIGHTS);
        for (TeamSignals t : teams) {
            Result r = evaluate(t);
            System.out.printf("%-9s score %3d (%+d vs last quarter) -> %s%n", r.team(), r.score(), r.trend(), r.action());
            r.flags().forEach(f -> System.out.println("          flag: " + f));
        }
    }
}
```

1. Weights are explicit and published, so teams know what matters.
2. Each signal is normalised to 0–100, higher is better, and clamped at zero.
3. A Goodhart guard: 100% review coverage with 1.5-minute median reviews is flagged and capped.
4. The trend versus last quarter matters as much as the absolute score.
5. Results map to offers of help, never to rankings or penalties.

**Run it:** `java GovernanceScorecardDemo.java`

**Output:**

```text
weights {catalogue=30, reference=25, debt=25, review=20}
checkout  score  97 (+5 vs last quarter) -> share what works at the guild demo
catalogue score  73 (-12 vs last quarter) -> tech lead check-in: what changed this quarter?
loyalty   score  47 (-2 vs last quarter) -> offer guild pairing and a reference migration sprint
          flag: rubber-stamp reviews suspected (median 1.5 min)
```

**What to notice:**

- Catalogue has a decent score but dropped 12 points, so it gets a conversation rather than a lecture.
- Loyalty's perfect review coverage was not real; the guard prevented a misleadingly good score.
- If scores were used for performance ratings, teams would optimise the numbers instead of the systems.

## 7. Pros, Cons & Trade-offs

| Practice | Pros | Cons |
|----------|------|------|
| Pattern catalogue + lint | Consistency, fast feedback | Needs owners; can ossify |
| Risk-based review | Fast path for most work | Calibration effort |
| Reference implementations | Teaches by example | Maintenance cost |
| Debt budget | Predictable paydown | Estimates are rough |
| Teaching programmes | Lasting capability | Slow, needs senior time |
| Scorecards | Visibility across teams | Gaming and fear if misused |

## 8. When to Use / When NOT to Use

**Formalise governance when:**

- several teams share platforms, data or partners;
- incidents repeat across teams for the same reason;
- onboarding takes months because "every service is different".

**Don't when:**

- one small team can agree in a conversation;
- the organisation is still searching for product fit and code is short-lived;
- the process would be enforced by people instead of tools.

## 9. Best Practices

1. **Automate standards; humans review what tools can't.** *(Consensus.)*
2. **Every rule has an owner, a rationale and an example.** *(Consensus.)*
3. **Scale review effort with risk and reversibility.** *(Widely used.)*
4. **Make the golden path the easiest path (templates, starters).** *(Widely used; platform engineering.)*
5. **Budget debt work as a fixed share of capacity.** *(Widely used.)*
6. **Measure to help, not to rank; watch for Goodhart's law.** *(Consensus.)*
7. **Revisit the catalogue regularly and retire stale rules.** *(Consensus.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Architecture review as a bottleneck (production failure mode)**  
> **Symptom:** a critical fix for a payment timeout waited nine days for the weekly architecture forum; meanwhile checkout errors continued.
> **Root cause:** every design went to the same forum regardless of risk; the queue was weeks long.
> **Fix:** risk-based routing (Design review checklists), an emergency path with retrospective review, and forum time only for one-way doors.

> 🚨 **Ivory tower standards**  
> **Symptom:** teams ignore the standards wiki.
> **Root cause:** rules written by people who don't ship, without examples or tooling.
> **Fix:** reference implementations maintained by practising engineers; rules as lint with clear messages.

> 🚨 **Metric gaming**  
> **Symptom:** review coverage jumps to 100% while incidents rise.
> **Root cause:** coverage became a target, so reviews became rubber stamps.
> **Fix:** combine metrics, add guards (review time), and use results for conversations.

> 🚨 **Frozen catalogue**  
> **Symptom:** a HOLD on a pattern the JDK now supports well (for example virtual threads instead of reactive for simple I/O) blocks good designs.
> **Root cause:** nobody reviews old decisions.
> **Fix:** each entry has a review date and an owner; quarterly guild review.

## 11. Configuration & Tuning

| Option | Typical default | What it does | When to change it | Risk of changing |
|--------|-----------------|--------------|-------------------|------------------|
| Debt capacity share | 20% | Funds debt work | Raise after incidents, lower in crunch for one quarter | Debt grows if cut repeatedly |
| Review escalation threshold | Risk 6 | Routes to architecture review | Tune with incident data | Too low: queue; too high: missed risks |
| HOLD rule severity | Warning on old, error on new | Enforces catalogue | When migration is funded | Noise if too strict |
| Debt comment max expiry | 6 months | Keeps debt items fresh | Longer for large migrations | Stale items |
| Scorecard weights | Published per quarter | Signals what matters | When priorities change | Confusing if changed often |
| Guild cadence | Every 2 weeks | Knowledge sharing | Fewer when stable | Loss of momentum |

## 12. Security Considerations

- Put security patterns in the catalogue (parameterised queries, secret handling, authn/authz libraries) and enforce them with SAST rules (Semgrep, CodeQL).
- The design review checklist should include data classification and threat modelling triggers.
- Reference implementations must include secure defaults (TLS, dependency scanning, least privilege), since teams copy them.
- Governance tools themselves need access control: who can change a HOLD rule or a scorecard weight.

## 13. Performance & Scalability

Governance must scale with the organisation: automated checks scale linearly, human review doesn't. Keep CI checks fast (seconds) or they get disabled. A federated model (guild owners per area, platform team for tooling) scales better than a single architecture group. Measure the process too: review lead time, percentage of designs needing architecture review, time from new rule to tooling.

## 14. Testing It

Governance tools are code; test them like code:

**GovernanceScorecardTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GovernanceScorecardTest {

    private static GovernanceScorecardDemo.TeamSignals team(int violations, int drift, double interest, int coverage, double minutes, int last) {
        return new GovernanceScorecardDemo.TeamSignals("t", violations, drift, interest, coverage, minutes, last);
    }

    @Test
    void weightsAddUpToOneHundred() {
        assertEquals(100, GovernanceScorecardDemo.WEIGHTS.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void perfectTeamScoresOneHundred() {
        var r = GovernanceScorecardDemo.evaluate(team(0, 0, 0, 100, 20, 100));
        assertEquals(100, r.score());
        assertEquals(0, r.trend());
        assertTrue(r.flags().isEmpty());
    }

    @Test
    void normalisedSignalsAreClampedToZero() {
        var n = GovernanceScorecardDemo.normalise(team(9, 100, 50, 0, 20, 0), new ArrayList<>());
        assertEquals(List.of(0, 0, 0, 0), List.copyOf(n.values()));
    }

    @Test
    void rubberStampReviewsAreCappedAndFlagged() {
        List<String> flags = new ArrayList<>();
        var n = GovernanceScorecardDemo.normalise(team(0, 0, 0, 100, 1, 0), flags);
        assertEquals(50, n.get("review"));
        assertEquals(1, flags.size());
    }

    @Test
    void lowScoresGetHelpAndSharpDropsGetACheckIn() {
        assertTrue(GovernanceScorecardDemo.evaluate(team(3, 80, 6, 50, 20, 40)).action().startsWith("offer guild pairing"));
        assertTrue(GovernanceScorecardDemo.evaluate(team(1, 40, 2, 90, 20, 95)).action().startsWith("tech lead check-in"));
    }
}
```

Also test lint rules with known-good and known-bad samples, and run the debt scanner on a fixture repository in CI.

## 15. Observability & Operations

Publish a governance dashboard: catalogue violations by team (new vs existing), reference drift, debt register size and interest, review lead times, and architecture review queue length. Track trends, not snapshots. Hold a short quarterly review with team leads to decide catalogue changes and help offers.

## 16. Version Notes

| Year | Change |
|------|--------|
| 1992 | Ward Cunningham coins "technical debt" |
| 2010 | ThoughtWorks Technology Radar first published |
| 2011 | Michael Nygard's ADR format |
| 2012 | Spotify's squads, tribes, chapters and guilds paper |
| 2017 | *Building Evolutionary Architectures* (fitness functions) |
| 2019 | *Team Topologies* (platform and enabling teams) |
| 2020 | Backstage open-sourced by Spotify (software templates) |
| 2023 | Platform engineering and golden paths go mainstream |

## 17. Expert Deep Dive

- **Paved roads, not gates.** Netflix's model: the supported path is so convenient that most teams choose it; leaving it is allowed but you own the consequences.
- **Enabling teams.** From *Team Topologies*: a small team that helps others adopt practices for a limited time, then moves on, instead of owning the practice forever.
- **Inverse Conway manoeuvre.** Shape team boundaries to get the architecture you want; governance that fights the org chart loses.
- **Decision records at scale.** ADRs per service plus organisation-level ADRs for catalogue changes, linked from lint messages.
- **Hotspot analysis.** Combine git churn with complexity (Adam Tornhill's *Your Code as a Crime Scene*) to find where debt actually costs.

> 📖 **War story: the forum that slowed everything down**  
> FreshCart's architecture forum met every Tuesday and reviewed every design. As the company grew to 12 teams, the queue reached three weeks, so teams stopped writing designs and shipped directly, including a change that moved loyalty data into a new database without a migration plan. **Fixes:** the risk-based checklist sent 80% of designs to self-serve or peer review; the forum focused on one-way doors; staff engineers held office hours; HOLD patterns moved into lint. Design docs went up, not down, because writing one no longer meant waiting. **Lesson:** governance that slows people down gets bypassed; make the right path the fast path.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's a pattern catalogue?** A **list of approved and avoided patterns** with guidance.
2. **What's a reference implementation?** A **working example of the approved way**.
3. **What's technical debt?** **Shortcuts whose cost grows over time.**
4. **Why use a PR checklist?** To make **required steps explicit and checkable**.
5. **What's a guild?** A **cross-team community** around a skill.

**▸ 🟡 L3**

1. **ADOPT vs TRIAL vs HOLD?** **Default** vs **allowed with review** vs **no new uses**.
2. **Why risk-based review?** **Fast path for safe changes**, attention for risky ones.
3. **Principal vs interest?** **Cost to fix** vs **ongoing cost of not fixing**.
4. **How do you roll out a new lint rule on old code?** **Ratchet: fail only new violations.**
5. **Why do templates drift?** **Copied once**; fixes don't propagate.

**▸ 🔴 L4–L5**

1. **How do you avoid governance becoming a bottleneck?** **Automate, route by risk, federate ownership.**
2. **How do you prioritise debt?** **Payback period, hotspots, fixed capacity share.**
3. **What's Goodhart's law here?** **Targeted metrics get gamed**; combine and guard them.
4. **Paved road vs gate?** **Make the right way easiest** instead of blocking alternatives.
5. **How do you retire a standard?** **Owner review, ADR superseding it, update lint and references.**

**▸ 💻 Hands-on**

**Task:** Extend `TechDebtStrategyDemo` so items in hotspot files (changed more than 20 times last quarter) get their interest doubled before ranking.

> ✅ **Solution**  
> ```java
> record Debt(String item, int principalDays, double interestDaysPerMonth, int changesLastQuarter) {
>     double effectiveInterest() { return changesLastQuarter > 20 ? interestDaysPerMonth * 2 : interestDaysPerMonth; }
>     double paybackMonths() { return principalDays / effectiveInterest(); }
> }
> // the God class with 35 changes now ranks above the retry loops
> ```

## 19. Practice

- [ ] Write a five-entry pattern catalogue for your team with status, owner and rationale.
    > 💡 **Hint**  
    > Start with patterns you argued about in the last month of reviews.

- [ ] Turn one HOLD entry into an ArchUnit rule with a ratchet baseline.
    > 💡 **Hint**  
    > Reuse the approach from the Evolutionary Architecture page.

- [ ] Estimate principal and interest for your three biggest debt items.
    > 💡 **Hint**  
    > Interest: ask "how many hours did this cost us last month?"

- [ ] Run a one-hour kata session using a practice item from this playbook.
    > 💡 **Hint**  
    > Pair people across skill levels and review solutions together.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Decide → enable → check → measure → adjust.
> - Catalogue with ADOPT/TRIAL/HOLD, owners, and lint enforcement.
> - Risk-based review: one-way doors get senior eyes.
> - Reference implementations teach; drift shows where they didn't land.
> - Debt = principal + interest; fund by payback from fixed capacity.
> - Teach with pairing, katas and guilds; measure to help, not rank.
>
> ```java
> Level level = escalate || risk >= 6 ? ARCHITECTURE_REVIEW : risk >= 3 ? PEER_REVIEW : SELF_SERVE;
> ```

## 21. Further Reading

- Matthew Skelton and Manuel Pais, *Team Topologies* (2019).
- Neal Ford, Rebecca Parsons, Patrick Kua and Pramod Sadalage, *Building Evolutionary Architectures*, 2nd ed. (2022).
- Adam Tornhill, *Your Code as a Crime Scene*, 2nd ed. (2024).
- Martin Fowler, [Technical Debt Quadrant](https://martinfowler.com/bliki/TechnicalDebtQuadrant.html).
- Tanya Reilly, *The Staff Engineer's Path* (2022).

---

[← Previous: Designing Frameworks and Libraries](framework-and-library-design.md)
