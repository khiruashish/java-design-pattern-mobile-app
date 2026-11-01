import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Backward compatibility rules: compare an old and a proposed response schema and classify each change. Adding
 * optional fields is safe; removing, renaming, retyping or making fields required breaks existing clients.
 */
public class BackwardCompatibilityDemo {

    record Field(String type, boolean required) {}

    static List<String> breakingChanges(Map<String, Field> before, Map<String, Field> after, Map<String, List<String>> enumsBefore, Map<String, List<String>> enumsAfter) {
        List<String> problems = new ArrayList<>();
        before.forEach((name, f) -> {
            Field g = after.get(name);
            if (g == null) problems.add("removed '" + name + "'");                                        // (1)!
            else if (!g.type().equals(f.type())) problems.add("changed type of '" + name + "' " + f.type() + " -> " + g.type());
        });
        after.forEach((name, g) -> {
            if (!before.containsKey(name) && g.required()) problems.add("new required field '" + name + "' (old clients never send it)");   // (2)!
        });
        enumsBefore.forEach((name, values) -> {
            List<String> added = enumsAfter.getOrDefault(name, List.of()).stream().filter(v -> !values.contains(v)).toList();
            if (!added.isEmpty()) problems.add("new enum values " + added + " in '" + name + "' (breaks strict clients)");   // (3)!
        });
        return problems;
    }

    public static void main(String[] args) {
        Map<String, Field> v1 = new TreeMap<>(Map.of("orderId", new Field("string", true), "total", new Field("number", true),
                "status", new Field("string", true), "eta", new Field("string", false)));
        Map<String, Field> safe = new TreeMap<>(v1);
        safe.put("riderName", new Field("string", false));
        Map<String, Field> risky = new TreeMap<>(v1);
        risky.remove("eta");
        risky.put("total", new Field("object", true));
        risky.put("slotId", new Field("string", true));
        Map<String, List<String>> statuses = Map.of("status", List.of("PLACED", "PACKED", "DELIVERED"));
        Map<String, List<String>> statusesPlus = Map.of("status", List.of("PLACED", "PACKED", "OUT_FOR_DELIVERY", "DELIVERED"));

        System.out.println("add optional riderName: " + breakingChanges(v1, safe, statuses, statuses));
        System.out.println("proposed v1.1:");
        breakingChanges(v1, risky, statuses, statusesPlus).forEach(p -> System.out.println("  BREAKING " + p));
    }
}
