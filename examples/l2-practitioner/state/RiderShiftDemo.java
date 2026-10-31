/** Minimal State: a rider's app behaves differently when off duty, available, or on a delivery. */
public class RiderShiftDemo {

    enum Shift {                                                                                  // (1)!
        OFF_DUTY { Shift tap(StringBuilder log) { log.append("started shift; "); return AVAILABLE; } },
        AVAILABLE { Shift tap(StringBuilder log) { log.append("accepted order; "); return ON_DELIVERY; } },
        ON_DELIVERY { Shift tap(StringBuilder log) { log.append("marked delivered; "); return AVAILABLE; } };

        abstract Shift tap(StringBuilder log);                                                   // (2)!
    }

    public static void main(String[] args) {
        Shift s = Shift.OFF_DUTY;
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 4; i++) s = s.tap(log);
        System.out.println(log + "now " + s);
    }
}
