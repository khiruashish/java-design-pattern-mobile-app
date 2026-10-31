/** QuickDrop: magic numbers and primitive obsession replaced by named constants and a small value type. */
public class MagicNumbersDemo {

    // ---- Before: what are 49900, 2500 and 0.18? Paise or rupees? ----
    static long chargeBefore(long amount) { return amount >= 49900 ? amount : amount + 2500 + (long) (2500 * 0.18); }

    // ---- After ----
    record Money(long paise) {                                                   // (1)!
        static Money rupees(long r) { return new Money(r * 100); }
        Money plus(Money o) { return new Money(paise + o.paise); }
        Money percent(int p) { return new Money(paise * p / 100); }
        boolean atLeast(Money o) { return paise >= o.paise; }
        @Override public String toString() { return "Rs " + paise / 100 + "." + String.format("%02d", paise % 100); }
    }
    static final Money FREE_DELIVERY_FROM = Money.rupees(499);                   // (2)!
    static final Money DELIVERY_FEE = Money.rupees(25);
    static final int GST_ON_FEES_PERCENT = 18;

    static Money charge(Money cart) {
        if (cart.atLeast(FREE_DELIVERY_FROM)) return cart;
        return cart.plus(DELIVERY_FEE).plus(DELIVERY_FEE.percent(GST_ON_FEES_PERCENT));
    }

    public static void main(String[] args) {
        System.out.println("before: " + chargeBefore(30000) + " and " + chargeBefore(50000));
        System.out.println("after:  " + charge(Money.rupees(300)) + " and " + charge(Money.rupees(500)));
    }
}
