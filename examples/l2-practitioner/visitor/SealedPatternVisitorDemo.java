import java.util.List;

/** Modern Java "visitor": a sealed hierarchy plus exhaustive switch. No accept() methods, and the compiler checks coverage. */
public class SealedPatternVisitorDemo {

    sealed interface Item permits LooseProduce, Packaged, GiftCard { }                            // (1)!
    record LooseProduce(String name, int grams, long paisePerKg) implements Item { }
    record Packaged(String name, long paise, int gstPercent) implements Item { }
    record GiftCard(long valuePaise) implements Item { }

    static long gst(Item i) {                                                                       // (2)!
        return switch (i) {
            case LooseProduce l -> 0;
            case Packaged(String n, long paise, int pct) -> paise * pct / 100;
            case GiftCard g -> 0;                                                                   // tax applies when redeemed
        };
    }

    static String label(Item i) {
        return switch (i) {
            case LooseProduce(String name, int grams, long perKg) -> name + " " + grams + " g @ Rs " + perKg / 100 + "/kg";
            case Packaged p -> p.name();
            case GiftCard g -> "Gift card Rs " + g.valuePaise() / 100;
        };
    }
    // Add a record to `permits` and both switches stop compiling until handled.   // (3)!

    public static void main(String[] args) {
        List<Item> cart = List.of(new LooseProduce("Tomatoes", 750, 4_000), new Packaged("Chips", 2_000, 12), new GiftCard(50_000));
        cart.forEach(i -> System.out.println(label(i) + " -> GST paise " + gst(i)));
    }
}
