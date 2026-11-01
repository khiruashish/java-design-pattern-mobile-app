import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** An API gateway: one entry point that routes, authenticates, rate-limits and adds request IDs for every service. */
public class ApiGatewayDemo {

    record Request(String path, Map<String, String> headers) {}
    record Response(int status, String body) {}

    static final class Gateway {
        private final Map<String, Function<Request, Response>> routes = new HashMap<>();
        private final Map<String, Integer> callsThisMinute = new HashMap<>();
        private final int limitPerMinute; private int requestSeq = 1;
        final List<String> accessLog = new ArrayList<>();
        Gateway(int limitPerMinute) { this.limitPerMinute = limitPerMinute; }
        void route(String prefix, Function<Request, Response> service) { routes.put(prefix, service); }

        Response handle(Request in) {
            String token = in.headers().getOrDefault("Authorization", "");
            if (!token.startsWith("Bearer ")) return log(in, new Response(401, "missing token"));               // (1)!
            String client = token.substring(7);
            if (callsThisMinute.merge(client, 1, Integer::sum) > limitPerMinute) return log(in, new Response(429, "slow down"));   // (2)!
            String prefix = "/" + in.path().split("/")[1];
            Function<Request, Response> service = routes.get(prefix);
            if (service == null) return log(in, new Response(404, "no route"));
            Map<String, String> forwarded = new HashMap<>(in.headers());
            forwarded.put("X-Request-Id", "req-" + requestSeq++);                                          // (3)!
            forwarded.put("X-Client", client);
            forwarded.remove("Authorization");
            return log(in, service.apply(new Request(in.path(), forwarded)));
        }
        private Response log(Request in, Response r) { accessLog.add(r.status() + " " + in.path()); return r; }
    }

    public static void main(String[] args) {
        Gateway gw = new Gateway(3);
        gw.route("/catalog", r -> new Response(200, "products for " + r.headers().get("X-Client") + " (" + r.headers().get("X-Request-Id") + ")"));
        gw.route("/orders", r -> new Response(200, "orders, saw auth header: " + r.headers().containsKey("Authorization")));

        Map<String, String> asha = Map.of("Authorization", "Bearer app-asha");
        System.out.println(gw.handle(new Request("/catalog/dairy", asha)));
        System.out.println(gw.handle(new Request("/orders/FC-1", asha)));
        System.out.println(gw.handle(new Request("/wallet/balance", asha)));
        System.out.println(gw.handle(new Request("/catalog/fruit", asha)));
        System.out.println(gw.handle(new Request("/catalog/dairy", Map.of())));
        System.out.println("log: " + gw.accessLog);
    }
}
