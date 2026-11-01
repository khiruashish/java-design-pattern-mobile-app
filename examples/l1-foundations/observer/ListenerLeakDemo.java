import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** The lapsed-listener problem, and the fix: subscribe() returns a handle you close. */
public class ListenerLeakDemo {

    static final class PriceFeed {
        private final List<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();

        void addListener(Consumer<Long> l) { listeners.add(l); }                          // (1)!

        AutoCloseable subscribe(Consumer<Long> l) {                                       // (2)!
            listeners.add(l);
            return () -> listeners.remove(l);
        }

        int listenerCount() { return listeners.size(); }
        void publish(long price) { listeners.forEach(l -> l.accept(price)); }
    }

    /** A product screen that only cares about prices while it's open. */
    static final class ProductScreen implements AutoCloseable {
        private final byte[] images = new byte[20_000];                                  // (3)!
        private final AutoCloseable subscription;
        ProductScreen(PriceFeed feed) { subscription = feed.subscribe(p -> render(p)); }
        void render(long p) { images[0] = (byte) p; }
        @Override public void close() throws Exception { subscription.close(); }
    }

    public static void main(String[] args) throws Exception {
        PriceFeed feed = new PriceFeed();

        for (int i = 0; i < 1_000; i++) {                                                 // user opens and leaves 1000 screens
            byte[] screenState = new byte[20_000];
            feed.addListener(p -> screenState[0] = (byte) (long) p);                      // nobody ever removes these
        }
        System.out.println("leaky: listeners still registered = " + feed.listenerCount() + " (~" + feed.listenerCount() * 20 / 1000 + " MB kept alive)");

        PriceFeed feed2 = new PriceFeed();
        for (int i = 0; i < 1_000; i++) {
            try (ProductScreen screen = new ProductScreen(feed2)) {                       // (4)!
                feed2.publish(129);
            }
        }
        System.out.println("fixed: listeners still registered = " + feed2.listenerCount());
    }
}
