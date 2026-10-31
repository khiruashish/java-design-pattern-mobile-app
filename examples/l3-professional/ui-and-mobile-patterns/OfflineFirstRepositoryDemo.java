import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Offline-first: the local database is the source of truth; writes queue up and sync when the network returns. */
public class OfflineFirstRepositoryDemo {

    static final class FakeServer {
        boolean online = true;
        final Map<String, Integer> shoppingList = new TreeMap<>(Map.of("rice", 1));
        Map<String, Integer> fetch() { check(); return new TreeMap<>(shoppingList); }
        void put(String item, int qty) { check(); shoppingList.put(item, qty); }
        private void check() { if (!online) throw new IllegalStateException("offline"); }
    }

    static final class ShoppingListRepository {
        private final Map<String, Integer> localDb = new TreeMap<>();         // (1)!
        private final Deque<String[]> outbox = new ArrayDeque<>();            // (2)!
        private final FakeServer server;
        final List<String> log = new ArrayList<>();
        ShoppingListRepository(FakeServer server) { this.server = server; }

        Map<String, Integer> items() { return new TreeMap<>(localDb); }    // UI always reads local data, instantly

        void setQuantity(String item, int qty) {
            localDb.put(item, qty);
            outbox.add(new String[] {item, String.valueOf(qty)});
            sync();
        }

        void sync() {                                                          // (3)!
            try {
                while (!outbox.isEmpty()) {
                    String[] op = outbox.peek();
                    server.put(op[0], Integer.parseInt(op[1]));
                    outbox.poll();
                }
                localDb.putAll(server.fetch());
                log.add("synced, pending=0");
            } catch (IllegalStateException e) {
                log.add("offline, pending=" + outbox.size());
            }
        }
    }

    public static void main(String[] args) {
        FakeServer server = new FakeServer();
        ShoppingListRepository repo = new ShoppingListRepository(server);
        repo.sync();
        System.out.println("start: " + repo.items());

        server.online = false;
        repo.setQuantity("milk", 2);
        repo.setQuantity("eggs", 12);
        System.out.println("offline, UI shows: " + repo.items() + ", server has: " + server.shoppingList);

        server.online = true;
        server.shoppingList.put("atta", 1);          // added from another phone meanwhile
        repo.sync();
        System.out.println("back online: " + repo.items());
        System.out.println("log: " + repo.log);
    }
}
