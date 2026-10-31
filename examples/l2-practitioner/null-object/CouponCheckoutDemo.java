import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Realistic: FreshCart checkout with an optional promo code. Three "absent or bad" situations each get a special case:
 * no code typed (NoCoupon), unknown code (InvalidCoupon), expired code (ExpiredCoupon). Checkout code has no null checks.
 */
public class CouponCheckoutDemo {

    sealed interface Coupon permits PercentCoupon, NoCoupon, InvalidCoupon, ExpiredCoupon {
        long discount(long subtotalPaise);
        String message();
    }
    record PercentCoupon(String code, int percent, long capPaise) implements Coupon {
        public long discount(long s) { return Math.min(s * percent / 100, capPaise); }
        public String message() { return code + " applied: " + percent + "% off (max Rs " + capPaise / 100 + ")"; }
    }
    enum NoCoupon implements Coupon {                                                                 // (1)!
        INSTANCE;
        public long discount(long s) { return 0; }
        public String message() { return ""; }
    }
    record InvalidCoupon(String code) implements Coupon {                                            // (2)!
        public long discount(long s) { return 0; }
        public String message() { return "code " + code + " isn't valid"; }
    }
    record ExpiredCoupon(String code, LocalDate expiredOn) implements Coupon {
        public long discount(long s) { return 0; }
        public String message() { return "code " + code + " expired on " + expiredOn; }
    }

    record StoredCoupon(int percent, long capPaise, LocalDate validUntil) { }
    static final Map<String, StoredCoupon> COUPONS = new LinkedHashMap<>();
    static {
        COUPONS.put("FRESH10", new StoredCoupon(10, 10_000, LocalDate.of(2026, 12, 31)));
        COUPONS.put("DIWALI25", new StoredCoupon(25, 15_000, LocalDate.of(2026, 10, 25)));
    }

    static Coupon resolve(String typed, LocalDate today) {                                           // (3)!
        if (typed == null || typed.isBlank()) return NoCoupon.INSTANCE;
        String code = typed.trim().toUpperCase();
        StoredCoupon c = COUPONS.get(code);
        if (c == null) return new InvalidCoupon(code);
        if (today.isAfter(c.validUntil())) return new ExpiredCoupon(code, c.validUntil());
        return new PercentCoupon(code, c.percent(), c.capPaise());
    }

    static String checkout(long subtotalPaise, String typedCode, LocalDate today) {
        Coupon coupon = resolve(typedCode, today);
        long total = subtotalPaise - coupon.discount(subtotalPaise);
        String msg = coupon.message().isEmpty() ? "" : "  [" + coupon.message() + "]";
        return String.format("Rs %.2f -> Rs %.2f%s", subtotalPaise / 100.0, total / 100.0, msg);
    }

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 31);
        System.out.println("no code:   " + checkout(64_900, null, today));
        System.out.println("FRESH10:   " + checkout(64_900, " fresh10 ", today));
        System.out.println("big cart:  " + checkout(250_000, "FRESH10", today));
        System.out.println("typo:      " + checkout(64_900, "FRSH10", today));
        System.out.println("expired:   " + checkout(64_900, "DIWALI25", today));
    }
}
