/** Step builder: interfaces force required fields in order at COMPILE time; optional fields come at the end. */
public class StepBuilderDemo {

    record Shipment(String orderId, String fromStore, String toPincode, String courier, boolean fragile) { }

    interface OrderStep { FromStep orderId(String orderId); }                          // (1)!
    interface FromStep { ToStep from(String storeId); }
    interface ToStep { OptionalStep to(String pincode); }
    interface OptionalStep {                                                              // (2)!
        OptionalStep courier(String courier);
        OptionalStep fragile();
        Shipment build();
    }

    static OrderStep shipment() { return new Steps(); }

    private static final class Steps implements OrderStep, FromStep, ToStep, OptionalStep {   // (3)!
        private String orderId, store, pincode, courier = "in-house"; private boolean fragile;
        public FromStep orderId(String v) { orderId = v; return this; }
        public ToStep from(String v) { store = v; return this; }
        public OptionalStep to(String v) {
            if (!v.matches("\\d{6}")) throw new IllegalArgumentException("pincode must be 6 digits: " + v);
            pincode = v; return this;
        }
        public OptionalStep courier(String v) { courier = v; return this; }
        public OptionalStep fragile() { fragile = true; return this; }
        public Shipment build() { return new Shipment(orderId, store, pincode, courier, fragile); }
    }

    public static void main(String[] args) {
        Shipment s = shipment().orderId("FC-720").from("pune-01").to("411001").fragile().build();
        System.out.println(s);
        System.out.println(shipment().orderId("FC-721").from("pune-02").to("411045").courier("QuickDrop").build());
        // shipment().orderId("FC-722").build();   // does not compile: build() is not offered yet
        try { shipment().orderId("FC-723").from("pune-01").to("41100"); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
