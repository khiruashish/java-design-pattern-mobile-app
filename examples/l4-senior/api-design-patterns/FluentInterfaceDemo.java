/**
 * Fluent interface: methods return an object to continue the chain, so code reads like a sentence. A staged
 * (step) builder makes the compiler enforce the required order: you can't build a query without a city.
 */
public class FluentInterfaceDemo {

    record SlotQuery(String city, String date, int minFreeSlots, boolean expressOnly) {}

    interface NeedsCity { NeedsDate in(String city); }                                         // (1)!
    interface NeedsDate { Options on(String date); }
    interface Options { Options withAtLeast(int freeSlots); Options expressOnly(); SlotQuery build(); }

    static NeedsCity slots() {
        return city -> date -> new Options() {                                                 // (2)!
            int min = 1; boolean express;
            public Options withAtLeast(int n) { min = n; return this; }
            public Options expressOnly() { express = true; return this; }
            public SlotQuery build() { return new SlotQuery(city, date, min, express); }
        };
    }

    public static void main(String[] args) {
        SlotQuery q = slots().in("BLR").on("2026-11-02").withAtLeast(5).expressOnly().build();   // (3)!
        System.out.println(q);
        System.out.println(slots().in("PUN").on("2026-11-03").build());
        // slots().on("2026-11-02").build();   does not compile: NeedsCity has no on(), so the city can't be forgotten
    }
}
