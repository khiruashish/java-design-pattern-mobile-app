import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * DairyDay's contexts (Subscriptions, Billing, Delivery) integrate through a published language: versioned event
 * messages with documented fields. Old messages are upcast to the current version; each consumer declares the
 * fields it relies on, and the producer's CI checks every schema change against those consumer contracts.
 */
public class ContextIntegrationDemo {

    // ---------- Published language: schema per version ----------
    record Schema(String type, int version, Set<String> required, Set<String> optional) {}

    static final Map<Integer, Schema> SUBSCRIPTION_STARTED = Map.of(                                    // (1)!
            1, new Schema("SubscriptionStarted", 1, Set.of("subscriptionId", "customerId", "litres"), Set.of()),
            2, new Schema("SubscriptionStarted", 2, Set.of("subscriptionId", "customerId", "litresPerDay", "pricePerLitrePaise"), Set.of("deliverySlot")));

    /** Upcaster: old messages are converted to the current version before any consumer sees them. */
    static Map<String, Object> upcast(Map<String, Object> msg) {                                        // (2)!
        Map<String, Object> m = new LinkedHashMap<>(msg);
        if ((int) m.get("version") == 1) {
            m.put("litresPerDay", m.remove("litres"));
            m.put("pricePerLitrePaise", 6_800L);           // v1 was only sold at the launch price
            m.put("version", 2);
        }
        return m;
    }

    // ---------- Consumer-driven contracts ----------
    record ConsumerContract(String consumer, Set<String> fieldsUsed) {}                                 // (3)!

    static List<String> checkCompatibility(Schema proposed, List<ConsumerContract> contracts) {
        List<String> broken = new ArrayList<>();
        for (ConsumerContract c : contracts)
            for (String f : new TreeSet<>(c.fieldsUsed()))
                if (!proposed.required().contains(f) && !proposed.optional().contains(f))
                    broken.add(c.consumer() + " needs '" + f + "'");
        return broken;
    }

    // ---------- Consumers, each with its own model ----------
    record BillingAccount(String customerId, long monthlyPaise) {}
    record RouteStop(String subscriptionId, String slot) {}

    static BillingAccount billing(Map<String, Object> m) {                                              // (4)!
        return new BillingAccount((String) m.get("customerId"), ((Number) m.get("litresPerDay")).longValue() * ((Number) m.get("pricePerLitrePaise")).longValue() * 30);
    }
    static RouteStop delivery(Map<String, Object> m) {
        return new RouteStop((String) m.get("subscriptionId"), (String) m.getOrDefault("deliverySlot", "06:00-07:00"));
    }

    // ---------- Anti-corruption layer for the PayLink mandate API (Billing's upstream) ----------
    record PayLinkMandateResponse(String mandate_ref, String state, String umrn) {}
    enum MandateStatus { ACTIVE, PENDING, REJECTED }
    static MandateStatus toMandateStatus(PayLinkMandateResponse r) {                                     // (5)!
        return switch (r.state()) {
            case "ACTIVE", "active" -> MandateStatus.ACTIVE;
            case "INITIATED", "PENDING_BANK_APPROVAL" -> MandateStatus.PENDING;
            default -> MandateStatus.REJECTED;
        };
    }

    public static void main(String[] args) {
        List<ConsumerContract> contracts = List.of(
                new ConsumerContract("billing", Set.of("customerId", "litresPerDay", "pricePerLitrePaise")),
                new ConsumerContract("delivery", Set.of("subscriptionId", "deliverySlot")));

        Map<String, Object> oldMsg = new LinkedHashMap<>(Map.of("type", "SubscriptionStarted", "version", 1, "subscriptionId", "SUB-1", "customerId", "C-9", "litres", 1));
        Map<String, Object> newMsg = new LinkedHashMap<>(Map.of("type", "SubscriptionStarted", "version", 2, "subscriptionId", "SUB-2", "customerId", "C-17",
                "litresPerDay", 2, "pricePerLitrePaise", 6_500L, "deliverySlot", "07:00-08:00"));
        for (Map<String, Object> m : List.of(oldMsg, newMsg)) {
            Map<String, Object> current = upcast(m);
            System.out.println("v" + m.get("version") + " -> billing " + billing(current) + ", delivery " + delivery(current));
        }

        System.out.println("v2 schema vs contracts: " + checkCompatibility(SUBSCRIPTION_STARTED.get(2), contracts));
        Schema proposedV3 = new Schema("SubscriptionStarted", 3, Set.of("subscriptionId", "customerId", "litresPerDay", "unitPrice"), Set.of("deliverySlot"));
        System.out.println("proposed v3 (renames price) vs contracts: " + checkCompatibility(proposedV3, contracts));

        for (var r : List.of(new PayLinkMandateResponse("m1", "active", "U1"), new PayLinkMandateResponse("m2", "PENDING_BANK_APPROVAL", null),
                new PayLinkMandateResponse("m3", "EXPIRED_BY_NPCI", null)))
            System.out.println("PayLink " + r.state() + " -> " + toMandateStatus(r));
    }
}
