import java.util.List;

/** Spaghetti: tangled control flow. Lava flow: dead code nobody dares remove. Both hide the actual rule. */
public class SpaghettiLavaFlowDemo {

    static final boolean LEGACY_FEE_V1 = false;   // turned off in 2023; nobody knows if it's safe to delete

    // ---- Before ----
    static long feeBefore(long subtotal, boolean member, boolean night, int km) {      // (1)!
        long fee = 0;
        if (LEGACY_FEE_V1) {                                                           // (2)!
            fee = 3_000;
            if (km > 5) fee += 1_000;
        } else {
            if (member) {
                if (subtotal >= 19_900) { fee = 0; }
                else { if (night) { fee = 2_000; } else { fee = 1_000; } }
            } else {
                if (subtotal >= 49_900) { if (night) { fee = 1_500; } else { fee = 0; } }
                else { if (night) { fee = 4_000; } else { if (km > 5) { fee = 3_500; } else { fee = 2_500; } } }
            }
        }
        return fee;
    }

    // ---- After: dead branch deleted (git remembers it), rule written as guard clauses in priority order. ----
    static long feeAfter(long subtotal, boolean member, boolean night, int km) {      // (3)!
        long freeAbove = member ? 19_900 : 49_900;
        if (subtotal >= freeAbove) return night && !member ? 1_500 : 0;
        if (member) return night ? 2_000 : 1_000;
        if (night) return 4_000;
        return km > 5 ? 3_500 : 2_500;
    }

    public static void main(String[] args) {
        int checked = 0, same = 0;
        for (long subtotal : List.of(10_000L, 19_900L, 30_000L, 49_900L, 80_000L))
            for (boolean member : new boolean[] {true, false})
                for (boolean night : new boolean[] {true, false})
                    for (int km : new int[] {2, 8}) {
                        checked++;
                        if (feeBefore(subtotal, member, night, km) == feeAfter(subtotal, member, night, km)) same++;
                    }
        System.out.println("combinations checked: " + checked + ", identical results: " + same);
        System.out.println("non-member, 300 rupees, night, 8 km: fee " + feeAfter(30_000, false, true, 8));
    }
}
