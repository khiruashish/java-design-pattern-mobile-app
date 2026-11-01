import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FreshCart's Delivery context integrates ParcelGo through an anti-corruption layer: ParcelGo's status codes,
 * timestamps and failure reasons are translated into FreshCart's own delivery lifecycle, and anything unexpected
 * is quarantined instead of leaking in.
 */
public class PartnerShipmentAclDemo {

    // ParcelGo's webhook payload (their language)
    record ParcelGoWebhook(String awb, String clientRef, String statusCode, long epochSeconds, String reasonCode) {}

    // FreshCart's Delivery context (our language)
    enum DeliveryStatus { ASSIGNED, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED_ATTEMPT }
    record DeliveryUpdate(String orderId, DeliveryStatus status, String customerMessage) {}

    static final class ParcelGoAcl {
        private static final Map<String, DeliveryStatus> STATUS = Map.of(                              // (1)!
                "MAN", DeliveryStatus.ASSIGNED, "PKD", DeliveryStatus.PICKED_UP, "OFD", DeliveryStatus.OUT_FOR_DELIVERY,
                "DLV", DeliveryStatus.DELIVERED, "UDL", DeliveryStatus.FAILED_ATTEMPT);
        private static final Map<String, String> REASONS = Map.of(
                "CNA", "We couldn't reach you; we'll try again tomorrow",
                "ADI", "The address seemed incomplete; please check it in the app");
        final List<ParcelGoWebhook> quarantine = new ArrayList<>();

        java.util.Optional<DeliveryUpdate> translate(ParcelGoWebhook w) {
            DeliveryStatus status = STATUS.get(w.statusCode());
            if (status == null || w.clientRef() == null || !w.clientRef().startsWith("FC-")) {             // (2)!
                quarantine.add(w);
                return java.util.Optional.empty();
            }
            String message = switch (status) {                                                              // (3)!
                case FAILED_ATTEMPT -> REASONS.getOrDefault(w.reasonCode(), "Delivery attempt failed; we'll contact you");
                case DELIVERED -> "Delivered. Enjoy!";
                case OUT_FOR_DELIVERY -> "On the way";
                default -> "Being prepared";
            };
            return java.util.Optional.of(new DeliveryUpdate(w.clientRef(), status, message));
        }
    }

    public static void main(String[] args) {
        ParcelGoAcl acl = new ParcelGoAcl();
        List<ParcelGoWebhook> webhooks = List.of(
                new ParcelGoWebhook("PG123", "FC-71", "OFD", 1_793_800_000L, null),
                new ParcelGoWebhook("PG123", "FC-71", "UDL", 1_793_801_800L, "CNA"),
                new ParcelGoWebhook("PG124", "FC-72", "RTO", 1_793_802_000L, null),
                new ParcelGoWebhook("PG125", null, "DLV", 1_793_803_000L, null),
                new ParcelGoWebhook("PG123", "FC-71", "DLV", 1_793_890_000L, null));
        for (ParcelGoWebhook w : webhooks) acl.translate(w).ifPresent(u -> System.out.println(u));
        System.out.println("quarantined for review: " + acl.quarantine.stream().map(w -> w.awb() + "/" + w.statusCode()).toList());
    }
}
