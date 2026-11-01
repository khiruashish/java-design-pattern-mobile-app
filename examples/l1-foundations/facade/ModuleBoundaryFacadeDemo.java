import java.time.LocalTime;
import java.util.List;

/**
 * A facade at a module boundary: the "delivery" module exposes ONE public interface and plain DTOs.
 * Everything else is internal (in a real project: package-private classes or a non-exported JPMS package).
 */
public class ModuleBoundaryFacadeDemo {

    // ===================== delivery module: public API =====================
    public record SlotDto(String label, long feeCents) { }                                // (1)!
    public interface DeliveryModule {                                                     // (2)!
        List<SlotDto> availableSlots(String pincode);
        String book(String orderId, String slotLabel);
        static DeliveryModule create() { return new DeliveryModuleFacade(new SlotPlanner(), new RiderPool(), new FeeRules()); }
    }

    // ===================== delivery module: internals, invisible to other modules =====================
    private record Slot(LocalTime start, int capacity, int booked) { boolean open() { return booked < capacity; } }   // (3)!
    private static final class SlotPlanner {
        List<Slot> slotsFor(String pincode) { return List.of(new Slot(LocalTime.of(10, 0), 20, 20), new Slot(LocalTime.of(12, 0), 20, 5), new Slot(LocalTime.of(18, 0), 20, 18)); }
    }
    private static final class RiderPool { String assign(String orderId) { return "rider-" + Math.floorMod(orderId.hashCode(), 50); } }
    private static final class FeeRules { long fee(Slot s) { return s.start().getHour() >= 17 ? 3_900 : 2_900; } }

    private static final class DeliveryModuleFacade implements DeliveryModule {
        private final SlotPlanner planner; private final RiderPool riders; private final FeeRules fees;
        DeliveryModuleFacade(SlotPlanner p, RiderPool r, FeeRules f) { planner = p; riders = r; fees = f; }
        public List<SlotDto> availableSlots(String pincode) {
            return planner.slotsFor(pincode).stream().filter(Slot::open)
                    .map(s -> new SlotDto(s.start() + "-" + s.start().plusHours(1), fees.fee(s))).toList();   // (4)!
        }
        public String book(String orderId, String slotLabel) { return orderId + " booked " + slotLabel + " with " + riders.assign(orderId); }
    }

    // ===================== checkout module: only uses the public API =====================
    public static void main(String[] args) {
        DeliveryModule delivery = DeliveryModule.create();
        List<SlotDto> slots = delivery.availableSlots("411001");
        System.out.println("slots: " + slots);
        System.out.println(delivery.book("FC-2201", slots.get(0).label()));
    }
}
