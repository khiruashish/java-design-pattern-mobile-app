/** Replace overloaded constructors whose meaning depends on argument order with named creation methods. */
public class CreationMethodsDemo {

    // ---- Before: which constructor makes a "percent off" coupon? What does the boolean mean? ----
    static final class CouponBefore {                                              // (1)!
        final String code; final int percent; final long flatPaise; final boolean freeDelivery; final long minOrder;
        CouponBefore(String code, int percent) { this(code, percent, 0, false, 0); }
        CouponBefore(String code, long flatPaise, long minOrder) { this(code, 0, flatPaise, false, minOrder); }
        CouponBefore(String code, boolean freeDelivery) { this(code, 0, 0, freeDelivery, 0); }
        CouponBefore(String code, int percent, long flatPaise, boolean freeDelivery, long minOrder) {
            this.code = code; this.percent = percent; this.flatPaise = flatPaise; this.freeDelivery = freeDelivery; this.minOrder = minOrder;
        }
    }

    // ---- After: intention-revealing names, validation per kind, one private constructor. ----
    record Coupon(String code, int percent, long flatPaise, boolean freeDelivery, long minOrderPaise) {
        static Coupon percentOff(String code, int percent) {                       // (2)!
            if (percent < 1 || percent > 50) throw new IllegalArgumentException("percent must be 1..50");
            return new Coupon(code, percent, 0, false, 0);
        }
        static Coupon flatOff(String code, long rupees, long minOrderRupees) {
            if (rupees * 2 > minOrderRupees) throw new IllegalArgumentException("flat discount must be at most half the minimum order");
            return new Coupon(code, 0, rupees * 100, false, minOrderRupees * 100);
        }
        static Coupon freeDelivery(String code) { return new Coupon(code, 0, 0, true, 0); }

        long discountOn(long subtotal) {                                           // (3)!
            if (subtotal < minOrderPaise) return 0;
            return percent > 0 ? subtotal * percent / 100 : flatPaise;
        }
    }

    public static void main(String[] args) {
        CouponBefore confusing = new CouponBefore("SAVE", 100L, 500L);   // 100 paise flat? 100 percent? min order 5 rupees?
        System.out.println("before: flat=" + confusing.flatPaise + " paise, min=" + confusing.minOrder + " (units unclear)");

        Coupon fresh10 = Coupon.percentOff("FRESH10", 10);
        Coupon save100 = Coupon.flatOff("SAVE100", 100, 500);
        Coupon ship = Coupon.freeDelivery("SHIPFREE");
        for (Coupon c : new Coupon[] {fresh10, save100, ship})
            System.out.println(c.code() + ": discount on Rs 600 = Rs " + c.discountOn(60_000) / 100 + ", free delivery=" + c.freeDelivery());
        try {
            Coupon.percentOff("OOPS", 90);
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}
