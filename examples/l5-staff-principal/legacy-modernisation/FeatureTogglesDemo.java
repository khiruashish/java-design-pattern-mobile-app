import java.time.LocalDate;
import java.util.List;

/**
 * Feature toggles: release toggles hide unfinished work, ops toggles are kill switches, experiment toggles split
 * traffic, permission toggles target groups. Percentage rollouts use a stable hash so a customer doesn't flip
 * between old and new. Every toggle has an owner and an expiry, because old toggles become debt.
 */
public class FeatureTogglesDemo {

    enum Kind { RELEASE, OPS, EXPERIMENT, PERMISSION }
    record Toggle(String name, Kind kind, int rolloutPercent, boolean killed, String owner, LocalDate expires) {
        boolean enabledFor(String customerId) {
            if (killed) return false;                                                                    // (1)!
            int bucket = Math.floorMod((name + ":" + customerId).hashCode(), 100);                      // (2)!
            return bucket < rolloutPercent;
        }
    }

    public static void main(String[] args) {
        Toggle newCheckout = new Toggle("new-checkout", Kind.RELEASE, 20, false, "checkout-team", LocalDate.of(2027, 1, 31));
        List<String> customers = List.of("C-1", "C-2", "C-3", "C-4", "C-5", "C-6", "C-7", "C-8", "C-9", "C-10");
        int on = 0;
        for (int i = 0; i < 10_000; i++) if (newCheckout.enabledFor("C-" + i)) on++;
        System.out.println("new-checkout at 20%: " + on + " of 10000 customers");
        System.out.println("same customers on every request: " + customers.stream().allMatch(c -> newCheckout.enabledFor(c) == newCheckout.enabledFor(c)));
        Toggle killed = new Toggle("new-checkout", Kind.RELEASE, 20, true, "checkout-team", LocalDate.of(2027, 1, 31));
        System.out.println("after kill switch: " + customers.stream().filter(killed::enabledFor).count() + " customers on new checkout");

        LocalDate today = LocalDate.of(2026, 11, 1);
        List<Toggle> all = List.of(newCheckout,
                new Toggle("paylink-fallback", Kind.OPS, 100, false, "payments-team", LocalDate.of(2099, 12, 31)),
                new Toggle("diwali-banner", Kind.EXPERIMENT, 50, false, "growth-team", LocalDate.of(2025, 11, 15)));
        all.stream().filter(t -> t.expires().isBefore(today))                                               // (3)!
                .forEach(t -> System.out.println("expired toggle: " + t.name() + " (" + t.kind() + ", owner " + t.owner() + ") since " + t.expires()));
    }
}
