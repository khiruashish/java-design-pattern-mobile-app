import java.util.List;

/** Minimal: the stock of one SKU as a ledger of movements; the count on hand is derived, never overwritten. */
public class StockLedgerDemo {

    record Movement(String type, int units) {}

    static int onHand(List<Movement> ledger) {
        return ledger.stream().mapToInt(m -> m.type().equals("received") ? m.units() : -m.units()).sum();   // (1)!
    }

    public static void main(String[] args) {
        List<Movement> milk = List.of(                                                                    // (2)!
                new Movement("received", 40), new Movement("sold", 12), new Movement("damaged", 2), new Movement("sold", 9));
        System.out.println("MILK-1L on hand: " + onHand(milk));
        System.out.println("after the first two movements: " + onHand(milk.subList(0, 2)));
        System.out.println("written off as damaged: " + milk.stream().filter(m -> m.type().equals("damaged")).mapToInt(Movement::units).sum());
    }
}
