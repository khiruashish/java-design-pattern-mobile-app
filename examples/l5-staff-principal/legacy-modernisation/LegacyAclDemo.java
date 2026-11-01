import java.time.LocalDate;
import java.util.List;

/**
 * Anti-corruption layer for a legacy system: the old billing mainframe exports fixed-width records with padded
 * fields, implied decimals, two-digit years and Y/N flags. The ACL turns them into clean domain objects and rejects
 * records it can't interpret, instead of letting legacy quirks leak into new code.
 */
public class LegacyAclDemo {

    record Customer(String id, String name, long balancePaise, boolean codAllowed, LocalDate since) {}

    static Customer translate(String record) {
        if (record.length() != 36) throw new IllegalArgumentException("expected 36 chars, got " + record.length());
        String id = record.substring(0, 8).trim();                                                      // (1)!
        String name = record.substring(8, 22).trim();
        long balance = Long.parseLong(record.substring(22, 29));            // implied 2 decimals: 0013600 = Rs 136.00
        String cod = record.substring(29, 30);
        if (!cod.equals("Y") && !cod.equals("N")) throw new IllegalArgumentException("unknown COD flag '" + cod + "'");
        int yy = Integer.parseInt(record.substring(30, 32));
        int year = yy >= 70 ? 1900 + yy : 2000 + yy;                                                     // (2)!
        LocalDate since = LocalDate.of(year, Integer.parseInt(record.substring(32, 34)), Integer.parseInt(record.substring(34, 36)));
        return new Customer(id.replaceFirst("^CUST0*", "C-"), name, balance, cod.equals("Y"), since);    // (3)!
    }

    public static void main(String[] args) {
        for (String r : List.of("CUST0042Asha Rao      0013600Y980315", "CUST1207Vikram S      0000000N240102", "CUST0099Old record    0000500X991231")) {
            try { System.out.println(translate(r)); }
            catch (IllegalArgumentException e) { System.out.println("quarantined: " + e.getMessage()); }
        }
    }
}
