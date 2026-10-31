import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** DIP: the stock-alert policy owns its abstractions; Slack, email and the warehouse API are plug-ins. */
public class DipDemo {

    // ===================== BEFORE: policy creates its own details =====================
    static final class SmtpClient { String send(String to, String text) { return "SMTP " + to + ": " + text; } }

    static final class StockAlertsBefore {                                                 // (1)!
        private final SmtpClient smtp = new SmtpClient();       // hard-wired: can't test without SMTP
        String check(String sku, int qty) { return qty < 10 ? smtp.send("store@freshcart.example", sku + " low: " + qty) : "ok"; }
    }

    // ===================== AFTER: abstractions owned by the policy =====================
    interface StockLevels { Map<String, Integer> current(); }                              // (2)!
    interface AlertChannel { void alert(String message); }

    /** High-level policy. Knows nothing about Slack, SMTP or HTTP. */
    static final class LowStockAlerts {
        private final StockLevels stock;
        private final AlertChannel channel;
        private final int threshold;
        LowStockAlerts(StockLevels stock, AlertChannel channel, int threshold) {
            if (threshold <= 0) throw new IllegalArgumentException("threshold must be positive");
            this.stock = stock; this.channel = channel; this.threshold = threshold;
        }
        int run() {
            int alerts = 0;
            for (var e : new java.util.TreeMap<>(stock.current()).entrySet()) {
                if (e.getValue() < threshold) { channel.alert(e.getKey() + " low: " + e.getValue() + " left"); alerts++; }
            }
            return alerts;
        }
    }

    // ---- Low-level details implement the policy's interfaces ----
    record SlackChannel(String webhookName) implements AlertChannel {
        public void alert(String m) { System.out.println("slack#" + webhookName + " <- " + m); }
    }
    record EmailChannel(String to) implements AlertChannel {
        public void alert(String m) { System.out.println("email " + to + " <- " + m); }
    }

    /** A decorator that adds retries to ANY channel: possible only because of the abstraction. */
    record RetryingChannel(AlertChannel inner, int attempts) implements AlertChannel {     // (3)!
        public void alert(String m) {
            RuntimeException last = null;
            for (int i = 1; i <= attempts; i++) {
                try { inner.alert(m); return; } catch (RuntimeException e) { last = e; System.out.println("  attempt " + i + " failed: " + e.getMessage()); }
            }
            throw new IllegalStateException("alert failed after " + attempts + " attempts", last);
        }
    }

    /** Composition root: the only place that picks concrete classes, here from configuration. */
    static AlertChannel channelFrom(Map<String, String> config) {                          // (4)!
        AlertChannel base = switch (config.getOrDefault("alerts.channel", "email")) {
            case "slack" -> new SlackChannel(config.getOrDefault("alerts.slack.webhook", "store-ops"));
            case "email" -> new EmailChannel(config.getOrDefault("alerts.email.to", "store@freshcart.example"));
            default -> throw new IllegalArgumentException("unknown alerts.channel " + config.get("alerts.channel"));
        };
        return new RetryingChannel(base, 3);
    }

    public static void main(String[] args) {
        System.out.println("before: " + new StockAlertsBefore().check("MILK-1L", 4));

        StockLevels warehouse = () -> Map.of("MILK-1L", 4, "BREAD-WHT", 25, "EGGS-12", 7);
        int sent = new LowStockAlerts(warehouse, channelFrom(Map.of("alerts.channel", "slack")), 10).run();
        System.out.println("alerts sent: " + sent);

        int[] calls = {0};
        AlertChannel flaky = m -> { if (++calls[0] < 3) throw new RuntimeException("timeout"); System.out.println("flaky <- " + m); };
        new LowStockAlerts(() -> Map.of("MILK-1L", 4), new RetryingChannel(flaky, 3), 10).run();

        List<String> captured = new ArrayList<>();                                         // (5)!
        new LowStockAlerts(warehouse, captured::add, 10).run();
        System.out.println("test double captured: " + captured);
    }
}
