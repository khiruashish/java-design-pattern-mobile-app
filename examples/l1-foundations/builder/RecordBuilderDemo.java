import java.util.Objects;

/** Records + builders: canonical constructor validates, a small nested builder handles optional fields, withers copy. */
public class RecordBuilderDemo {

    record Coupon(String code, int percentOff, long minBasketCents, boolean firstOrderOnly) {
        Coupon {                                                                           // (1)!
            Objects.requireNonNull(code, "code");
            if (percentOff < 1 || percentOff > 90) throw new IllegalArgumentException("percentOff must be 1..90");
            if (minBasketCents < 0) throw new IllegalArgumentException("minBasketCents must be >= 0");
        }

        static Builder builder(String code, int percentOff) { return new Builder(code, percentOff); }

        Coupon withPercentOff(int p) { return new Coupon(code, p, minBasketCents, firstOrderOnly); }   // (2)!

        Builder toBuilder() { return new Builder(code, percentOff).minBasketCents(minBasketCents).firstOrderOnly(firstOrderOnly); }   // (3)!

        static final class Builder {
            private final String code; private final int percentOff;
            private long minBasketCents = 0; private boolean firstOrderOnly = false;     // defaults in one place
            private Builder(String code, int percentOff) { this.code = code; this.percentOff = percentOff; }
            Builder minBasketCents(long v) { minBasketCents = v; return this; }
            Builder firstOrderOnly(boolean v) { firstOrderOnly = v; return this; }
            Coupon build() { return new Coupon(code, percentOff, minBasketCents, firstOrderOnly); }   // (4)!
        }
    }

    public static void main(String[] args) {
        Coupon welcome = Coupon.builder("WELCOME20", 20).firstOrderOnly(true).build();
        Coupon big = Coupon.builder("BIGBASKET", 10).minBasketCents(150_000).build();
        System.out.println(welcome);
        System.out.println(big);
        System.out.println("wither: " + welcome.withPercentOff(25));
        System.out.println("toBuilder: " + big.toBuilder().firstOrderOnly(true).build());
        try { Coupon.builder("BAD", 95).build(); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
