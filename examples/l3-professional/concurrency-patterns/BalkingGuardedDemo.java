import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Balking: if the object isn't in the right state, return immediately (don't wait).
 * Guarded suspension: if the condition isn't true yet, WAIT until another thread makes it true.
 */
public class BalkingGuardedDemo {

    static final class CartAutosave {
        private final AtomicBoolean dirty = new AtomicBoolean(), saving = new AtomicBoolean();
        int saves;
        void edit() { dirty.set(true); }
        String save() {
            if (!dirty.get()) return "balked: nothing changed";                                         // (1)!
            if (!saving.compareAndSet(false, true)) return "balked: save already running";
            try { dirty.set(false); saves++; return "saved"; } finally { saving.set(false); }
        }
    }

    static final class SlotBoard {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition slotFreed = lock.newCondition();
        private int free;
        String takeSlot(String who) throws InterruptedException {
            lock.lock();
            try {
                while (free == 0) slotFreed.await();                                                    // (2)!
                free--;
                return who + " got a slot";
            } finally { lock.unlock(); }
        }
        void release() {
            lock.lock();
            try { free++; slotFreed.signal(); } finally { lock.unlock(); }                              // (3)!
        }
    }

    public static void main(String[] args) throws Exception {
        CartAutosave autosave = new CartAutosave();
        System.out.println(autosave.save());
        autosave.edit();
        System.out.println(autosave.save() + ", then: " + autosave.save());

        SlotBoard board = new SlotBoard();
        CountDownLatch waiting = new CountDownLatch(1);
        String[] result = new String[1];
        Thread customer = Thread.ofVirtual().start(() -> {
            try { waiting.countDown(); result[0] = board.takeSlot("Asha"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        waiting.await();
        Thread.sleep(100);
        System.out.println("customer still waiting for a slot: " + customer.isAlive());
        board.release();
        customer.join();
        System.out.println("after a cancellation freed a slot: " + result[0]);
    }
}
