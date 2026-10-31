import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicLong;

/** Enum singleton: serialization-safe and reflection-safe for free (Effective Java, Item 3). */
public class EnumSingletonDemo {

    enum OrderNumbers {                                                               // (1)!
        INSTANCE;

        private final AtomicLong next = new AtomicLong(1000);

        String nextOrderId() { return "FC-" + next.incrementAndGet(); }               // (2)!
    }

    @SuppressWarnings("unchecked")
    static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(OrderNumbers.INSTANCE.nextOrderId());
        System.out.println(OrderNumbers.INSTANCE.nextOrderId());

        OrderNumbers copy = roundTrip(OrderNumbers.INSTANCE);                         // (3)!
        System.out.println("same after serialization: " + (copy == OrderNumbers.INSTANCE));

        try {
            var ctor = OrderNumbers.class.getDeclaredConstructor(String.class, int.class);
            ctor.setAccessible(true);
            ctor.newInstance("SECOND", 1);
        } catch (IllegalArgumentException e) {
            System.out.println("reflection blocked: " + e.getMessage());               // (4)!
        }
    }
}
