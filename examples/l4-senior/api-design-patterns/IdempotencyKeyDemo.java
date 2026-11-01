import java.util.HashMap;
import java.util.Map;

/**
 * Idempotency keys: the client sends a unique key with a non-idempotent request (POST). The server stores the
 * result under that key; a retry with the same key gets the stored result instead of repeating the action.
 */
public class IdempotencyKeyDemo {

    record StoredResponse(int requestHash, int status, String body) {}

    static final Map<String, StoredResponse> idempotencyStore = new HashMap<>();
    static int ordersCreated;

    static String postOrder(String idempotencyKey, String body) {
        StoredResponse previous = idempotencyStore.get(idempotencyKey);
        if (previous != null) {
            if (previous.requestHash() != body.hashCode())                                         // (1)!
                return "422 key reused with a different request";
            return previous.status() + " " + previous.body() + " (replayed)";                       // (2)!
        }
        ordersCreated++;
        StoredResponse created = new StoredResponse(body.hashCode(), 201, "order FC-" + (1000 + ordersCreated));
        idempotencyStore.put(idempotencyKey, created);                                              // (3)!
        return created.status() + " " + created.body();
    }

    public static void main(String[] args) {
        String key = "7f3c9a2e-checkout-C1";
        System.out.println(postOrder(key, "{cart: C-1, total: 13600}"));
        System.out.println(postOrder(key, "{cart: C-1, total: 13600}"));      // app retried after a network timeout
        System.out.println(postOrder(key, "{cart: C-1, total: 99900}"));      // client bug: same key, different body
        System.out.println(postOrder("a91b-checkout-C1", "{cart: C-1, total: 4500}"));
        System.out.println("orders created: " + ordersCreated);
    }
}
