import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decomposition by business capability vs by entity: count how many network hops each use case would need.
 * Fewer cross-service hops per use case means less latency, fewer partial failures and more team autonomy.
 */
public class DecompositionDemo {

    record UseCase(String name, List<String> steps) {}                                      // (1)!

    static final List<UseCase> USE_CASES = List.of(
            new UseCase("place order", List.of("cart", "price", "reserve-stock", "order", "payment")),
            new UseCase("track order", List.of("order", "delivery-status", "rider-location")),
            new UseCase("restock shelf", List.of("reserve-stock", "supplier-po")),
            new UseCase("refund", List.of("order", "payment", "refund-policy")));

    /** Maps each step to the service that owns it, for one candidate decomposition. */
    static int crossServiceHops(Map<String, String> owner) {                                // (2)!
        int hops = 0;
        for (UseCase uc : USE_CASES)
            for (int i = 1; i < uc.steps().size(); i++)
                if (!owner.get(uc.steps().get(i)).equals(owner.get(uc.steps().get(i - 1)))) hops++;
        return hops;
    }

    static long services(Map<String, String> owner) { return owner.values().stream().distinct().count(); }

    public static void main(String[] args) {
        Map<String, String> byEntity = new LinkedHashMap<>();                                // (3)!
        byEntity.put("cart", "CartService"); byEntity.put("price", "PriceService"); byEntity.put("reserve-stock", "ProductService");
        byEntity.put("order", "OrderService"); byEntity.put("payment", "PaymentService"); byEntity.put("delivery-status", "DeliveryService");
        byEntity.put("rider-location", "RiderService"); byEntity.put("supplier-po", "SupplierService"); byEntity.put("refund-policy", "RefundService");

        Map<String, String> byCapability = new LinkedHashMap<>();
        byCapability.put("cart", "Ordering"); byCapability.put("price", "Ordering"); byCapability.put("order", "Ordering");
        byCapability.put("reserve-stock", "Inventory"); byCapability.put("supplier-po", "Inventory");
        byCapability.put("payment", "Payments"); byCapability.put("refund-policy", "Payments");
        byCapability.put("delivery-status", "Fulfilment"); byCapability.put("rider-location", "Fulfilment");

        System.out.println("by entity:     " + services(byEntity) + " services, " + crossServiceHops(byEntity) + " cross-service hops across " + USE_CASES.size() + " use cases");
        System.out.println("by capability: " + services(byCapability) + " services, " + crossServiceHops(byCapability) + " cross-service hops");
        for (UseCase uc : USE_CASES)
            System.out.println("  " + uc.name() + ": " + uc.steps().stream().map(byCapability::get).distinct().toList());
    }
}
