import java.util.List;
import java.util.Map;

/** The god facade: one class fronting everything. The fix: one facade per use-case area. */
public class GodFacadeDemo {

    // Imagine each name is a subsystem class
    static final List<String> GOD_FACADE_DEPENDENCIES = List.of(                           // (1)!
            "Inventory", "Pricing", "Payments", "Notifications", "Riders", "Tracking", "Loyalty",
            "Coupons", "Accounts", "Addresses", "Reports", "Refunds", "Support", "Search");

    static final Map<String, List<String>> GOD_FACADE_METHODS = Map.of(
            "FreshCartFacade", List.of("placeOrder", "cancelOrder", "trackOrder", "assignRider", "applyCoupon", "updateAddress",
                    "resetPassword", "dailyReport", "refund", "openTicket", "search", "loyaltyBalance"));

    // Split by use-case area: each facade has few dependencies and a clear owner
    static final Map<String, List<String>> SPLIT = Map.of(                                 // (2)!
            "CheckoutFacade", List.of("Inventory", "Pricing", "Coupons", "Payments", "Notifications"),
            "DeliveryFacade", List.of("Riders", "Tracking", "Notifications"),
            "AccountFacade", List.of("Accounts", "Addresses", "Loyalty"),
            "SupportFacade", List.of("Refunds", "Support", "Payments"));

    public static void main(String[] args) {
        System.out.println("god facade: " + GOD_FACADE_DEPENDENCIES.size() + " dependencies, "
                + GOD_FACADE_METHODS.get("FreshCartFacade").size() + " unrelated methods, owned by everyone (so by no one)");
        SPLIT.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue().size() + " dependencies " + e.getValue()));
        long reused = SPLIT.values().stream().flatMap(List::stream).filter(d -> d.equals("Notifications") || d.equals("Payments")).count();
        System.out.println("shared subsystems used by more than one facade: Notifications, Payments (" + reused + " usages)");   // (3)!
    }
}
