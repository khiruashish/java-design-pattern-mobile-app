/** Minimal builder: an immutable delivery address with two required and three optional fields. */
public class AddressDemo {

    record Address(String line1, String pincode, String line2, String landmark, String gateCode) {
        static Builder builder(String line1, String pincode) { return new Builder(line1, pincode); }   // (1)!

        static final class Builder {
            private final String line1, pincode;
            private String line2 = "", landmark = "", gateCode = "";
            private Builder(String line1, String pincode) { this.line1 = line1; this.pincode = pincode; }
            Builder line2(String v) { line2 = v; return this; }
            Builder landmark(String v) { landmark = v; return this; }
            Builder gateCode(String v) { gateCode = v; return this; }                    // (2)!
            Address build() {
                if (line1 == null || line1.isBlank()) throw new IllegalStateException("line1 is required");
                if (pincode == null || !pincode.matches("\\d{6}")) throw new IllegalStateException("pincode must be 6 digits");
                return new Address(line1, pincode, line2, landmark, gateCode);
            }
        }
    }

    public static void main(String[] args) {
        Address home = Address.builder("Flat 4B, Lake View", "411001").landmark("Near City Park").gateCode("4471").build();
        Address office = Address.builder("Plot 9, IT Park", "411057").build();
        System.out.println(home);
        System.out.println(office);
        try { Address.builder("Somewhere", "4110").build(); } catch (IllegalStateException e) { System.out.println("rejected: " + e.getMessage()); }
    }
}
