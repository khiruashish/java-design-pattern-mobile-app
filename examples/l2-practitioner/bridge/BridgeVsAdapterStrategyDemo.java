/** Three patterns with the same shape (an object holding an interface) and different intents. */
public class BridgeVsAdapterStrategyDemo {

    // ===================== Adapter: make an EXISTING, incompatible API fit after the fact =====================
    static final class LegacyTextBlastApi { int sendMessage(String msisdn, String payload, int priority) { return 202; } }
    interface SmsSender { boolean send(String phone, String text); }
    record TextBlastAdapter(LegacyTextBlastApi api) implements SmsSender {                    // (1)!
        public boolean send(String phone, String text) { return api.sendMessage(phone, text, 1) == 202; }
    }

    // ===================== Strategy: swap ONE algorithm inside one object =====================
    interface FeeRule { long feePaise(long orderPaise); }
    record Checkout(FeeRule rule) {                                                            // (2)!
        long total(long orderPaise) { return orderPaise + rule.feePaise(orderPaise); }
    }

    // ===================== Bridge: TWO hierarchies designed up front to vary independently =====================
    interface Renderer { String render(String title, String body); }
    record Plain() implements Renderer { public String render(String t, String b) { return t + ": " + b; } }
    record Html() implements Renderer { public String render(String t, String b) { return "<h1>" + t + "</h1><p>" + b + "</p>"; } }
    abstract static class Page { final Renderer r; Page(Renderer r) { this.r = r; } abstract String show(); }   // (3)!
    static final class OrderPage extends Page { OrderPage(Renderer r) { super(r); } String show() { return r.render("Order FC-5101", "3 items"); } }
    static final class HelpPage extends Page { HelpPage(Renderer r) { super(r); } String show() { return r.render("Help", "call 1800-000"); } }

    public static void main(String[] args) {
        System.out.println("adapter:  sent = " + new TextBlastAdapter(new LegacyTextBlastApi()).send("+9198", "hi"));
        System.out.println("strategy: " + new Checkout(o -> o >= 50_000 ? 0 : 2_500).total(30_000) + " / " + new Checkout(o -> 1_000).total(30_000));
        System.out.println("bridge:   " + new OrderPage(new Plain()).show() + " | " + new HelpPage(new Html()).show());
    }
}
