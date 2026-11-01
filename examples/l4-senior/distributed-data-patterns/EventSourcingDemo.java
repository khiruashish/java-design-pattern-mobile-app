import java.util.ArrayList;
import java.util.List;

/**
 * Event sourcing: store the events that happened, not the current state. State is a fold over the events; history,
 * audits and "what was the balance on the 3rd?" come for free. Appends check the expected version.
 */
public class EventSourcingDemo {

    sealed interface WalletEvent permits Credited, Debited {}
    record Credited(int day, long paise, String reason) implements WalletEvent {}
    record Debited(int day, long paise, String orderId) implements WalletEvent {}

    static final class EventStore {
        private final List<WalletEvent> stream = new ArrayList<>();
        int version() { return stream.size(); }
        void append(int expectedVersion, WalletEvent e) {                                          // (1)!
            if (expectedVersion != stream.size())
                throw new IllegalStateException("concurrency conflict: expected v" + expectedVersion + " but stream is at v" + stream.size());
            stream.add(e);
        }
        List<WalletEvent> read() { return List.copyOf(stream); }
    }

    static long balance(List<WalletEvent> events, int upToDay) {                                  // (2)!
        long b = 0;
        for (WalletEvent e : events) {
            switch (e) {
                case Credited c when c.day() <= upToDay -> b += c.paise();
                case Debited d when d.day() <= upToDay -> b -= d.paise();
                default -> { }
            }
        }
        return b;
    }

    static void debit(EventStore store, int day, long paise, String orderId) {                    // (3)!
        int version = store.version();
        if (balance(store.read(), Integer.MAX_VALUE) < paise) throw new IllegalStateException("insufficient balance for " + orderId);
        store.append(version, new Debited(day, paise, orderId));
    }

    public static void main(String[] args) {
        EventStore wallet = new EventStore();
        wallet.append(0, new Credited(1, 50_000, "top-up"));
        debit(wallet, 2, 24_000, "FC-1");
        wallet.append(2, new Credited(3, 2_400, "cashback FC-1"));
        debit(wallet, 5, 13_600, "FC-2");

        System.out.println("events: " + wallet.read().size() + ", balance now: Rs " + balance(wallet.read(), 99) / 100);
        System.out.println("balance at end of day 3: Rs " + balance(wallet.read(), 3) / 100);
        try { debit(wallet, 6, 20_000, "FC-3"); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        int staleVersion = 2;
        try { wallet.append(staleVersion, new Credited(6, 100, "duplicate cashback")); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        System.out.println("audit trail:");
        wallet.read().forEach(e -> System.out.println("  " + e));
    }
}
