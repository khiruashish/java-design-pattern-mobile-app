import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Servlet filters and Spring interceptors are chains: each filter can act before, after, or stop the request. */
public class FiltersInterceptorsDemo {

    record Request(String path, Map<String, String> headers) { }
    record Response(int status, String body) { }

    interface Filter { Response doFilter(Request req, Chain chain); }                            // (1)!
    interface Chain { Response proceed(Request req); }

    static Chain build(List<Filter> filters, Chain endpoint) {
        Chain chain = endpoint;
        for (int i = filters.size() - 1; i >= 0; i--) {                                           // (2)!
            Filter f = filters.get(i); Chain next = chain;
            chain = req -> f.doFilter(req, next);
        }
        return chain;
    }

    static final List<String> LOG = new ArrayList<>();

    static final Filter LOGGING = (req, chain) -> {
        LOG.add("-> " + req.path());
        Response r = chain.proceed(req);                                                         // (3)!
        LOG.add("<- " + r.status());
        return r;
    };
    static final Filter AUTH = (req, chain) -> req.headers().containsKey("Authorization")
            ? chain.proceed(req)
            : new Response(401, "login required");                                                // (4)!

    public static void main(String[] args) {
        Chain app = build(List.of(LOGGING, AUTH), req -> new Response(200, "orders for " + req.headers().get("Authorization")));
        System.out.println(app.proceed(new Request("/orders", Map.of("Authorization", "asha"))));
        System.out.println(app.proceed(new Request("/orders", Map.of())));
        System.out.println("log: " + LOG);

        LOG.clear();
        Chain wrongOrder = build(List.of(AUTH, LOGGING), req -> new Response(200, "ok"));
        wrongOrder.proceed(new Request("/orders", Map.of()));
        System.out.println("auth before logging: rejected request not logged at all -> log " + LOG);
    }
}
