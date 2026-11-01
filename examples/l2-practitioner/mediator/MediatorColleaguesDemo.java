import java.util.List;
import java.util.Map;

/** Mediator: widgets on the checkout screen talk to one coordinator instead of to each other. */
public class MediatorColleaguesDemo {

    interface CheckoutMediator { void changed(Object widget); }                                    // (1)!

    static final class AddressField {
        private final CheckoutMediator m; String pincode;
        AddressField(CheckoutMediator m) { this.m = m; }
        void enter(String pin) { pincode = pin; m.changed(this); }                                 // (2)!
    }
    static final class SlotPicker {
        private final CheckoutMediator m; List<String> options = List.of(); String chosen;
        SlotPicker(CheckoutMediator m) { this.m = m; }
        void choose(String slot) { chosen = slot; m.changed(this); }
    }
    static final class PaymentSelector {
        private final CheckoutMediator m; String method;
        PaymentSelector(CheckoutMediator m) { this.m = m; }
        void select(String method) { this.method = method; m.changed(this); }
    }
    static final class PlaceOrderButton { boolean enabled; }

    static final class CheckoutScreen implements CheckoutMediator {                                 // (3)!
        final AddressField address = new AddressField(this);
        final SlotPicker slots = new SlotPicker(this);
        final PaymentSelector payment = new PaymentSelector(this);
        final PlaceOrderButton button = new PlaceOrderButton();
        private final Map<String, List<String>> slotsByArea = Map.of("560034", List.of("10-11", "11-12"), "560001", List.of("18-19"));

        public void changed(Object widget) {
            if (widget == address) {
                slots.options = slotsByArea.getOrDefault(address.pincode, List.of());
                slots.chosen = null;                                                                  // a new address invalidates the slot
                payment.method = slots.options.isEmpty() ? null : payment.method;
            }
            button.enabled = slots.chosen != null && payment.method != null;                        // (4)!
            System.out.println("  after " + widget.getClass().getSimpleName() + ": slots=" + slots.options + " chosen=" + slots.chosen + " pay=" + payment.method + " button=" + (button.enabled ? "ON" : "off"));
        }
    }

    public static void main(String[] args) {
        CheckoutScreen s = new CheckoutScreen();
        s.address.enter("560034");
        s.slots.choose("10-11");
        s.payment.select("UPI");
        s.address.enter("560001");
    }
}
