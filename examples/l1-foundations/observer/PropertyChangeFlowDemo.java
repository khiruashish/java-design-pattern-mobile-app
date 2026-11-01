import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

/** Two JDK observer APIs: PropertyChangeSupport (sync, per-property) and Flow/SubmissionPublisher (async, back-pressure). */
public class PropertyChangeFlowDemo {

    // ===================== java.beans.PropertyChangeSupport =====================
    static final class Product {
        private final PropertyChangeSupport changes = new PropertyChangeSupport(this);   // (1)!
        private long priceCents; private int stock;
        Product(long price, int stock) { this.priceCents = price; this.stock = stock; }
        void addListener(String property, PropertyChangeListener l) { changes.addPropertyChangeListener(property, l); }
        void setPrice(long p) { long old = priceCents; priceCents = p; changes.firePropertyChange("price", old, p); }
        void setStock(int s) { int old = stock; stock = s; changes.firePropertyChange("stock", old, s); }   // (2)!
    }

    // ===================== java.util.concurrent.Flow =====================
    static final class SlowWarehouseSubscriber implements Flow.Subscriber<String> {
        final List<String> received = new CopyOnWriteArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        private Flow.Subscription subscription;
        public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(2); }  // (3)!
        public void onNext(String pickList) {
            received.add(pickList);
            if (received.size() % 2 == 0) subscription.request(2);                       // ask for more only when ready
        }
        public void onError(Throwable t) { done.countDown(); }
        public void onComplete() { done.countDown(); }
    }

    public static void main(String[] args) throws Exception {
        Product milk = new Product(6_800, 40);
        milk.addListener("price", e -> System.out.println("price watcher: " + e.getOldValue() + " -> " + e.getNewValue()));
        milk.addListener("stock", e -> { if ((int) e.getNewValue() == 0) System.out.println("stock watcher: sold out"); });
        milk.setPrice(6_400);
        milk.setPrice(6_400);                                                             // same value: no event
        milk.setStock(0);

        SlowWarehouseSubscriber warehouse = new SlowWarehouseSubscriber();
        try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>()) {      // (4)!
            publisher.subscribe(warehouse);
            for (int i = 1; i <= 5; i++) publisher.submit("PL-" + i);
        }
        warehouse.done.await(5, TimeUnit.SECONDS);
        System.out.println("flow subscriber received in order: " + warehouse.received);
    }
}
