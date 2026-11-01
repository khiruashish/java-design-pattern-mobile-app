import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/** java.util.concurrent.Flow: Observer with back-pressure. The subscriber asks for N items; the publisher never floods it. */
public class FlowReactiveDemo {

    static final class SlowPrinter implements Flow.Subscriber<String> {                                  // (1)!
        private final CountDownLatch done; private Flow.Subscription subscription; private int received;
        SlowPrinter(CountDownLatch done) { this.done = done; }
        public void onSubscribe(Flow.Subscription s) { subscription = s; System.out.println("subscribed, requesting 2"); s.request(2); }
        public void onNext(String event) {
            received++;
            System.out.println("  got " + event);
            if (received % 2 == 0) { System.out.println("  processed a batch, requesting 2 more"); subscription.request(2); }   // (2)!
        }
        public void onError(Throwable t) { System.out.println("error " + t); done.countDown(); }
        public void onComplete() { System.out.println("complete after " + received + " events"); done.countDown(); }
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        try (SubmissionPublisher<String> orders = new SubmissionPublisher<>()) {                        // (3)!
            orders.subscribe(new SlowPrinter(done));
            for (String e : List.of("FC-1 placed", "FC-2 placed", "FC-1 packed", "FC-3 placed", "FC-2 packed")) orders.submit(e);
        }
        done.await();
    }
}
