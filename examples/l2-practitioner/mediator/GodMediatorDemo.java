import java.util.List;

/** A mediator that coordinates everything becomes a god class. Split by workflow, each with only the colleagues it needs. */
public class GodMediatorDemo {

    // ===================== Before: one mediator, every event, every colleague =====================
    static final class StoreMediator {                                                               // (1)!
        static final List<String> COLLEAGUES = List.of("cart", "slots", "payment", "inventory", "riders", "sms", "promos", "analytics", "refunds");
        static final List<String> EVENTS = List.of("cartChanged", "slotChosen", "paid", "packed", "riderFree", "delivered", "cancelled", "stockLow", "refundRequested");
        void on(String event) { /* a 400-line switch lives here */ }
    }

    // ===================== After: one mediator per workflow =====================
    record Mediator(String name, List<String> colleagues, List<String> events) { }                 // (2)!

    static final List<Mediator> SPLIT = List.of(
            new Mediator("CheckoutMediator", List.of("cart", "slots", "payment"), List.of("cartChanged", "slotChosen", "paid")),
            new Mediator("DispatchMediator", List.of("inventory", "riders", "sms"), List.of("packed", "riderFree", "delivered")),
            new Mediator("AfterSalesMediator", List.of("refunds", "sms", "inventory"), List.of("cancelled", "refundRequested")),
            new Mediator("MerchandisingMediator", List.of("promos", "inventory", "analytics"), List.of("stockLow")));

    public static void main(String[] args) {
        System.out.println("before: 1 mediator, " + StoreMediator.COLLEAGUES.size() + " colleagues, " + StoreMediator.EVENTS.size() + " events");
        SPLIT.forEach(m -> System.out.println("after:  " + m.name() + " -> " + m.colleagues().size() + " colleagues, " + m.events().size() + " event" + (m.events().size() == 1 ? "" : "s")));
        for (String colleague : List.of("refunds", "inventory")) {                                   // (3)!
            List<String> users = SPLIT.stream().filter(m -> m.colleagues().contains(colleague)).map(Mediator::name).toList();
            System.out.println("a change to '" + colleague + "' touches " + users + " (before: StoreMediator, which every team edits)");
        }
    }
}
