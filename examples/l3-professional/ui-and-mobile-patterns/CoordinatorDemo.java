import java.util.ArrayList;
import java.util.List;

/** A Coordinator owns navigation: screens report what happened, and the coordinator decides where to go next. */
public class CoordinatorDemo {

    sealed interface Event {}                                            // (1)!
    record ProductTapped(String sku) implements Event {}
    record AddedToCart(String sku) implements Event {}
    record CheckoutTapped() implements Event {}
    record LoginSucceeded() implements Event {}
    record PaymentDone(String orderId) implements Event {}

    static final class Navigator {
        final List<String> stack = new ArrayList<>(List.of("home"));
        void push(String screen) { stack.add(screen); }
        void resetTo(String screen) { stack.clear(); stack.add(screen); }
        public String toString() { return String.join(" > ", stack); }
    }

    static final class ShopCoordinator {                                 // (2)!
        private final Navigator nav; private boolean loggedIn; private boolean checkoutPending;
        ShopCoordinator(Navigator nav, boolean loggedIn) { this.nav = nav; this.loggedIn = loggedIn; }

        void on(Event e) {
            switch (e) {
                case ProductTapped p -> nav.push("product/" + p.sku());
                case AddedToCart a -> nav.push("cart");
                case CheckoutTapped c -> {
                    if (loggedIn) nav.push("payment");
                    else { checkoutPending = true; nav.push("login"); }       // (3)!
                }
                case LoginSucceeded l -> {
                    loggedIn = true;
                    nav.stack.remove(nav.stack.size() - 1);
                    if (checkoutPending) { checkoutPending = false; nav.push("payment"); }
                }
                case PaymentDone d -> nav.resetTo("order/" + d.orderId());
            }
        }

        void openDeepLink(String url) {
            nav.resetTo("home");
            if (url.startsWith("freshcart://product/")) on(new ProductTapped(url.substring("freshcart://product/".length())));
        }
    }

    public static void main(String[] args) {
        Navigator nav = new Navigator();
        ShopCoordinator coordinator = new ShopCoordinator(nav, false);
        for (Event e : List.of(new ProductTapped("MANGO-1KG"), new AddedToCart("MANGO-1KG"), new CheckoutTapped(),
                new LoginSucceeded(), new PaymentDone("FC-901"))) {
            coordinator.on(e);
            System.out.println(String.format("%-36s %s", e, nav));
        }
        coordinator.openDeepLink("freshcart://product/GHEE-1L");
        System.out.println(String.format("%-36s %s", "deep link", nav));
    }
}
