import java.util.HashMap;
import java.util.Map;

/** QuickDrop's address book in three layers: controller (input/output), service (rules), repository (storage). */
public class ThreeLayerDemo {

    static final class AddressRepository { final Map<String, String> rows = new HashMap<>(); }

    static final class AddressService {                                                  // (1)!
        private final AddressRepository repo;
        AddressService(AddressRepository repo) { this.repo = repo; }
        void save(String customer, String pin) {
            if (!pin.matches("[1-9]\\d{5}")) throw new IllegalArgumentException("invalid PIN " + pin);
            repo.rows.put(customer, pin);
        }
        String pinOf(String customer) { return repo.rows.getOrDefault(customer, "none"); }
    }

    static final class AddressController {                                               // (2)!
        private final AddressService service;
        AddressController(AddressService service) { this.service = service; }
        String post(String customer, String pin) {
            try { service.save(customer, pin); return "200 saved"; }
            catch (IllegalArgumentException e) { return "400 " + e.getMessage(); }
        }
        String get(String customer) { return "200 " + service.pinOf(customer); }
    }

    public static void main(String[] args) {
        AddressController api = new AddressController(new AddressService(new AddressRepository()));
        System.out.println(api.post("asha", "560034"));
        System.out.println(api.post("ravi", "12"));
        System.out.println(api.get("asha") + " / " + api.get("ravi"));
    }
}
