import java.util.ArrayList;
import java.util.List;

/** Poltergeists: short-lived classes that only pass calls along; they add indirection and no behaviour. */
public class PoltergeistDemo {

    static final List<String> trace = new ArrayList<>();

    static final class InvoiceGenerator { String generate(String orderId) { trace.add("generate"); return "INV-" + orderId; } }

    // ---- Before: a "starter" and a "controller" that exist only to call the next thing. ----
    static final class InvoiceProcessStarter {                               // (1)!
        void start(String orderId, InvoiceProcessController c) { trace.add("starter"); c.control(orderId); }
    }
    static final class InvoiceProcessController {
        String result;
        void control(String orderId) { trace.add("controller"); result = new InvoiceGenerator().generate(orderId); }   // (2)!
    }

    public static void main(String[] args) {
        var controller = new InvoiceProcessController();
        new InvoiceProcessStarter().start("FC-77", controller);
        System.out.println("before: " + controller.result + " via " + trace);

        trace.clear();
        String invoice = new InvoiceGenerator().generate("FC-77");           // (3)!
        System.out.println("after:  " + invoice + " via " + trace);
    }
}
