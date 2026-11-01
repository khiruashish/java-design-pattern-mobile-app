import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Scopes and lifecycles: singleton, per-use (prototype), per-request; plus the "singleton holds a request object" trap. */
public class ScopesDemo {

    static int nextId = 0;
    static final class RequestContext { final int id = ++nextId; String customer; }

    // ===================== Trap: a singleton captures ONE request-scoped object forever =====================
    static final class GreetingServiceBroken {
        private final RequestContext ctx;                                                  // (1)!
        GreetingServiceBroken(RequestContext ctx) { this.ctx = ctx; }
        String greet() { return "Hello " + ctx.customer + " (ctx #" + ctx.id + ")"; }
    }

    // ===================== Fix: inject a provider and ask for the CURRENT one each time =====================
    static final class GreetingService {
        private final Supplier<RequestContext> ctx;                                        // (2)!
        GreetingService(Supplier<RequestContext> ctx) { this.ctx = ctx; }
        String greet() { return "Hello " + ctx.get().customer + " (ctx #" + ctx.get().id + ")"; }
    }

    // ===================== Lifecycle: singletons are started once and closed in reverse order =====================
    static final List<String> LOG = new ArrayList<>();
    record Resource(String name) implements AutoCloseable {
        Resource { LOG.add("start " + name); }
        public void close() { LOG.add("stop " + name); }
    }

    public static void main(String[] args) throws Exception {
        ThreadLocal<RequestContext> current = new ThreadLocal<>();                          // (3)!

        current.set(new RequestContext()); current.get().customer = "asha";
        GreetingServiceBroken broken = new GreetingServiceBroken(current.get());           // singleton created during the first request
        GreetingService fixed = new GreetingService(current::get);
        System.out.println("request 1: " + broken.greet() + " | " + fixed.greet());

        current.set(new RequestContext()); current.get().customer = "ravi";               // next request
        System.out.println("request 2: " + broken.greet() + " | " + fixed.greet());
        current.remove();

        List<AutoCloseable> singletons = new ArrayList<>();
        singletons.add(new Resource("db-pool"));
        singletons.add(new Resource("payment-client"));                                    // depends on nothing, but created later
        singletons.add(new Resource("order-service"));                                     // depends on both
        for (int i = singletons.size() - 1; i >= 0; i--) singletons.get(i).close();      // (4)!
        System.out.println("lifecycle: " + LOG);
    }
}
