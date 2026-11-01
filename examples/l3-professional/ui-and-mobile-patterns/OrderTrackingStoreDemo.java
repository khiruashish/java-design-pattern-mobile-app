import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DairyDay's order-tracking screen with MVI: one immutable state, a pure reducer, side effects run outside
 * the reducer, stale responses ignored, offline handled, and state that survives process death.
 */
public class OrderTrackingStoreDemo {

    // ---------- State ----------
    enum Phase { IDLE, LOADING, SHOWING, OFFLINE, ERROR }
    record TrackingState(Phase phase, String orderId, int requestNo, String status, Integer etaMinutes, String banner) {   // (1)!
        static TrackingState initial() { return new TrackingState(Phase.IDLE, null, 0, null, null, null); }
        String saveForRestore() { return orderId == null ? "" : orderId; }
        static TrackingState restore(String saved) {
            return saved.isEmpty() ? initial() : new TrackingState(Phase.IDLE, saved, 0, null, null, null);
        }
    }

    // ---------- Intents (from the user and from results) ----------
    sealed interface Intent {}
    record Open(String orderId) implements Intent {}
    record Refresh() implements Intent {}
    record Loaded(int requestNo, String status, int eta) implements Intent {}
    record Failed(int requestNo, boolean offline, String message) implements Intent {}
    record ConnectivityBack() implements Intent {}

    // ---------- Effects the reducer asks for; the store runs them ----------
    sealed interface Effect {}
    record FetchStatus(String orderId, int requestNo) implements Effect {}
    record Vibrate() implements Effect {}

    record Next(TrackingState state, List<Effect> effects) {}

    static Next reduce(TrackingState s, Intent intent) {                         // (2)!
        return switch (intent) {
            case Open o -> load(new TrackingState(Phase.LOADING, o.orderId(), s.requestNo(), null, null, null));
            case Refresh r -> s.orderId() == null || s.phase() == Phase.LOADING ? new Next(s, List.of()) : load(s);
            case ConnectivityBack c -> s.phase() == Phase.OFFLINE ? load(s) : new Next(s, List.of());
            case Loaded l when l.requestNo() != s.requestNo() -> new Next(s, List.of());     // (3)!
            case Failed f when f.requestNo() != s.requestNo() -> new Next(s, List.of());
            case Loaded l -> {
                var next = new TrackingState(Phase.SHOWING, s.orderId(), s.requestNo(), l.status(), l.eta(), null);
                boolean arrived = l.status().equals("DELIVERED") && !"DELIVERED".equals(s.status());
                yield new Next(next, arrived ? List.of(new Vibrate()) : List.of());
            }
            case Failed f -> new Next(new TrackingState(f.offline() ? Phase.OFFLINE : Phase.ERROR, s.orderId(), s.requestNo(),
                    s.status(), s.etaMinutes(), f.offline() ? "You're offline. Showing last known status." : "Couldn't refresh: " + f.message()), List.of());
        };
    }

    private static Next load(TrackingState s) {
        int no = s.requestNo() + 1;
        var loading = new TrackingState(Phase.LOADING, s.orderId(), no, s.status(), s.etaMinutes(), s.banner());   // keep last data visible
        return new Next(loading, List.of(new FetchStatus(s.orderId(), no)));
    }

    // ---------- Store: the only place that holds state and runs effects ----------
    interface TrackingApi { void fetch(String orderId, int requestNo, Consumer<Intent> reply); }

    static final class Store {                                                   // (4)!
        private TrackingState state;
        private final TrackingApi api;
        private final List<Consumer<TrackingState>> views = new ArrayList<>();
        final List<String> effectsRun = new ArrayList<>();
        Store(TrackingState initial, TrackingApi api) { state = initial; this.api = api; }

        synchronized void dispatch(Intent intent) {
            Next next = reduce(state, intent);
            boolean changed = !next.state().equals(state);
            state = next.state();
            if (changed) views.forEach(v -> v.accept(state));
            for (Effect e : next.effects()) run(e);
        }
        private void run(Effect e) {                                             // (5)!
            effectsRun.add(e.getClass().getSimpleName());
            switch (e) {
                case FetchStatus f -> api.fetch(f.orderId(), f.requestNo(), this::dispatch);
                case Vibrate v -> { }
            }
        }
        void render(Consumer<TrackingState> view) { views.add(view); view.accept(state); }
        TrackingState state() { return state; }
    }

    static String screen(TrackingState s) {
        String body = switch (s.phase()) {
            case IDLE -> "nothing to show";
            case LOADING -> s.status() == null ? "spinner" : s.status() + " (refreshing)";
            case SHOWING -> s.status() + ", " + s.etaMinutes() + " min";
            case OFFLINE, ERROR -> (s.status() == null ? "no data" : s.status()) + " | " + s.banner();
        };
        return "[" + s.phase() + "] " + body;
    }

    /** A scripted fake API: answers are held so the demo can deliver them in any order. */
    static final class ScriptedApi implements TrackingApi {
        record Pending(int requestNo, Consumer<Intent> reply) {}
        final List<Pending> pending = new ArrayList<>();
        public void fetch(String orderId, int requestNo, Consumer<Intent> reply) { pending.add(new Pending(requestNo, reply)); }
        void answer(int requestNo, Intent result) {
            Pending p = pending.stream().filter(x -> x.requestNo() == requestNo).findFirst().orElseThrow();
            pending.remove(p);
            p.reply().accept(result);
        }
    }

    public static void main(String[] args) {
        ScriptedApi api = new ScriptedApi();
        Store store = new Store(TrackingState.initial(), api);
        store.render(s -> System.out.println("  " + screen(s)));

        System.out.println("> open DD-42");
        store.dispatch(new Open("DD-42"));
        System.out.println("> pull to refresh twice (second is ignored while loading)");
        store.dispatch(new Refresh());
        store.dispatch(new Refresh());
        System.out.println("> server answers request 1");
        api.answer(1, new Loaded(1, "PACKED", 25));
        System.out.println("> refresh, then the network drops");
        store.dispatch(new Refresh());
        api.answer(2, new Failed(2, true, "no connection"));
        System.out.println("> connectivity back");
        store.dispatch(new ConnectivityBack());
        api.answer(3, new Loaded(3, "DELIVERED", 0));
        System.out.println("> a very late answer to request 2 arrives");
        store.dispatch(new Loaded(2, "OUT_FOR_DELIVERY", 9));

        String saved = store.state().saveForRestore();
        Store restored = new Store(TrackingState.restore(saved), api);
        System.out.println("process restarted, restored order: " + restored.state().orderId());
        System.out.println("effects run: " + store.effectsRun);
    }
}
