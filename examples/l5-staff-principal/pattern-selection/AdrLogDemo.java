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

    static Adr parse(String file, String text, List<String> problems) {                               // (1)!
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
            if (a.supersededBy() != null && !indexOut.containsKey(a.supersededBy()))                  // (2)!
                problems.add("ADR-" + id(a.number()) + ": superseded by missing ADR-" + id(a.supersededBy()));
            if (a.status().equals("Accepted") && a.reviewBy() != null && a.reviewBy().isBefore(today))   // (3)!
                problems.add("ADR-" + id(a.number()) + ": review was due " + a.reviewBy());
        }
        Pattern cite = Pattern.compile("ADR-(\\d{4})");
        sourceFiles.forEach((file, code) -> {                                                         // (4)!
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
        index.values().forEach(a -> System.out.println("  ADR-" + id(a.number()) + " " + a.title() + " [" + a.status() + "]"));   // (5)!
        System.out.println(problems.size() + " problems:");
        problems.forEach(p -> System.out.println("  " + p));
    }
}
