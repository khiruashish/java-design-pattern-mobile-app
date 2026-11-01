/** Factory Method: ONE product, chosen by a subclass. Abstract Factory: a FAMILY of products, chosen by picking a factory object. */
public class AbstractFactoryVsFactoryMethodDemo {

    interface Bag { String describe(); }
    interface Box { String describe(); }

    // ===================== Factory Method: a subclass decides one product =====================
    abstract static class Packer {
        abstract Bag bag();                                                                // (1)!
        String pack(String item) { return item + " in " + bag().describe(); }
    }
    static final class EcoPacker extends Packer { Bag bag() { return () -> "paper bag"; } }

    // ===================== Abstract Factory: an object creates related products together =====================
    interface PackagingFactory { Bag bag(); Box box(); }                                    // (2)!
    static final class EcoPackaging implements PackagingFactory {
        public Bag bag() { return () -> "paper bag"; }
        public Box box() { return () -> "recycled carton"; }
    }
    static final class PremiumPackaging implements PackagingFactory {
        public Bag bag() { return () -> "jute bag"; }
        public Box box() { return () -> "branded gift box"; }
    }

    static String packOrder(PackagingFactory f, String loose, String fragile) {          // (3)!
        return loose + " in " + f.bag().describe() + ", " + fragile + " in " + f.box().describe();
    }

    public static void main(String[] args) {
        System.out.println("factory method:   " + new EcoPacker().pack("bread"));
        System.out.println("abstract factory: " + packOrder(new EcoPackaging(), "bread", "eggs"));
        System.out.println("abstract factory: " + packOrder(new PremiumPackaging(), "bread", "eggs"));
    }
}
