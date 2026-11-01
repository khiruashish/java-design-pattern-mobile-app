import java.util.List;

/** Value objects: immutable, compared by value, self-validating, and they carry the behaviour of the concept. */
public class ValueObjectDemo {

    record Money(long paise, String currency) {                                          // (1)!
        Money {
            if (paise < 0) throw new IllegalArgumentException("money can't be negative");
            if (!currency.equals("INR")) throw new IllegalArgumentException("only INR supported");
        }
        static Money rupees(long r) { return new Money(r * 100, "INR"); }
        Money plus(Money o) { return new Money(paise + o.paise, currency); }
        Money times(int q) { return new Money(paise * q, currency); }
        Money percentOff(int p) { return new Money(paise - paise * p / 100, currency); }
        @Override public String toString() { return "Rs " + paise / 100 + "." + String.format("%02d", paise % 100); }
    }

    record Quantity(int value, String unit) {
        Quantity { if (value <= 0 || value > 50) throw new IllegalArgumentException("quantity 1..50, got " + value); }
    }

    record DeliveryWindow(int startHour, int endHour) {                                  // (2)!
        DeliveryWindow {
            if (startHour < 6 || endHour > 23 || endHour - startHour != 2) throw new IllegalArgumentException("windows are 2 hours between 06 and 23");
        }
        boolean overlaps(DeliveryWindow o) { return startHour < o.endHour && o.startHour < endHour; }
    }

    public static void main(String[] args) {
        Money price = Money.rupees(68);
        Money line = price.times(new Quantity(3, "pack").value());
        System.out.println("line: " + line + ", after 10% off: " + line.percentOff(10) + ", original unchanged: " + line);
        System.out.println("equal by value: " + Money.rupees(68).equals(new Money(6_800, "INR")));           // (3)!
        System.out.println("07-09 overlaps 08-10: " + new DeliveryWindow(7, 9).overlaps(new DeliveryWindow(8, 10)));
        for (Runnable bad : List.<Runnable>of(() -> new Quantity(0, "kg"), () -> new DeliveryWindow(22, 24), () -> new Money(100, "USD"))) {
            try { bad.run(); } catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
