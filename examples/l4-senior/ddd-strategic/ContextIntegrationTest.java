import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContextIntegrationTest {

    static final List<ContextIntegrationDemo.ConsumerContract> CONTRACTS = List.of(
            new ContextIntegrationDemo.ConsumerContract("billing", Set.of("customerId", "litresPerDay", "pricePerLitrePaise")),
            new ContextIntegrationDemo.ConsumerContract("delivery", Set.of("subscriptionId", "deliverySlot")));

    @Test
    void currentSchemaSatisfiesEveryConsumer() {
        assertEquals(List.of(), ContextIntegrationDemo.checkCompatibility(ContextIntegrationDemo.SUBSCRIPTION_STARTED.get(2), CONTRACTS));
    }

    @Test
    void removingAFieldAConsumerUsesIsCaught() {
        var withoutSlot = new ContextIntegrationDemo.Schema("SubscriptionStarted", 3,
                Set.of("subscriptionId", "customerId", "litresPerDay", "pricePerLitrePaise"), Set.of());
        assertEquals(List.of("delivery needs 'deliverySlot'"), ContextIntegrationDemo.checkCompatibility(withoutSlot, CONTRACTS));
    }

    @Test
    void v1MessagesAreUpcastBeforeConsumersSeeThem() {
        Map<String, Object> v1 = new LinkedHashMap<>(Map.of("type", "SubscriptionStarted", "version", 1, "subscriptionId", "S", "customerId", "C", "litres", 3));
        var current = ContextIntegrationDemo.upcast(v1);
        assertEquals(2, current.get("version"));
        assertEquals(3, current.get("litresPerDay"));
        assertEquals(3 * 6_800L * 30, ContextIntegrationDemo.billing(current).monthlyPaise());
        assertEquals(1, v1.get("version"), "the original message is not modified");
    }

    @Test
    void unknownPayLinkStatesAreTreatedAsRejected() {
        assertEquals(ContextIntegrationDemo.MandateStatus.REJECTED,
                ContextIntegrationDemo.toMandateStatus(new ContextIntegrationDemo.PayLinkMandateResponse("m", "SOMETHING_NEW", null)));
    }

    @Test
    void aclQuarantinesWhatItCannotTranslate() {
        var acl = new PartnerShipmentAclDemo.ParcelGoAcl();
        assertTrue(acl.translate(new PartnerShipmentAclDemo.ParcelGoWebhook("A", "FC-1", "XYZ", 0, null)).isEmpty());
        assertTrue(acl.translate(new PartnerShipmentAclDemo.ParcelGoWebhook("B", "OTHER-1", "DLV", 0, null)).isEmpty());
        assertEquals(2, acl.quarantine.size());
        assertEquals(PartnerShipmentAclDemo.DeliveryStatus.DELIVERED,
                acl.translate(new PartnerShipmentAclDemo.ParcelGoWebhook("C", "FC-2", "DLV", 0, null)).orElseThrow().status());
    }

    @Test
    void contextMapImpactStopsAtAntiCorruptionLayers() {
        assertEquals(Set.of("Delivery"), ContextMappingDemo.impactOf("ParcelGo API"));
        assertTrue(ContextMappingDemo.impactOf("PayLink API").contains("Delivery"));
    }

    @Test
    void erpTranslationRemovesTaxAndSkipsDeletedItems() {
        var products = new AntiCorruptionLayerDemo.ErpProductTranslator().translate(List.of(
                new AntiCorruptionLayerDemo.ErpItemRow("000000000010045", "P", "KG", "0,2", "105,00", "V1", ""),
                new AntiCorruptionLayerDemo.ErpItemRow("000000000010046", "C", "G", "400", "10,00", "V1", "X")));
        assertEquals(1, products.size());
        assertEquals(10_000, products.get(0).pricePaiseExTax());
        assertEquals(200, products.get(0).grams());
    }
}
