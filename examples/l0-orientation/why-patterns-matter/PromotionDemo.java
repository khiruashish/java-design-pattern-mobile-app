import java.util.List;

public class PromotionDemo {

    record Basket(long totalInCents, int items) { }

    // ---- Version 1: a switch that grows with every marketing idea ----
    static long discountWithSwitch(String promo, Basket basket) {             // (1)!
        return switch (promo) {
            case "NONE" -> 0;
            case "TEN_PERCENT" -> basket.totalInCents() / 10;
            case "FLAT_5_OVER_40" -> basket.totalInCents() >= 4_000 ? 500 : 0;
            default -> throw new IllegalArgumentException("Unknown promotion " + promo);
        };
    }

    // ---- Version 2: Strategy. New promotions are new objects, nothing else changes ----
    interface Promotion {                                                      // (2)!
        String code();
        long discountInCents(Basket basket);
    }

    record PercentOff(String code, int percent) implements Promotion {
        public long discountInCents(Basket b) { return b.totalInCents() * percent / 100; }
    }

    record FlatOffOver(String code, long offInCents, long thresholdInCents) implements Promotion {
        public long discountInCents(Basket b) { return b.totalInCents() >= thresholdInCents ? offInCents : 0; }
    }

    /** Added in a later sprint WITHOUT editing any existing promotion or the checkout. */
    record BuyManySaveMore(String code, int minItems, long offPerItemInCents) implements Promotion {   // (3)!
        public long discountInCents(Basket b) { return b.items() >= minItems ? b.items() * offPerItemInCents : 0; }
    }

    static String money(long cents) { return "$%d.%02d".formatted(cents / 100, cents % 100); }

    public static void main(String[] args) {
        Basket basket = new Basket(4_800, 12);
        System.out.println("switch   TEN_PERCENT    -> " + money(discountWithSwitch("TEN_PERCENT", basket)));
        System.out.println("switch   FLAT_5_OVER_40 -> " + money(discountWithSwitch("FLAT_5_OVER_40", basket)));

        List<Promotion> promotions = List.of(
                new PercentOff("TEN_PERCENT", 10),
                new FlatOffOver("FLAT_5_OVER_40", 500, 4_000),
                new BuyManySaveMore("BULK_10", 10, 25));
        for (Promotion p : promotions) {
            System.out.printf("strategy %-14s -> %s%n", p.code(), money(p.discountInCents(basket)));
        }
    }
}
