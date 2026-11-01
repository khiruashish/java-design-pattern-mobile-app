import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reflection, MethodHandles and VarHandles: reflection looks members up by name and checks access on every call;
 * a MethodHandle is a typed, directly executable reference checked once at lookup, which the JIT can inline;
 * a VarHandle gives atomic and memory-ordered access to fields (what AtomicXxx classes use internally).
 */
public class MethodHandlesDemo {

    static final class Stock {
        volatile int units = 10;
        int reserve(int n) { units -= n; return units; }
    }

    static final VarHandle UNITS;
    static { try { UNITS = MethodHandles.lookup().findVarHandle(Stock.class, "units", int.class); } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); } }

    public static void main(String[] args) throws Throwable {
        Stock s = new Stock();
        Method reflective = Stock.class.getDeclaredMethod("reserve", int.class);                      // (1)!
        System.out.println("reflection: " + reflective.invoke(s, 2));

        MethodHandle handle = MethodHandles.lookup().findVirtual(Stock.class, "reserve", MethodType.methodType(int.class, int.class));   // (2)!
        int left = (int) handle.invokeExact(s, 3);
        System.out.println("method handle: " + left + ", type " + handle.type());

        List<Thread> threads = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();
        Stock shelf = new Stock();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(() -> {
                int current;
                do {
                    current = (int) UNITS.getVolatile(shelf);
                    if (current == 0) return;                                                       // sold out
                } while (!UNITS.compareAndSet(shelf, current, current - 1));                        // (3)!
                succeeded.incrementAndGet();
            });
            threads.add(t); t.start();
        }
        for (Thread t : threads) t.join();
        System.out.println("20 buyers, 10 units: " + succeeded.get() + " reservations, " + shelf.units + " left (never negative)");
    }
}
