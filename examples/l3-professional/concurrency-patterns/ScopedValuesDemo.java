import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

/**
 * Scoped values (preview in Java 21, final in Java 25) bind a value for the duration of a call, immutably,
 * and unbind it automatically. This Java 21 demo shows the problem with ThreadLocal and a small class with
 * the same semantics, so the code runs without --enable-preview.
 */
public class ScopedValuesDemo {

    record RequestContext(String requestId, String tenant) { }

    /** Same shape as java.lang.ScopedValue: where(value).call(...); get() inside; nothing outside. */
    static final class Scoped<T> {                                                                     // (1)!
        private final ThreadLocal<T> holder = new ThreadLocal<>();
        <R> R callWhere(T value, Callable<R> body) throws Exception {
            T previous = holder.get();
            holder.set(value);
            try { return body.call(); } finally { if (previous == null) holder.remove(); else holder.set(previous); }   // (2)!
        }
        T get() { T v = holder.get(); if (v == null) throw new NoSuchElementException("not bound"); return v; }
        boolean isBound() { return holder.get() != null; }
    }

    static final Scoped<RequestContext> CONTEXT = new Scoped<>();

    static String repositoryCall() { return "query for tenant " + CONTEXT.get().tenant() + " [" + CONTEXT.get().requestId() + "]"; }   // (3)!

    public static void main(String[] args) throws Exception {
        System.out.println(CONTEXT.callWhere(new RequestContext("req-1", "freshcart"), ScopedValuesDemo::repositoryCall));
        System.out.println("nested: " + CONTEXT.callWhere(new RequestContext("req-2", "dairyday"),
                () -> CONTEXT.callWhere(new RequestContext("req-2a", "dairyday-audit"), ScopedValuesDemo::repositoryCall) + " then back to " + CONTEXT.get().tenant()));
        System.out.println("bound after the call returned: " + CONTEXT.isBound());
    }
}
