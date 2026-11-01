import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strangler fig in practice: migration is tracked per capability, by traffic share and by who owns the data.
 * A capability is only "strangled" when the new service owns writes and the legacy code path is deleted.
 */
public class StranglerInPracticeDemo {

    enum DataOwner { LEGACY, NEW_SERVICE }
    record Capability(String name, int newTrafficPercent, DataOwner writes, boolean legacyCodeDeleted, int dailyRequests) {
        String stage() {                                                                                  // (1)!
            if (legacyCodeDeleted) return "done";
            if (writes == DataOwner.NEW_SERVICE) return "new service owns data; delete legacy path";
            if (newTrafficPercent == 100) return "reads migrated; legacy still owns writes (CDC syncs to new)";
            if (newTrafficPercent > 0) return "canary at " + newTrafficPercent + "%";
            return "not started";
        }
    }

    public static void main(String[] args) {
        Map<String, Capability> plan = new LinkedHashMap<>();
        plan.put("catalog browse", new Capability("catalog browse", 100, DataOwner.NEW_SERVICE, true, 900_000));
        plan.put("delivery slots", new Capability("delivery slots", 100, DataOwner.NEW_SERVICE, false, 300_000));
        plan.put("order history", new Capability("order history", 100, DataOwner.LEGACY, false, 120_000));
        plan.put("checkout", new Capability("checkout", 5, DataOwner.LEGACY, false, 80_000));
        plan.put("invoicing", new Capability("invoicing", 0, DataOwner.LEGACY, false, 20_000));
        plan.values().forEach(c -> System.out.printf("%-15s %s%n", c.name(), c.stage()));

        long total = plan.values().stream().mapToLong(Capability::dailyRequests).sum();
        long onNew = plan.values().stream().mapToLong(c -> c.dailyRequests() * c.newTrafficPercent() / 100).sum();
        long deleted = plan.values().stream().filter(Capability::legacyCodeDeleted).count();
        System.out.printf("traffic on new services: %d%%%n", onNew * 100 / total);                        // (2)!
        System.out.println("capabilities fully strangled: " + deleted + " of " + plan.size());            // (3)!
    }
}
