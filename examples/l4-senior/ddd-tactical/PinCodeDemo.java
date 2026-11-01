import java.util.List;

/** QuickDrop: replace a String PIN code used everywhere with a value object that validates once and knows its zone. */
public class PinCodeDemo {

    record PinCode(String value) {
        PinCode {                                                                        // (1)!
            value = value.strip();
            if (!value.matches("[1-9]\\d{5}")) throw new IllegalArgumentException("'" + value + "' is not a 6-digit PIN code");
        }
        String region() { return switch (value.charAt(0)) { case '1', '2' -> "North"; case '3', '4' -> "West"; case '5', '6' -> "South"; default -> "East"; }; }
        boolean sameCityAs(PinCode o) { return value.substring(0, 3).equals(o.value.substring(0, 3)); }   // (2)!
    }

    public static void main(String[] args) {
        PinCode home = new PinCode(" 560034 "), office = new PinCode("560103");
        System.out.println(home + " region " + home.region() + ", same city as office: " + home.sameCityAs(office));
        for (String raw : List.of("400001", "056003", "5600")) {
            try { System.out.println(new PinCode(raw).region()); }
            catch (IllegalArgumentException e) { System.out.println("rejected: " + e.getMessage()); }
        }
    }
}
