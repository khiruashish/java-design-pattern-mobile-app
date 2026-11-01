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
