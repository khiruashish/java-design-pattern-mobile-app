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

    static Map<String, Set<String>> moduleGraph(Map<String, String> sources, List<Violation> violations) {   // (1)!
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
                if (i.group(2).startsWith(".internal"))                                                 // (2)!
                    violations.add(new Violation("internal-access", file + " imports " + target + i.group(2)));
            }
        });
        return graph;
    }

    static List<Violation> evaluate(Map<String, String> sources, Map<String, Set<String>> allowed) {
        List<Violation> violations = new ArrayList<>();
        Map<String, Set<String>> graph = moduleGraph(sources, violations);
        graph.forEach((from, tos) -> tos.forEach(to -> {
            if (!allowed.getOrDefault(from, Set.of()).contains(to))                                   // (3)!
                violations.add(new Violation("allowed-dependencies", from + " -> " + to));
        }));
        for (List<String> cycle : cycles(graph)) violations.add(new Violation("no-cycles", String.join(" -> ", cycle)));
        violations.sort(null);
        return violations;
    }

    /** Depth-first search; reports each cycle once, starting from its alphabetically smallest module. */
    static List<List<String>> cycles(Map<String, Set<String>> graph) {                                  // (4)!
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

    static String ratchet(List<Violation> current, Set<String> baseline) {                              // (5)!
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
