import java.util.ArrayList;
import java.util.List;

/** Why Cloneable is avoided: shallow by default, bypasses constructors, clashes with final fields, checked exception. */
public class CloneableDemo {

    static class Promotion implements Cloneable {                                         // (1)!
        private static int created;
        final String id;
        List<String> skus;
        Promotion(String id, List<String> skus) { this.id = id; this.skus = skus; created++; }

        @Override public Promotion clone() {                                              // (2)!
            try { return (Promotion) super.clone(); }
            catch (CloneNotSupportedException e) { throw new AssertionError(e); }        // can't happen, but must be handled
        }
    }

    /** The alternative: a copy constructor that is explicit about what it copies. */
    static final class PromotionV2 {
        final String id; final List<String> skus;
        PromotionV2(String id, List<String> skus) { this.id = id; this.skus = List.copyOf(skus); }
        PromotionV2(PromotionV2 other, String newId) { this(newId, other.skus); }        // (3)!
        public String toString() { return id + skus; }
    }

    public static void main(String[] args) {
        Promotion diwali = new Promotion("DIWALI", new ArrayList<>(List.of("SWEETS", "DRY-FRUITS")));
        Promotion copy = diwali.clone();
        copy.skus.add("CANDLES");
        System.out.println("clone shares the list: original now " + diwali.skus);
        System.out.println("clone kept the same final id: " + copy.id + " (can't be changed after super.clone())");
        System.out.println("constructor calls: " + Promotion.created + " for 2 objects (clone skipped the constructor)");

        PromotionV2 eid = new PromotionV2("EID", List.of("DATES", "VERMICELLI"));
        PromotionV2 eidDubai = new PromotionV2(eid, "EID-DXB");
        System.out.println("copy constructor: " + eid + " -> " + eidDubai);
    }
}
