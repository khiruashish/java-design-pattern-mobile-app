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
        Pattern item = Pattern.compile("^- \\[( |x)\\] (.+)$", Pattern.MULTILINE);                 // (1)!
        Matcher m = item.matcher(description);
        java.util.Set<String> checked = new java.util.TreeSet<>();
        while (m.find()) if (m.group(1).equals("x")) checked.add(m.group(2).strip());
        List<String> missing = REQUIRED.stream().filter(r -> !checked.contains(r)).toList();
        System.out.println(pr + ": " + (missing.isEmpty() ? "ready to merge" : "BLOCKED, unchecked " + missing)); // (2)!
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
