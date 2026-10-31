import java.util.List;

/** Minimal Abstract Factory: stores choose a packaging family; bag, cold pack and filler always match. */
public class PackagingFactoryDemo {

    interface Bag { String name(); }
    interface ColdPack { String name(); }
    interface Filler { String name(); }

    interface PackagingFactory { Bag bag(); ColdPack coldPack(); Filler filler(); }           // (1)!

    static final class StandardPackaging implements PackagingFactory {
        public Bag bag() { return () -> "plastic-free paper bag"; }
        public ColdPack coldPack() { return () -> "gel ice pack"; }
        public Filler filler() { return () -> "air pillows"; }
    }
    static final class EcoPackaging implements PackagingFactory {
        public Bag bag() { return () -> "reusable jute bag"; }
        public ColdPack coldPack() { return () -> "water-based ice pack"; }
        public Filler filler() { return () -> "shredded cardboard"; }
    }

    static List<String> pack(PackagingFactory f, boolean hasFrozen) {                     // (2)!
        return hasFrozen ? List.of(f.bag().name(), f.coldPack().name(), f.filler().name()) : List.of(f.bag().name(), f.filler().name());
    }

    public static void main(String[] args) {
        System.out.println("standard: " + pack(new StandardPackaging(), true));
        System.out.println("eco:      " + pack(new EcoPackaging(), true));
        System.out.println("eco, no frozen: " + pack(new EcoPackaging(), false));
    }
}
