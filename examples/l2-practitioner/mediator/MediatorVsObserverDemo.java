import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Observer broadcasts and each listener decides alone; a Mediator holds the coordination rule that involves several parties. */
public class MediatorVsObserverDemo {

    // ===================== Observer: stock broadcasts; listeners react independently =====================
    static final class Stock {
        final List<Consumer<Integer>> listeners = new ArrayList<>();
        void update(int units) { listeners.forEach(l -> l.accept(units)); }                          // (1)!
    }

    // ===================== Mediator: the rule needs stock AND promo AND buyer, so it lives in one place =====================
    static final class Promo { boolean active = true; void pause() { active = false; System.out.println("  promo paused"); } }
    static final class Buyer { void reorder(int qty) { System.out.println("  buyer: reorder " + qty + " units"); } }

    static final class StockPromoMediator {                                                          // (2)!
        private final Promo promo; private final Buyer buyer;
        StockPromoMediator(Promo promo, Buyer buyer) { this.promo = promo; this.buyer = buyer; }
        void stockChanged(int units) {
            if (units < 20 && promo.active) { promo.pause(); buyer.reorder(200); }                   // (3)!
            else if (units < 20) buyer.reorder(100);
        }
    }

    public static void main(String[] args) {
        Stock mangoes = new Stock();
        mangoes.listeners.add(u -> System.out.println("  dashboard shows " + u + " units"));
        mangoes.listeners.add(u -> { if (u < 20) System.out.println("  app shows 'only a few left'"); });
        System.out.println("observer:");
        mangoes.update(15);

        System.out.println("mediator:");
        StockPromoMediator m = new StockPromoMediator(new Promo(), new Buyer());
        m.stockChanged(15);
        m.stockChanged(12);
    }
}
