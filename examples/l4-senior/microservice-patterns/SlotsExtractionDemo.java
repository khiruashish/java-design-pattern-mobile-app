import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Realistic: extracting delivery-slot calculation from the FreshCart monolith. The facade shadows traffic to the
 * new service, compares answers, keeps serving the monolith's answer, and only cuts over when mismatches stay low.
 */
public class SlotsExtractionDemo {

    static final Function<String, String> MONOLITH = pin -> pin.startsWith("560") ? "07:00-09:00" : "18:00-20:00";

    /** The new service has one bug: it doesn't know 5601xx PIN codes are served in the morning too. */
    static final Function<String, String> SLOTS_SERVICE_V1 = pin -> pin.startsWith("5600") ? "07:00-09:00" : "18:00-20:00";
    static final Function<String, String> SLOTS_SERVICE_V2 = pin -> pin.startsWith("560") ? "07:00-09:00" : "18:00-20:00";

    static final class ShadowingFacade {
        private Function<String, String> candidate; private boolean cutOver;
        int compared, mismatched; final List<String> mismatches = new ArrayList<>();
        ShadowingFacade(Function<String, String> candidate) { this.candidate = candidate; }
        void deploy(Function<String, String> next) { candidate = next; compared = 0; mismatched = 0; mismatches.clear(); }

        String slotFor(String pin) {
            if (cutOver) return candidate.apply(pin);
            String served = MONOLITH.apply(pin);
            String shadow = candidate.apply(pin);                                             // (1)!
            compared++;
            if (!served.equals(shadow)) { mismatched++; mismatches.add(pin + " monolith=" + served + " new=" + shadow); }
            return served;                                                                    // (2)!
        }

        String tryCutOver(double maxMismatchRate, int minSamples) {                          // (3)!
            double rate = compared == 0 ? 1 : (double) mismatched / compared;
            if (compared >= minSamples && rate <= maxMismatchRate) { cutOver = true; return "cut over (" + mismatched + "/" + compared + " mismatches)"; }
            return "stay on monolith (" + mismatched + "/" + compared + " mismatches)";
        }
    }

    public static void main(String[] args) {
        List<String> pins = List.of("560001", "560102", "411001", "560034", "560110", "110001");
        ShadowingFacade facade = new ShadowingFacade(SLOTS_SERVICE_V1);
        pins.forEach(facade::slotFor);
        System.out.println("v1: " + facade.tryCutOver(0.01, 5));
        facade.mismatches.forEach(m -> System.out.println("  " + m));
        facade.deploy(SLOTS_SERVICE_V2);
        pins.forEach(facade::slotFor);
        System.out.println("v2: " + facade.tryCutOver(0.01, 5));
        System.out.println("560102 now served by the new service: " + facade.slotFor("560102"));
    }
}
