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

    static Set<String> snapshot(Class<?>... types) {                                              // (1)!
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
        Bump required = !removed.isEmpty() || !breakingAdditions.isEmpty() ? Bump.MAJOR             // (2)!
                : !added.isEmpty() ? Bump.MINOR                                                    // (3)!
                : Bump.PATCH;
        return new Report(required, removed, added, breakingAdditions);
    }

    static Bump proposedBump(String from, String to) {
        int[] a = Arrays.stream(from.split("\\.")).mapToInt(Integer::parseInt).toArray();
        int[] b = Arrays.stream(to.split("\\.")).mapToInt(Integer::parseInt).toArray();
        return b[0] > a[0] ? Bump.MAJOR : b[1] > a[1] ? Bump.MINOR : Bump.PATCH;
    }

    static boolean gate(String from, String to, Report report) {
        return proposedBump(from, to).compareTo(report.required()) >= 0;                           // (4)!
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
            String get(String path);                                                               // (5)!
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
