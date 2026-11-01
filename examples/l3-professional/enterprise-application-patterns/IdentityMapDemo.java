import java.util.HashMap;
import java.util.Map;

/** An Identity Map makes sure each database row is loaded into exactly one object per unit of work. */
public class IdentityMapDemo {

    static final Map<String, Integer> TABLE = new HashMap<>(Map.of("W-ASHA", 50_000));
    static int queries = 0;

    static final class Wallet {
        final String id; int balance;
        Wallet(String id, int balance) { this.id = id; this.balance = balance; }
    }

    static Wallet loadFromDb(String id) { queries++; return new Wallet(id, TABLE.get(id)); }

    static final class WalletFinder {
        private final Map<String, Wallet> identityMap = new HashMap<>();      // (1)!
        Wallet find(String id) { return identityMap.computeIfAbsent(id, IdentityMapDemo::loadFromDb); }   // (2)!
    }

    public static void main(String[] args) {
        // Before: two parts of one request load the same row into two objects.
        Wallet forRefund = loadFromDb("W-ASHA");
        Wallet forPayment = loadFromDb("W-ASHA");
        forRefund.balance += 10_000;
        forPayment.balance -= 30_000;
        TABLE.put("W-ASHA", forRefund.balance);
        TABLE.put("W-ASHA", forPayment.balance);                              // (3)!
        System.out.println("without map: same object=" + (forRefund == forPayment)
                + ", saved balance=" + TABLE.get("W-ASHA") + " (expected 30000), queries=" + queries);

        // After: both parts get the same object; both changes survive.
        TABLE.put("W-ASHA", 50_000); queries = 0;
        WalletFinder finder = new WalletFinder();
        Wallet a = finder.find("W-ASHA");
        Wallet b = finder.find("W-ASHA");
        a.balance += 10_000;
        b.balance -= 30_000;
        TABLE.put("W-ASHA", a.balance);
        System.out.println("with map:    same object=" + (a == b)
                + ", saved balance=" + TABLE.get("W-ASHA") + ", queries=" + queries);
    }
}
