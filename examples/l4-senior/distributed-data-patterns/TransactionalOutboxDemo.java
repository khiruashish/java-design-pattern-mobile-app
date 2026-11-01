import java.util.ArrayList;
import java.util.List;

/**
 * Transactional outbox: write the business row and the event row in the same local transaction; a relay publishes
 * outbox rows afterwards. No more "saved but never published" (or "published but never saved").
 */
public class TransactionalOutboxDemo {

    static final class Db {
        final List<String> orders = new ArrayList<>(), outbox = new ArrayList<>(), sent = new ArrayList<>();
        void inTransaction(Runnable work) {                                                        // (1)!
            List<String> o = new ArrayList<>(orders), x = new ArrayList<>(outbox);
            try { work.run(); } catch (RuntimeException e) { orders.clear(); orders.addAll(o); outbox.clear(); outbox.addAll(x); throw e; }
        }
    }

    static final List<String> broker = new ArrayList<>();
    static boolean brokerUp = true;
    static void publish(String event) { if (!brokerUp) throw new IllegalStateException("broker unavailable"); broker.add(event); }

    public static void main(String[] args) {
        // Dual write: save, then publish. The broker is down at the wrong moment.
        Db db = new Db();
        brokerUp = false;
        db.orders.add("FC-1");
        try { publish("OrderPlaced FC-1"); } catch (IllegalStateException e) { System.out.println("dual write: FC-1 saved, event lost (" + e.getMessage() + ")"); }

        // Outbox: both rows commit together; publishing happens later and is retried.
        db.inTransaction(() -> { db.orders.add("FC-2"); db.outbox.add("OrderPlaced FC-2"); });   // (2)!
        relay(db);
        brokerUp = true;
        relay(db);
        // The relay crashes after publishing but before marking the row sent: the event is published again.
        db.inTransaction(() -> { db.orders.add("FC-3"); db.outbox.add("OrderPlaced FC-3"); });
        publish(db.outbox.get(db.outbox.size() - 1));
        System.out.println("relay crashed before marking FC-3 sent");
        relay(db);
        System.out.println("orders=" + db.orders + " broker=" + broker);
    }

    static void relay(Db db) {                                                                     // (3)!
        for (String event : new ArrayList<>(db.outbox)) {
            if (db.sent.contains(event)) continue;
            try { publish(event); db.sent.add(event); System.out.println("relay published " + event); }
            catch (IllegalStateException e) { System.out.println("relay will retry " + event + " (" + e.getMessage() + ")"); return; }
        }
    }
}
