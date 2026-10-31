import java.util.ArrayDeque;
import java.util.Deque;

/** Pooling cheap objects buys nothing on a modern JVM and adds a classic bug: state from the previous user leaks. */
public class WhenPoolingHurtsDemo {

    static final class ReceiptBuffer { final StringBuilder sb = new StringBuilder(); String customer; }

    static final Deque<ReceiptBuffer> POOL = new ArrayDeque<>();
    static ReceiptBuffer borrow() { return POOL.isEmpty() ? new ReceiptBuffer() : POOL.pop(); }
    static void giveBack(ReceiptBuffer b) { POOL.push(b); }                                // (1)!

    static String receipt(String customer, String items) {
        ReceiptBuffer b = borrow();
        if (b.customer == null) b.customer = customer;                                    // (2)!
        b.sb.append(items);
        String out = "Receipt for " + b.customer + ": " + b.sb;
        giveBack(b);
        return out;
    }

    static String receiptWithoutPool(String customer, String items) {
        return "Receipt for " + customer + ": " + new StringBuilder().append(items);       // (3)!
    }

    public static void main(String[] args) {
        System.out.println(receipt("asha", "MILK,BREAD"));
        System.out.println(receipt("ravi", "EGGS"));
        System.out.println("without pool: " + receiptWithoutPool("ravi", "EGGS"));
    }
}
