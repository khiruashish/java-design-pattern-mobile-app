import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** Realistic Facade: the checkout screen needs ONE "delivery quote"; behind it are serviceability, slots, fees and ETA. */
public class DeliveryQuoteFacadeDemo {

    // ---- subsystems owned by different teams ----
    static final class Serviceability { Optional<String> storeFor(String pincode) { return pincode.startsWith("411") ? Optional.of("pune-01") : Optional.empty(); } }
    static final class SlotService { List<LocalTime> openSlots(String store) { return List.of(LocalTime.of(12, 0), LocalTime.of(18, 0)); } }
    static final class FeeService { long fee(String store, long basketCents, LocalTime slot) { return basketCents >= 50_000 ? 0 : slot.getHour() >= 17 ? 3_900 : 2_900; } }
    static final class EtaService { int expressMinutes(String store) { return 25; } }

    // ---- what the app needs, in one shape ----
    record SlotOption(String label, long feeCents) { }
    sealed interface Quote permits Available, NotServiceable { }
    record Available(String store, int expressEtaMinutes, List<SlotOption> slots) implements Quote { }
    record NotServiceable(String pincode, String message) implements Quote { }

    static final class DeliveryQuoteFacade {
        private final Serviceability serviceability; private final SlotService slots; private final FeeService fees; private final EtaService eta;
        DeliveryQuoteFacade(Serviceability s, SlotService sl, FeeService f, EtaService e) { serviceability = s; slots = sl; fees = f; eta = e; }

        Quote quote(String pincode, long basketCents) {
            Optional<String> store = serviceability.storeFor(pincode);
            if (store.isEmpty()) return new NotServiceable(pincode, "We don't deliver to " + pincode + " yet");   // (1)!
            String s = store.get();
            List<SlotOption> options = slots.openSlots(s).stream()
                    .map(t -> new SlotOption(t + "-" + t.plusHours(1), fees.fee(s, basketCents, t))).toList();   // (2)!
            return new Available(s, eta.expressMinutes(s), options);
        }
    }

    public static void main(String[] args) {
        DeliveryQuoteFacade quotes = new DeliveryQuoteFacade(new Serviceability(), new SlotService(), new FeeService(), new EtaService());
        System.out.println(quotes.quote("411001", 32_000));
        System.out.println(quotes.quote("411001", 62_000));
        System.out.println(quotes.quote("560001", 32_000));
    }
}
