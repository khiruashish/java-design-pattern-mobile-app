import java.util.ArrayList;
import java.util.List;

/** Fluent builder for an immutable object, with defensive copies and validation in build(). */
public class FluentBuilderDemo {

    static final class DeliveryRequest {
        private final String orderId; private final String address; private final List<String> instructions;
        private final boolean contactless; private final int tipInCents;

        private DeliveryRequest(Builder b) {
            orderId = b.orderId; address = b.address; contactless = b.contactless; tipInCents = b.tipInCents;
            instructions = List.copyOf(b.instructions);                                   // (1)!
        }
        static Builder builder() { return new Builder(); }
        List<String> instructions() { return instructions; }
        public String toString() { return orderId + " to " + address + " contactless=" + contactless + " tip=" + tipInCents + " notes=" + instructions; }

        static final class Builder {
            private String orderId; private String address; private final List<String> instructions = new ArrayList<>();
            private boolean contactless; private int tipInCents;
            Builder orderId(String v) { orderId = v; return this; }                       // (2)!
            Builder address(String v) { address = v; return this; }
            Builder instruction(String v) { instructions.add(v); return this; }           // (3)!
            Builder contactless() { contactless = true; return this; }
            Builder tip(int cents) { tipInCents = cents; return this; }
            DeliveryRequest build() {                                                     // (4)!
                if (orderId == null || address == null) throw new IllegalStateException("orderId and address are required");
                if (tipInCents < 0 || tipInCents > 50_000) throw new IllegalStateException("tip must be 0..50000 cents");
                return new DeliveryRequest(this);
            }
        }
    }

    public static void main(String[] args) {
        DeliveryRequest.Builder b = DeliveryRequest.builder()
                .orderId("FC-610").address("12 Lake Road, Pune")
                .instruction("Ring once").instruction("Leave with guard").contactless().tip(2_000);
        DeliveryRequest first = b.build();
        b.instruction("Call on arrival");                                                 // builder reused after build
        System.out.println(first);
        System.out.println("built object unaffected by later builder calls: " + (first.instructions().size() == 2));
        try { first.instructions().add("hack"); } catch (UnsupportedOperationException e) { System.out.println("instructions are immutable"); }
        try { DeliveryRequest.builder().orderId("FC-611").build(); } catch (IllegalStateException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
