import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reversibility: classify decisions as one-way or two-way doors by what it would cost to undo them, and buy
 * reversibility cheaply where it matters with a seam (an interface) at the boundary.
 */
public class ReversibilityDemo {

    record Decision(String name, int filesTouchedToUndo, int teamsInvolved, boolean dataMigration, boolean externalContract) {
        int reversalCost() { return filesTouchedToUndo * teamsInvolved * (dataMigration ? 5 : 1) * (externalContract ? 3 : 1); }   // (1)!
        String door() { return reversalCost() >= 100 ? "one-way door: slow down, write an ADR, prototype" : "two-way door: decide fast, revisit later"; }
    }

    public static void main(String[] args) {
        List<String> sources = List.of(
                "CheckoutService: PayLinkClient.charge(...)", "RefundService: PayLinkClient.refund(...)",
                "SubscriptionBilling: PayLinkClient.mandate(...)", "WalletTopUp: PayLinkClient.charge(...)",
                "AdminTools: PayLinkClient.lookup(...)", "Reconciliation: PayLinkClient.settlements(...)");
        System.out.println("PayLink calls spread through the code: " + sources.size() + " files to change to switch provider");
        System.out.println("behind a PaymentGateway port: 1 adapter file to change");                // (2)!

        Map<String, String> verdicts = new TreeMap<>();
        for (Decision d : List.of(
                new Decision("Caffeine vs Guava cache inside pricing", 2, 1, false, false),
                new Decision("PayLink SDK called directly everywhere", 6, 4, true, false),
                new Decision("Event schema published to 4 teams", 8, 5, true, true),
                new Decision("Payment provider behind a port", 1, 1, true, false)))
            verdicts.put(d.name(), "cost " + d.reversalCost() + ": " + d.door());                    // (3)!
        verdicts.forEach((k, v) -> System.out.println(k + " -> " + v));
    }
}
