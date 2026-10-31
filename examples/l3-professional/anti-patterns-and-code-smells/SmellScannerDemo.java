import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small CI gate for code smells: scans Java source text, applies thresholds, compares with a baseline of
 * accepted issues, and fails the build only on new ones. Real teams use Sonar, PMD or Error Prone; the mechanics
 * (rules, thresholds, baselines, gating only new issues) are the same.
 */
public class SmellScannerDemo {

    enum Severity { INFO, MAJOR, CRITICAL }
    record Finding(String file, String rule, Severity severity, String detail) {
        String key() { return file + ":" + rule + ":" + detail; }                        // (1)!
    }
    record Thresholds(int maxMethodLines, int maxParams, int maxFields, int maxMethods) {
        Thresholds {
            if (maxMethodLines < 5 || maxParams < 1 || maxFields < 1 || maxMethods < 1) throw new IllegalArgumentException("thresholds too low");
        }
    }

    private static final Pattern METHOD = Pattern.compile("^\\s+(?:public |private |protected |static |final )*[\\w<>\\[\\], ]+\\s+(\\w+)\\(([^)]*)\\)\\s*(?:throws [\\w, ]+)?\\{");
    private static final Pattern FIELD = Pattern.compile("^\\s{4}(?:private |public |protected |static |final )*[\\w<>\\[\\], ]+\\s+\\w+\\s*(=.*)?;\\s*$");
    private static final Pattern EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*}");
    private static final Pattern GET_INSTANCE = Pattern.compile("\\b(\\w+)\\.getInstance\\(\\)");

    static List<Finding> scan(String file, String source, Thresholds t) {              // (2)!
        List<Finding> out = new ArrayList<>();
        String[] lines = source.split("\n");
        int fields = 0, methods = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (FIELD.matcher(line).matches()) fields++;
            Matcher m = METHOD.matcher(line);
            if (m.find()) {
                methods++;
                String name = m.group(1), params = m.group(2).strip();
                int paramCount = params.isEmpty() ? 0 : params.split(",").length;
                if (paramCount > t.maxParams())
                    out.add(new Finding(file, "LongParameterList", Severity.MAJOR, name + " has " + paramCount + " parameters"));
                int length = methodLength(lines, i);
                if (length > t.maxMethodLines())
                    out.add(new Finding(file, "LongMethod", Severity.MAJOR, name + " is " + length + " lines"));
            }
        }
        if (fields > t.maxFields() || methods > t.maxMethods())
            out.add(new Finding(file, "GodClass", Severity.CRITICAL, fields + " fields, " + methods + " methods"));
        Matcher c = EMPTY_CATCH.matcher(source);
        while (c.find()) out.add(new Finding(file, "EmptyCatch", Severity.CRITICAL, "line " + lineOf(source, c.start())));
        Map<String, Integer> singletons = new LinkedHashMap<>();
        Matcher g = GET_INSTANCE.matcher(source);
        while (g.find()) singletons.merge(g.group(1), 1, Integer::sum);
        if (singletons.size() >= 3)
            out.add(new Finding(file, "Singletonitis", Severity.INFO, "uses " + singletons.size() + " singletons " + singletons.keySet()));
        return out;
    }

    private static int methodLength(String[] lines, int start) {
        int depth = 0;
        for (int i = start; i < lines.length; i++) {
            for (char ch : lines[i].toCharArray()) { if (ch == '{') depth++; else if (ch == '}') depth--; }
            if (depth == 0) return i - start + 1;
        }
        return lines.length - start;
    }
    private static int lineOf(String s, int index) { return (int) s.substring(0, index).chars().filter(ch -> ch == '\n').count() + 1; }

    record GateResult(boolean pass, List<Finding> newFindings, int baselined, List<String> fixedSinceBaseline) {}

    static GateResult gate(List<Finding> findings, Set<String> baseline, Severity failAt) {   // (3)!
        List<Finding> fresh = findings.stream().filter(f -> !baseline.contains(f.key()))
                .sorted(Comparator.comparing(Finding::severity).reversed().thenComparing(Finding::file)).toList();
        Set<String> nowKeys = new java.util.HashSet<>(findings.stream().map(Finding::key).toList());
        List<String> fixed = baseline.stream().filter(k -> !nowKeys.contains(k)).sorted().toList();     // (4)!
        boolean pass = fresh.stream().noneMatch(f -> f.severity().compareTo(failAt) >= 0);          // (5)!
        return new GateResult(pass, fresh, findings.size() - fresh.size(), fixed);
    }

    // ---------- Sample sources ----------
    static final String LEGACY = """
            class OrderManager {
                private int a;
                private int b;
                private int c;
                private int d;
                private int e;
                private int f;
                void place(String id, String phone, String address, long total, boolean express, int slot) {
                    Config.getInstance().load();
                    Clock.getInstance().now();
                    Metrics.getInstance().inc();
                }
                void sync() {
                    try { remote(); } catch (Exception e) { }
                }
            }
            """;
    static final String NEW_CODE = """
            class SlotService {
                private final Clock clock;
                boolean canBook(Slot slot) {
                    try { return slot.open(clock); } catch (IllegalStateException e) { }
                    return false;
                }
            }
            """;

    public static void main(String[] args) {
        Thresholds t = new Thresholds(20, 5, 5, 20);
        List<Finding> findings = new ArrayList<>();
        findings.addAll(scan("OrderManager.java", LEGACY, t));
        findings.addAll(scan("SlotService.java", NEW_CODE, t));
        System.out.println("all findings:");
        findings.forEach(f -> System.out.println("  " + f.severity() + " " + f.file() + " " + f.rule() + ": " + f.detail()));

        Set<String> baseline = Set.of(                                    // accepted legacy debt, tracked separately
                "OrderManager.java:GodClass:6 fields, 2 methods",
                "OrderManager.java:LongParameterList:place has 6 parameters",
                "OrderManager.java:EmptyCatch:line 14",
                "OrderManager.java:Singletonitis:uses 3 singletons [Config, Clock, Metrics]",
                "Invoice.java:LongMethod:render is 140 lines");
        GateResult result = gate(findings, baseline, Severity.MAJOR);
        System.out.println("baselined: " + result.baselined() + ", fixed since baseline: " + result.fixedSinceBaseline());
        System.out.println("new findings: " + result.newFindings().stream().map(f -> f.file() + " " + f.rule()).toList());
        System.out.println("build " + (result.pass() ? "PASSES" : "FAILS"));
    }
}
