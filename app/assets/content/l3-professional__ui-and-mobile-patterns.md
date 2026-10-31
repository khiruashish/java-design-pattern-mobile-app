# UI and Mobile Patterns

L3 🟡 Professional · ⏱ 45 min read · Prerequisites: [Observer](../l1-foundations/observer.md), [Command](../l2-practitioner/command.md), [State](../l2-practitioner/state.md)

> 📌 **TL;DR**  
> UI code fails when screens hold business logic, state is scattered, and asynchronous answers arrive in surprising orders. **MVC, MVP and MVVM** separate what's shown (view) from what it means (model) and the logic in between (controller, presenter, ViewModel). **MVI / unidirectional data flow** goes further: one immutable state, changed only by a pure reducer, so every screen is a function of state. Mobile apps add **offline-first repositories** (local data is the source of truth), **coordinators** that own navigation, and a **Backend for Frontend** that shapes server data for each client. Android's recommended architecture is MVVM + UDF with Jetpack Compose; Flutter's BLoC and Riverpod follow the same ideas.

## 1. What It Is

**Plain English.** A grocery app's cart screen shows items, totals and a checkout button, reacts to taps, talks to a server that may be slow or unreachable, and must survive the phone rotating or killing the app. UI patterns decide where each of those responsibilities lives so the screen stays correct and testable.

**Definition.** The patterns on this page:

| Pattern | Core idea | Typical home |
|---------|-----------|--------------|
| MVC | Controller handles input, model notifies views | Server-side web (Spring MVC), desktop |
| MVP | Presenter drives a passive view interface | Older Android, GWT, WinForms |
| MVVM | View binds to observable ViewModel state | Android Jetpack, WPF, SwiftUI, Flutter |
| MVI / UDF | Intents → reducer → immutable state → view | Compose, Redux, BLoC |
| Offline-first repository | Local DB is the source of truth; sync in background | Mobile apps |
| Coordinator / navigator | One object decides screen flow | iOS, Android navigation graphs |
| Backend for Frontend | One API layer per client type | Mobile and web backends |

## 2. Why It Exists

Early UIs put everything in the screen class: an Android `Activity` that validated input, called the network, wrote the database and updated widgets. Those screens couldn't be unit-tested, lost state on rotation, leaked memory through callbacks, and showed wrong data when responses arrived out of order. Separating presentation logic from rendering made logic testable; making state explicit and immutable made bugs reproducible; moving data access into repositories made offline support possible.

## 3. Mental Model / Analogy

**Analogy.** A restaurant's order screen in the kitchen. The **model** is the list of orders. The **view** is the screen on the wall. In **MVC**, waiters (controllers) change orders and the screen redraws itself. In **MVP**, a manager (presenter) tells the screen exactly what to show. In **MVVM**, the screen watches a whiteboard (ViewModel) and redraws when it changes. In **MVI**, every change is a written ticket processed in order by one clerk who rewrites the whole whiteboard, so you can replay the day from the tickets.

**Technical model.** Data flows one way: user events → logic → new state → render. Effects (network, navigation, vibration) are triggered by logic but executed outside it, and their results come back as new events.

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### MVC

**Description.** Model-View-Controller splits an interactive app into the **model** (data and rules), **views** (render the model) and **controllers** (turn input into model operations). In classic MVC the model notifies views through Observer; in web MVC (Spring MVC), a controller handles a request, updates or reads the model and picks a view template.

**Advantages / features.** Several views of one model stay in sync; input handling separate from rendering.

**MvcDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MVC: the controller turns input into model changes; the model notifies views; views render the model. */
public class MvcDemo {

    interface ModelListener { void changed(CartModel model); }

    static final class CartModel {
        private final Map<String, Integer> qty = new LinkedHashMap<>();
        private final List<ModelListener> listeners = new ArrayList<>();
        void subscribe(ModelListener l) { listeners.add(l); }
        void add(String item) { qty.merge(item, 1, Integer::sum); listeners.forEach(l -> l.changed(this)); }
        void remove(String item) {
            qty.computeIfPresent(item, (k, v) -> v > 1 ? v - 1 : null);
            listeners.forEach(l -> l.changed(this));
        }
        Map<String, Integer> items() { return Map.copyOf(qty); }
        int count() { return qty.values().stream().mapToInt(Integer::intValue).sum(); }
    }

    static final class CartBadgeView implements ModelListener {
        public void changed(CartModel m) { System.out.println("  [badge] " + m.count() + " items"); }
    }
    static final class CartListView implements ModelListener {
        public void changed(CartModel m) { System.out.println("  [list]  " + new java.util.TreeMap<>(m.items())); }
    }

    static final class CartController {
        private final CartModel model;
        CartController(CartModel model) { this.model = model; }
        void onInput(String command) {
            String[] parts = command.split(" ", 2);
            switch (parts[0]) {
                case "add" -> model.add(parts[1]);
                case "remove" -> model.remove(parts[1]);
                default -> System.out.println("  unknown command: " + command);
            }
        }
    }

    public static void main(String[] args) {
        CartModel model = new CartModel();
        model.subscribe(new CartBadgeView());
        model.subscribe(new CartListView());
        CartController controller = new CartController(model);
        for (String input : List.of("add milk", "add bread", "add milk", "remove milk", "checkout")) {
            System.out.println("> " + input);
            controller.onInput(input);
        }
    }
}
```

1. The model owns the data and notifies listeners on every change; it knows nothing about how it's shown.
2. Two independent views render the same model differently.
3. The controller interprets input and calls model operations; it never touches views.

**Run it:** `java MvcDemo.java`

```text
> add milk
  [badge] 1 items
  [list]  {milk=1}
> add bread
  [badge] 2 items
  [list]  {bread=1, milk=1}
> add milk
  [badge] 3 items
  [list]  {bread=1, milk=2}
> remove milk
  [badge] 2 items
  [list]  {bread=1, milk=1}
> checkout
  unknown command: checkout
```

| Variant | Who updates the view | Where |
|---------|----------------------|-------|
| Classic (Smalltalk) MVC | Model notifies views | Desktop toolkits, Swing |
| Web MVC (Model 2) | Controller picks a view per request | Spring MVC, Rails, ASP.NET MVC |
| "Massive View Controller" | Everything in the controller | Anti-pattern in iOS UIKit apps |

**Use** web MVC for server-rendered pages and REST controllers. **Avoid** classic MVC for modern mobile apps; MVVM or MVI fit reactive toolkits better.
**Real-world Java:** Spring MVC `@Controller` + Thymeleaf; Swing's `JTable` with `TableModel`.
**Best practice:** keep controllers thin; they translate input, call services and choose a view. **Gotcha:** on Android, `Activity` was meant as the controller and ended up as controller + view + model: the "massive activity".

### MVP

**Description.** Model-View-Presenter makes the view **passive**: an interface with methods like `showLoading`, `showError`, `goToHome`. The presenter holds all presentation logic and calls those methods; the real view (an Activity, a Swing frame) just implements them. Tests use a fake view.

**Advantages / features.** Presentation logic testable without a UI toolkit; view is trivially simple.

**MvpDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** MVP: a passive view interface and a presenter holding all UI logic, testable without any UI toolkit. */
public class MvpDemo {

    interface LoginView {
        void showLoading(boolean on);
        void showError(String message);
        void goToHome(String name);
    }

    interface AuthApi { String login(String phone, String otp); }   // returns name or throws

    static final class LoginPresenter {
        private final LoginView view; private final AuthApi api;
        LoginPresenter(LoginView view, AuthApi api) { this.view = view; this.api = api; }

        void onLoginClicked(String phone, String otp) {
            if (!phone.matches("\\d{10}")) { view.showError("Enter a 10-digit phone number"); return; }
            if (!otp.matches("\\d{6}")) { view.showError("OTP has 6 digits"); return; }
            view.showLoading(true);
            try {
                String name = api.login(phone, otp);
                view.goToHome(name);
            } catch (RuntimeException e) {
                view.showError("Login failed: " + e.getMessage());
            } finally {
                view.showLoading(false);
            }
        }
    }

    /** A recording view: what a test (or this demo) uses instead of an Android Activity. */
    static final class RecordingView implements LoginView {
        final List<String> calls = new ArrayList<>();
        public void showLoading(boolean on) { calls.add("loading=" + on); }
        public void showError(String m) { calls.add("error: " + m); }
        public void goToHome(String n) { calls.add("home: " + n); }
    }

    public static void main(String[] args) {
        AuthApi api = (phone, otp) -> {
            if (otp.equals("123456")) return "Asha";
            throw new IllegalArgumentException("wrong OTP");
        };
        for (String[] attempt : new String[][] {{"98765", "123456"}, {"9876543210", "12"}, {"9876543210", "000000"}, {"9876543210", "123456"}}) {
            RecordingView view = new RecordingView();
            new LoginPresenter(view, api).onLoginClicked(attempt[0], attempt[1]);
            System.out.println(attempt[0] + "/" + attempt[1] + " -> " + view.calls);
        }
    }
}
```

1. The view contract: commands the presenter can give. The real screen implements these.
2. All decisions (validation, loading, errors, navigation) live in the presenter.
3. A recording view lets tests assert exactly what the screen was told to do, in order.

**Run it:** `java MvpDemo.java`

```text
98765/123456 -> [error: Enter a 10-digit phone number]
9876543210/12 -> [error: OTP has 6 digits]
9876543210/000000 -> [loading=true, error: Login failed: wrong OTP, loading=false]
9876543210/123456 -> [loading=true, home: Asha, loading=false]
```

| | MVP | MVVM |
|-|-----|------|
| Presenter/ViewModel knows the view | Yes (interface) | No |
| View updates | Imperative calls | Data binding / observation |
| Lifecycle (rotation) | Presenter must be detached/reattached | ViewModel survives configuration changes |
| Tests | Verify calls on fake view | Assert on state values |

**Use** MVP for imperative UI toolkits without binding (Swing, legacy Android Views). **Avoid** it for declarative UIs (Compose, Flutter, SwiftUI), where the view is a function of state.
**Real-world Java:** pre-2017 Android apps (Mosby library), GWT, Vaadin's MVP add-ons.
**Best practice:** one presenter per screen; keep the view interface intention-revealing. **Gotcha:** presenters holding a view reference across asynchronous calls leak the Activity or crash after rotation; detach the view in `onDestroy`.

### MVVM

**Description.** Model-View-ViewModel exposes screen state as **observable properties** on a ViewModel. The view binds to them and sends user actions to ViewModel methods. The ViewModel never references the view, so it survives rotation and is easy to test. On Android: `ViewModel` + `StateFlow`/`LiveData`; in Flutter: `ChangeNotifier`, Riverpod or BLoC.

**Advantages / features.** No view references in logic; automatic UI updates; survives configuration changes.

**MvvmDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** MVVM: the ViewModel exposes observable state; views bind to it and never get called by name. */
public class MvvmDemo {

    /** A minimal LiveData/StateFlow: holds a value and notifies observers on change. */
    static final class Observable<T> {
        private T value; private final List<Consumer<T>> observers = new ArrayList<>();
        Observable(T initial) { value = initial; }
        T get() { return value; }
        void set(T v) { if (!v.equals(value)) { value = v; observers.forEach(o -> o.accept(v)); } }
        void observe(Consumer<T> o) { observers.add(o); o.accept(value); }
    }

    record Slot(String time, int left) {}

    static final class SlotPickerViewModel {
        final Observable<List<String>> options = new Observable<>(List.of());
        final Observable<String> selected = new Observable<>("none");
        final Observable<Boolean> canContinue = new Observable<>(false);

        void load(List<Slot> slots) {
            options.set(slots.stream().map(s -> s.time() + (s.left() == 0 ? " (full)" : " (" + s.left() + " left)")).toList());
        }
        void select(Slot slot) {
            if (slot.left() == 0) return;
            selected.set(slot.time());
            canContinue.set(true);
        }
    }

    public static void main(String[] args) {
        SlotPickerViewModel vm = new SlotPickerViewModel();
        vm.options.observe(o -> System.out.println("  list shows " + o));
        vm.selected.observe(s -> System.out.println("  header shows selected: " + s));
        vm.canContinue.observe(c -> System.out.println("  continue button enabled: " + c));

        List<Slot> slots = List.of(new Slot("07-09", 0), new Slot("09-11", 4), new Slot("18-20", 12));
        System.out.println("> load");
        vm.load(slots);
        System.out.println("> tap 07-09");
        vm.select(slots.get(0));
        System.out.println("> tap 09-11");
        vm.select(slots.get(1));
    }
}
```

1. A minimal observable value, like `StateFlow`: holds a value and notifies observers only when it changes.
2. The ViewModel exposes state and actions; it has no idea which widgets exist.
3. Views subscribe and redraw; tapping a full slot changes nothing, so nothing redraws.

**Run it:** `java MvvmDemo.java`

```text
  list shows []
  header shows selected: none
  continue button enabled: false
> load
  list shows [07-09 (full), 09-11 (4 left), 18-20 (12 left)]
> tap 07-09
> tap 09-11
  header shows selected: 09-11
  continue button enabled: true
```

| Platform | ViewModel | Observable state | View |
|----------|-----------|------------------|------|
| Android | `androidx.lifecycle.ViewModel` | `StateFlow`, `LiveData` | Compose, XML + data binding |
| Flutter | `ChangeNotifier`, Cubit, Riverpod `Notifier` | `ValueNotifier`, streams | Widgets |
| JavaFX | Plain class | `Property<T>` | FXML with bindings |
| iOS | `ObservableObject` | `@Published` | SwiftUI |

**Use** MVVM as the default for mobile and desktop UIs. **Avoid** many independent observables that can drift out of sync; a single state object (below) prevents impossible combinations.
**Real-world Java:** Android Architecture Components, JavaFX bindings.
**Best practice:** expose read-only state (`StateFlow`) and private mutable state. **Gotcha:** one-off events (show a toast, navigate) as state get replayed after rotation; model them as consumed events or effects.

### MVI and unidirectional data flow

**Description.** Model-View-Intent keeps **one immutable state** per screen. User actions and results become **intents**; a pure **reducer** `(state, intent) → state` produces the next state; the view renders it. Side effects run outside the reducer and feed results back as intents. Because the reducer is pure, every bug is a sequence of intents you can replay.

**Advantages / features.** Impossible states are unrepresentable; time-travel debugging; trivial unit tests; ordering problems become explicit.

**MviDemo.java**

```java
import java.util.List;

/** MVI: intents go into a pure reducer that returns a new immutable state; the view renders the state. */
public class MviDemo {

    record SearchState(String query, boolean loading, List<String> results, String error) {
        static SearchState initial() { return new SearchState("", false, List.of(), null); }
    }

    sealed interface Intent {}
    record QueryChanged(String text) implements Intent {}
    record ResultsLoaded(String forQuery, List<String> items) implements Intent {}
    record LoadFailed(String forQuery, String message) implements Intent {}

    static SearchState reduce(SearchState s, Intent intent) {
        return switch (intent) {
            case QueryChanged q -> new SearchState(q.text(), !q.text().isBlank(), List.of(), null);
            case ResultsLoaded r -> r.forQuery().equals(s.query())
                    ? new SearchState(s.query(), false, r.items(), null)
                    : s;                                                 // stale response for an old query: ignore
            case LoadFailed f -> f.forQuery().equals(s.query())
                    ? new SearchState(s.query(), false, List.of(), f.message())
                    : s;
        };
    }

    static String render(SearchState s) {
        if (s.loading()) return "[spinner] searching '" + s.query() + "'";
        if (s.error() != null) return "[error] " + s.error();
        return "[list] " + s.results();
    }

    public static void main(String[] args) {
        List<Intent> intents = List.of(
                new QueryChanged("pan"),
                new QueryChanged("paneer"),
                new ResultsLoaded("pan", List.of("Pan masala", "Pancake mix")),   // late answer for the old query
                new ResultsLoaded("paneer", List.of("Paneer 200g", "Paneer tikka kit")),
                new QueryChanged("xyz"),
                new LoadFailed("xyz", "network timeout"));
        SearchState state = SearchState.initial();
        for (Intent i : intents) {
            state = reduce(state, i);
            System.out.println(i.getClass().getSimpleName() + " -> " + render(state));
        }
    }
}
```

1. The whole screen state in one record.
2. Every way the state can change is an intent type.
3. The reducer is a pure function; it ignores results that belong to an older query.

**Run it:** `java MviDemo.java`

```text
QueryChanged -> [spinner] searching 'pan'
QueryChanged -> [spinner] searching 'paneer'
ResultsLoaded -> [spinner] searching 'paneer'
ResultsLoaded -> [list] [Paneer 200g, Paneer tikka kit]
QueryChanged -> [spinner] searching 'xyz'
LoadFailed -> [error] network timeout
```

| | MVVM (many observables) | MVI (single state) |
|-|-------------------------|---------------------|
| State | Several fields | One immutable object |
| Changes | Methods mutate fields | Reducer returns new state |
| Impossible combinations | Possible (loading + error) | Prevented by design |
| Boilerplate | Lower | Higher (intents, reducer) |
| Debugging | Inspect fields | Replay intent log |

**Use** MVI for complex screens with async results, offline behaviour or many interactions. **Avoid** it for static or form-only screens where it's ceremony.
**Real-world Java:** Redux-style stores in Compose apps (Orbit MVI, Mavericks), Flutter BLoC, Elm-inspired architectures.
**Best practice:** keep reducers pure and fast; run effects in the store. **Gotcha:** creating a new state object on every keystroke is fine; deep-copying large lists each time isn't. Use persistent collections or structural sharing.

### Repository for offline-first apps

**Description.** An offline-first repository makes the **local database the single source of truth**. The UI always reads local data (instantly, even offline). Writes go to the local DB and an **outbox** of pending changes; a sync process pushes them when online and pulls server changes. On Android this is Room + WorkManager; in Flutter, Drift or Isar plus a background sync.

**Advantages / features.** Instant UI; works in lifts and basements; survives app restarts; fewer network calls.

**OfflineFirstRepositoryDemo.java**

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Offline-first: the local database is the source of truth; writes queue up and sync when the network returns. */
public class OfflineFirstRepositoryDemo {

    static final class FakeServer {
        boolean online = true;
        final Map<String, Integer> shoppingList = new TreeMap<>(Map.of("rice", 1));
        Map<String, Integer> fetch() { check(); return new TreeMap<>(shoppingList); }
        void put(String item, int qty) { check(); shoppingList.put(item, qty); }
        private void check() { if (!online) throw new IllegalStateException("offline"); }
    }

    static final class ShoppingListRepository {
        private final Map<String, Integer> localDb = new TreeMap<>();
        private final Deque<String[]> outbox = new ArrayDeque<>();
        private final FakeServer server;
        final List<String> log = new ArrayList<>();
        ShoppingListRepository(FakeServer server) { this.server = server; }

        Map<String, Integer> items() { return new TreeMap<>(localDb); }    // UI always reads local data, instantly

        void setQuantity(String item, int qty) {
            localDb.put(item, qty);
            outbox.add(new String[] {item, String.valueOf(qty)});
            sync();
        }

        void sync() {
            try {
                while (!outbox.isEmpty()) {
                    String[] op = outbox.peek();
                    server.put(op[0], Integer.parseInt(op[1]));
                    outbox.poll();
                }
                localDb.putAll(server.fetch());
                log.add("synced, pending=0");
            } catch (IllegalStateException e) {
                log.add("offline, pending=" + outbox.size());
            }
        }
    }

    public static void main(String[] args) {
        FakeServer server = new FakeServer();
        ShoppingListRepository repo = new ShoppingListRepository(server);
        repo.sync();
        System.out.println("start: " + repo.items());

        server.online = false;
        repo.setQuantity("milk", 2);
        repo.setQuantity("eggs", 12);
        System.out.println("offline, UI shows: " + repo.items() + ", server has: " + server.shoppingList);

        server.online = true;
        server.shoppingList.put("atta", 1);          // added from another phone meanwhile
        repo.sync();
        System.out.println("back online: " + repo.items());
        System.out.println("log: " + repo.log);
    }
}
```

1. The local copy is what the UI reads; it never waits for the network.
2. Changes queue in an outbox in order, so nothing is lost while offline.
3. Sync pushes pending changes first, then pulls the server's view (including changes made elsewhere).

**Run it:** `java OfflineFirstRepositoryDemo.java`

```text
start: {rice=1}
offline, UI shows: {eggs=12, milk=2, rice=1}, server has: {rice=1}
back online: {atta=1, eggs=12, milk=2, rice=1}
log: [synced, pending=0, offline, pending=1, offline, pending=2, synced, pending=0]
```

| Conflict strategy | How | Good for |
|-------------------|-----|----------|
| Last write wins | Timestamp or server order | Preferences, simple lists |
| Server wins | Discard local change, notify user | Prices, stock |
| Field-level merge | Merge non-overlapping fields | Profiles, addresses |
| Operations / CRDTs | Sync operations, not values | Shared lists, collaborative editing |

**Use** offline-first for apps used on the move (shopping lists, delivery rider apps). **Avoid** it for data that must be live (payments, stock reservations); show a clear "offline" state instead.
**Real-world Java:** Android's Now in Android sample (Room + WorkManager), Firebase Firestore's offline persistence.
**Best practice:** make outbox operations idempotent (client-generated IDs) so retries are safe. **Gotcha:** clocks on phones are wrong; don't resolve conflicts with device timestamps.

### Navigation and coordinator patterns

**Description.** A **coordinator** (or navigator, or navigation graph) owns the flow between screens. Screens only report what happened ("checkout tapped", "login succeeded"); the coordinator decides where to go, handles conditional flows (log in first, then continue) and deep links. Screens become reusable in different flows.

**Advantages / features.** Navigation logic in one testable place; screens decoupled from each other; consistent deep-link handling.

**CoordinatorDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/** A Coordinator owns navigation: screens report what happened, and the coordinator decides where to go next. */
public class CoordinatorDemo {

    sealed interface Event {}
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

    static final class ShopCoordinator {
        private final Navigator nav; private boolean loggedIn; private boolean checkoutPending;
        ShopCoordinator(Navigator nav, boolean loggedIn) { this.nav = nav; this.loggedIn = loggedIn; }

        void on(Event e) {
            switch (e) {
                case ProductTapped p -> nav.push("product/" + p.sku());
                case AddedToCart a -> nav.push("cart");
                case CheckoutTapped c -> {
                    if (loggedIn) nav.push("payment");
                    else { checkoutPending = true; nav.push("login"); }
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
```

1. Screens emit events, not navigation commands.
2. The coordinator maps events to navigation, using flow state (logged in, pending checkout).
3. A conditional detour: log in, then continue to payment where the user left off.

**Run it:** `java CoordinatorDemo.java`

```text
ProductTapped[sku=MANGO-1KG]         home > product/MANGO-1KG
AddedToCart[sku=MANGO-1KG]           home > product/MANGO-1KG > cart
CheckoutTapped[]                     home > product/MANGO-1KG > cart > login
LoginSucceeded[]                     home > product/MANGO-1KG > cart > payment
PaymentDone[orderId=FC-901]          order/FC-901
deep link                            home > product/GHEE-1L
```

| Approach | Platform |
|----------|----------|
| Navigation graph (XML or Compose `NavHost`) | Android Jetpack Navigation |
| `go_router` / `Navigator 2.0` | Flutter |
| Coordinator objects | iOS UIKit |
| Router with route table | Web SPAs (Angular, React Router) |

**Use** a coordinator or navigation graph when flows have conditions, deep links or reuse screens. **Avoid** screens calling each other directly (`startActivity(new Intent(this, PaymentActivity.class))` scattered everywhere).
**Real-world Java:** Jetpack Navigation with Safe Args; Spring Web Flow for server-side wizards.
**Best practice:** treat deep links as just another entry into the coordinator, and rebuild a sensible back stack. **Gotcha:** after payment, reset the stack; pressing Back into a "pay now" screen causes double payments.

### Backend for Frontend

**Description.** A Backend for Frontend (BFF) is a server-side API built for one client type (mobile app, web app, partner). It aggregates calls to several microservices, removes fields the client doesn't need, shapes data per screen, and handles client-specific concerns (versioning, auth tokens, image sizes).

**Advantages / features.** One round trip per screen on slow mobile networks; small payloads; clients evolve independently; internal fields stay internal.

**BffDemo.java**

```java
import java.util.List;
import java.util.Map;

/** Backend for Frontend: one backend per client type, shaping and aggregating data for that client's screens. */
public class BffDemo {

    // Downstream microservices, each returning more than any one screen needs.
    record Order(String id, String status, long totalPaise, String warehouse, String internalNotes) {}
    record Rider(String id, String name, String phone, double lat, double lng, String vehicleNo) {}
    record Eta(String orderId, int minutes, String model) {}

    interface OrderService { Order get(String id); }
    interface RiderService { Rider forOrder(String id); }
    interface EtaService { Eta get(String id); }

    // What the mobile tracking screen needs: one small payload, one request.
    record MobileTrackingDto(String status, String total, String rider, String eta) {}
    // What the web support console needs: more detail, different shape.
    record SupportConsoleDto(String orderId, String status, String warehouse, String notes, String riderPhone, String vehicle) {}

    record MobileBff(OrderService orders, RiderService riders, EtaService etas) {
        MobileTrackingDto tracking(String orderId) {
            Order o = orders.get(orderId);
            Rider r = riders.forOrder(orderId);
            Eta e = etas.get(orderId);
            return new MobileTrackingDto(o.status(), "Rs " + o.totalPaise() / 100, r.name(), e.minutes() + " min");
        }
    }
    record SupportBff(OrderService orders, RiderService riders) {
        SupportConsoleDto view(String orderId) {
            Order o = orders.get(orderId);
            Rider r = riders.forOrder(orderId);
            return new SupportConsoleDto(o.id(), o.status(), o.warehouse(), o.internalNotes(), r.phone(), r.vehicleNo());
        }
    }

    public static void main(String[] args) {
        OrderService orders = id -> new Order(id, "OUT_FOR_DELIVERY", 51_800, "BLR-HSR-2", "customer asked for no plastic");
        RiderService riders = id -> new Rider("R7", "Bina", "9000000007", 12.91, 77.64, "KA01AB1234");
        EtaService etas = id -> new Eta(id, 12, "eta-v3");

        var mobile = new MobileBff(orders, riders, etas).tracking("FC-501");
        var support = new SupportBff(orders, riders).view("FC-501");
        System.out.println("mobile:  " + mobile);
        System.out.println("support: " + support);
        System.out.println("mobile payload fields: " + MobileTrackingDto.class.getRecordComponents().length
                + ", downstream fields: " + (Order.class.getRecordComponents().length + Rider.class.getRecordComponents().length + Eta.class.getRecordComponents().length));
    }
}
```

1. Each client gets a DTO shaped for its screen.
2. The mobile BFF calls three services and returns four fields.
3. The support console's BFF returns different, more detailed data from the same services.

**Run it:** `java BffDemo.java`

```text
mobile:  MobileTrackingDto[status=OUT_FOR_DELIVERY, total=Rs 518, rider=Bina, eta=12 min]
support: SupportConsoleDto[orderId=FC-501, status=OUT_FOR_DELIVERY, warehouse=BLR-HSR-2, notes=customer asked for no plastic, riderPhone=9000000007, vehicle=KA01AB1234]
mobile payload fields: 4, downstream fields: 14
```

| | Shared general API | BFF per client | GraphQL gateway |
|-|--------------------|----------------|-----------------|
| Payload shape | Generic | Per screen | Client chooses fields |
| Round trips | Many | One per screen | One |
| Ownership | Platform team | Client team | Platform team |
| Risk | Over-fetching | Duplicated logic across BFFs | Expensive queries |

**Use** a BFF when mobile and web need different shapes or aggregation. **Avoid** putting business rules in the BFF; it should compose and shape, not decide.
**Real-world Java:** Spring Boot or Spring Cloud Gateway services per client; Netflix's and SoundCloud's original BFFs.
**Best practice:** the client team owns its BFF. **Gotcha:** fan-out in the BFF multiplies latency and failure points; call services in parallel with timeouts and degrade optional sections.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** QuickDrop's product page has a quantity stepper: minimum 1, maximum the stock limit, with buttons disabled at the limits.

**QuantityStepperDemo.java**

```java
/** QuickDrop's quantity stepper: a tiny ViewModel whose state the view simply renders. */
public class QuantityStepperDemo {

    record StepperState(int qty, boolean canDecrease, boolean canIncrease) {}

    static final class StepperViewModel {
        private final int max; private int qty = 1;
        StepperViewModel(int max) { this.max = max; }
        StepperState state() { return new StepperState(qty, qty > 1, qty < max); }
        void plus() { if (qty < max) qty++; }
        void minus() { if (qty > 1) qty--; }
    }

    static String render(StepperState s) {
        return (s.canDecrease() ? "[-]" : "[ ]") + " " + s.qty() + " " + (s.canIncrease() ? "[+]" : "[ ]");
    }

    public static void main(String[] args) {
        StepperViewModel vm = new StepperViewModel(3);
        System.out.println(render(vm.state()));
        for (String tap : new String[] {"+", "+", "+", "-", "-", "-"}) {
            if (tap.equals("+")) vm.plus(); else vm.minus();
            System.out.println("tap " + tap + "  " + render(vm.state()));
        }
    }
}
```

1. The ViewModel computes everything the view needs, including which buttons are enabled.
2. The view only renders state; it contains no rules.

**Run it:** `java QuantityStepperDemo.java`

**Output:**

```text
[ ] 1 [+]
tap +  [-] 2 [+]
tap +  [-] 3 [ ]
tap +  [-] 3 [ ]
tap -  [-] 2 [+]
tap -  [ ] 1 [+]
tap -  [ ] 1 [+]
```

**What to notice:**

- Taps beyond the limits do nothing, and the disabled state comes from the same rule, so the UI and logic can't disagree.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** the cart screen lists items, shows the subtotal and a "free delivery" nudge, disables checkout when empty, and shows a message when the customer asks for more than is in stock.

**CartScreenDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** FreshCart cart screen in MVVM: repository for data, ViewModel for screen state and actions, a dumb view. */
public class CartScreenDemo {

    record CartLine(String sku, String name, long pricePaise, int qty) {}

    /** Local cart storage plus a catalogue lookup. */
    static final class CartRepository {
        private final Map<String, CartLine> lines = new TreeMap<>();
        private final Map<String, Integer> stock;
        CartRepository(Map<String, Integer> stock) { this.stock = stock; }
        List<CartLine> lines() { return List.copyOf(lines.values()); }
        boolean setQty(String sku, String name, long price, int qty) {
            if (qty > stock.getOrDefault(sku, 0)) return false;
            if (qty == 0) lines.remove(sku); else lines.put(sku, new CartLine(sku, name, price, qty));
            return true;
        }
    }

    record CartUiState(List<String> rows, String subtotal, String deliveryNote, boolean checkoutEnabled, String message) {}

    static final class CartViewModel {
        private final CartRepository repo;
        private final List<Consumer<CartUiState>> observers = new ArrayList<>();
        private String message = "";
        CartViewModel(CartRepository repo) { this.repo = repo; }

        void observe(Consumer<CartUiState> o) { observers.add(o); o.accept(state()); }

        void add(String sku, String name, long price) { change(sku, name, price, +1); }
        void remove(String sku, String name, long price) { change(sku, name, price, -1); }

        private void change(String sku, String name, long price, int delta) {
            int current = repo.lines().stream().filter(l -> l.sku().equals(sku)).mapToInt(CartLine::qty).findFirst().orElse(0);
            boolean ok = repo.setQty(sku, name, price, Math.max(0, current + delta));
            message = ok ? "" : "Only " + current + " " + name + " available";
            observers.forEach(o -> o.accept(state()));
        }

        private CartUiState state() {
            List<CartLine> lines = repo.lines();
            long subtotal = lines.stream().mapToLong(l -> l.pricePaise() * l.qty()).sum();
            long toFree = 49_900 - subtotal;
            String note = lines.isEmpty() ? "" : toFree <= 0 ? "Free delivery" : "Add Rs " + (toFree + 99) / 100 + " for free delivery";
            List<String> rows = lines.stream().map(l -> l.qty() + " x " + l.name()).toList();
            return new CartUiState(rows, "Rs " + subtotal / 100, note, !lines.isEmpty(), message);
        }
    }

    public static void main(String[] args) {
        CartViewModel vm = new CartViewModel(new CartRepository(Map.of("MANGO-1KG", 1, "MILK-1L", 20)));
        vm.observe(s -> System.out.println("  " + s.rows() + " " + s.subtotal() + " | " + s.deliveryNote()
                + " | checkout=" + s.checkoutEnabled() + (s.message().isEmpty() ? "" : " | " + s.message())));
        System.out.println("> add milk x2");
        vm.add("MILK-1L", "Milk 1L", 6_800);
        vm.add("MILK-1L", "Milk 1L", 6_800);
        System.out.println("> add mango x2");
        vm.add("MANGO-1KG", "Alphonso 1kg", 45_000);
        vm.add("MANGO-1KG", "Alphonso 1kg", 45_000);
        System.out.println("> remove mango");
        vm.remove("MANGO-1KG", "Alphonso 1kg", 45_000);
    }
}
```

1. One UI state record holds everything the screen shows.
2. Actions go through the ViewModel, which asks the repository and records any message.
3. The state is derived from repository data every time, so totals can't drift.

**Run it:** `java CartScreenDemo.java`

**Output:**

```text
  [] Rs 0 |  | checkout=false
> add milk x2
  [1 x Milk 1L] Rs 68 | Add Rs 431 for free delivery | checkout=true
  [2 x Milk 1L] Rs 136 | Add Rs 363 for free delivery | checkout=true
> add mango x2
  [1 x Alphonso 1kg, 2 x Milk 1L] Rs 586 | Free delivery | checkout=true
  [1 x Alphonso 1kg, 2 x Milk 1L] Rs 586 | Free delivery | checkout=true | Only 1 Alphonso 1kg available
> remove mango
  [2 x Milk 1L] Rs 136 | Add Rs 363 for free delivery | checkout=true
```

**What to notice:**

- The "only 1 available" message is part of state, so it survives rotation and is easy to test.
- The free-delivery threshold logic lives in one place; in production it would come from the BFF.

### Example 3: Production-grade

**Scenario:** DairyDay's order-tracking screen. Customers pull to refresh repeatedly, lose signal in lifts, and get late responses from old requests. The screen must never show stale data over new data, must keep the last known status when offline, must vibrate exactly once on delivery, and must restore the open order after Android kills the app in the background.

**OrderTrackingStoreDemo.java**

```java
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
    record TrackingState(Phase phase, String orderId, int requestNo, String status, Integer etaMinutes, String banner) {
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

    static Next reduce(TrackingState s, Intent intent) {
        return switch (intent) {
            case Open o -> load(new TrackingState(Phase.LOADING, o.orderId(), s.requestNo(), null, null, null));
            case Refresh r -> s.orderId() == null || s.phase() == Phase.LOADING ? new Next(s, List.of()) : load(s);
            case ConnectivityBack c -> s.phase() == Phase.OFFLINE ? load(s) : new Next(s, List.of());
            case Loaded l when l.requestNo() != s.requestNo() -> new Next(s, List.of());
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

    static final class Store {
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
        private void run(Effect e) {
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
```

1. One immutable state, with a request number and a minimal saved form for process-death restore.
2. A pure reducer returns the next state **and** the effects to run.
3. Responses for older requests are dropped by comparing request numbers.
4. The store is the only mutable holder; it renders only when the state actually changes.
5. Effects (network, vibration) run outside the reducer, and their results come back as intents.

**Run it:** `java OrderTrackingStoreDemo.java`

**Output:**

```text
  [IDLE] nothing to show
> open DD-42
  [LOADING] spinner
> pull to refresh twice (second is ignored while loading)
> server answers request 1
  [SHOWING] PACKED, 25 min
> refresh, then the network drops
  [LOADING] PACKED (refreshing)
  [OFFLINE] PACKED | You're offline. Showing last known status.
> connectivity back
  [LOADING] PACKED (refreshing)
  [SHOWING] DELIVERED, 0 min
> a very late answer to request 2 arrives
process restarted, restored order: DD-42
effects run: [FetchStatus, FetchStatus, FetchStatus, Vibrate]
```

**What to notice:**

- The second refresh during loading made no request; three fetches ran in total.
- Offline kept "PACKED" on screen with a banner instead of a blank error page.
- The late answer to request 2 ("OUT_FOR_DELIVERY") arrived after "DELIVERED" and was ignored.
- Only the order ID is saved for restore; data is re-fetched, so it's never stale.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| MVC | Simple, well known | Fat controllers; poor fit for reactive UIs |
| MVP | Testable, passive view | Lifecycle leaks; verbose view interfaces |
| MVVM | Lifecycle-aware, binding | Scattered observables; event handling awkward |
| MVI | Predictable, replayable | Boilerplate; learning curve |
| Offline-first | Fast, resilient | Sync and conflict complexity |
| Coordinator | Centralised flows | Another layer; can grow large |
| BFF | Lean payloads, client autonomy | More services to run; logic duplication |

## 8. When to Use / When NOT to Use

**Use them when:**

- screens have logic worth testing (validation, async loading, error states);
- the app runs on unreliable networks or must survive lifecycle events;
- several clients consume the same backend differently.

**Don't use them when:**

- the screen is static content; a plain view is enough;
- a prototype or throwaway demo where speed matters more than structure;
- a single client with one simple API; a BFF adds a hop for nothing.

## 9. Best Practices

1. **Views render state and forward events; no business logic in views.** *(Consensus.)*
2. **Single immutable state per screen, updated in one place.** *(Consensus for Compose/Flutter; Android official guidance.)*
3. **Local data as the source of truth for mobile apps.** *(Consensus, Android architecture guide.)*
4. **Ignore stale async results** (request IDs, cancellation, `switchMap`/`collectLatest`). *(Consensus.)*
5. **Navigation in a coordinator or graph, not screen-to-screen calls.** *(Consensus.)*
6. **Save minimal state for process death; re-fetch data.** *(Consensus on Android.)*
7. **The client team owns the BFF.** *(Opinion, Sam Newman's recommendation.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Stale response overwrites fresh data (production failure mode)**  
> **Symptom:** the tracking screen flipped from "Delivered" back to "Out for delivery"; customers called support.
> **Root cause:** two refreshes in flight; the older, slower response arrived last and was rendered.
> **Fix:** tag requests and ignore old ones in the reducer (or cancel previous calls with `collectLatest`/`switchMap`); a test that delivers responses out of order.

> 🚨 **Massive Activity / Fragment**  
> **Symptom:** a 3,000-line `CheckoutActivity` nobody wants to touch; no unit tests.
> **Root cause:** network, validation, persistence and rendering all in the screen.
> **Fix:** extract a ViewModel and repository; the screen keeps only rendering and event forwarding.

> 🚨 **One-off events stored as state**  
> **Symptom:** after rotating the phone, the "Order placed!" toast shows again, or navigation happens twice.
> **Root cause:** the event was a field in the ViewModel's state and was replayed to the new view.
> **Fix:** model effects separately (a channel consumed once) or clear the event after handling.

> 🚨 **Leaking the view**  
> **Symptom:** memory grows with each rotation; crashes "view not attached".
> **Root cause:** presenters or callbacks holding a reference to a destroyed Activity.
> **Fix:** lifecycle-aware ViewModels without view references; cancel work in `onCleared`.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| `SharingStarted.WhileSubscribed(5000)` | — | Keeps flows alive briefly after the UI stops | Avoid restarts on rotation | Wasted work if too long |
| Room / Drift DB as cache | — | Local source of truth | Offline-first apps | Migration effort |
| WorkManager sync constraints | none | Run sync when online/charging | Background sync | Delayed sync |
| `SavedStateHandle` | — | State surviving process death | Store IDs, not data | Bundle size limit (~1 MB transaction) |
| BFF timeouts per downstream | — | Bounds screen latency | Always set | Too short: degraded screens |
| HTTP cache headers from BFF | none | Client-side caching | Static or slow-changing data | Stale data |

## 12. Security Considerations

- Never trust client-side validation; the BFF and services must re-validate.
- Don't store tokens or personal data in plain local databases; use Android Keystore / EncryptedSharedPreferences, iOS Keychain, or `flutter_secure_storage`.
- Deep links are untrusted input: validate parameters and require auth before showing sensitive screens.
- The BFF must not expose internal fields; response DTOs are an allow-list.
- Logs and crash reports from state (MVI intent logs) must redact personal data.

## 13. Performance & Scalability

UI performance means not doing work on the main thread and not re-rendering more than needed. Emit state only when it changes (`distinctUntilChanged`), use stable keys in lists, and keep reducers cheap. Offline-first reads are fast because they're local; batch sync writes. A BFF reduces mobile round trips (each 100–300 ms on 4G), so aggregate in parallel server-side. Measure with Android Studio profilers, Compose recomposition counts, and Flutter DevTools.

## 14. Testing It

Reducers and ViewModels are plain code: test them with plain unit tests, delivering intents and responses in tricky orders. UI tests (Espresso, Compose testing, Flutter widget tests) then only check rendering:

**OrderTrackingStoreTest.java**

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTrackingStoreTest {

    // The reducer is a pure function: plain input/output tests, no UI, no threads.
    @Test
    void openStartsLoadingAndRequestsData() {
        var next = OrderTrackingStoreDemo.reduce(OrderTrackingStoreDemo.TrackingState.initial(), new OrderTrackingStoreDemo.Open("DD-1"));
        assertEquals(OrderTrackingStoreDemo.Phase.LOADING, next.state().phase());
        assertEquals(List.of(new OrderTrackingStoreDemo.FetchStatus("DD-1", 1)), next.effects());
    }

    @Test
    void staleResponsesAreIgnored() {
        var s = OrderTrackingStoreDemo.reduce(OrderTrackingStoreDemo.TrackingState.initial(), new OrderTrackingStoreDemo.Open("DD-1")).state();
        s = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.Loaded(1, "PACKED", 20)).state();
        s = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.Refresh()).state();   // request 2
        var afterStale = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.Loaded(1, "OLD", 99));
        assertEquals(s, afterStale.state());
        assertTrue(afterStale.effects().isEmpty());
    }

    @Test
    void offlineKeepsLastKnownDataAndReloadsWhenBack() {
        var s = OrderTrackingStoreDemo.reduce(OrderTrackingStoreDemo.TrackingState.initial(), new OrderTrackingStoreDemo.Open("DD-1")).state();
        s = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.Loaded(1, "PACKED", 20)).state();
        s = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.Refresh()).state();
        s = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.Failed(2, true, "x")).state();
        assertEquals(OrderTrackingStoreDemo.Phase.OFFLINE, s.phase());
        assertEquals("PACKED", s.status());
        var back = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.ConnectivityBack());
        assertEquals(List.of(new OrderTrackingStoreDemo.FetchStatus("DD-1", 3)), back.effects());
    }

    @Test
    void vibratesOnlyOnTheTransitionToDelivered() {
        var s = OrderTrackingStoreDemo.reduce(OrderTrackingStoreDemo.TrackingState.initial(), new OrderTrackingStoreDemo.Open("DD-1")).state();
        var first = OrderTrackingStoreDemo.reduce(s, new OrderTrackingStoreDemo.Loaded(1, "DELIVERED", 0));
        assertEquals(List.of(new OrderTrackingStoreDemo.Vibrate()), first.effects());
        var again = OrderTrackingStoreDemo.reduce(OrderTrackingStoreDemo.reduce(first.state(), new OrderTrackingStoreDemo.Refresh()).state(),
                new OrderTrackingStoreDemo.Loaded(2, "DELIVERED", 0));
        assertTrue(again.effects().isEmpty());
    }

    @Test
    void storeRendersEachDistinctStateOnce() {
        var api = new OrderTrackingStoreDemo.ScriptedApi();
        var store = new OrderTrackingStoreDemo.Store(OrderTrackingStoreDemo.TrackingState.initial(), api);
        List<OrderTrackingStoreDemo.Phase> rendered = new ArrayList<>();
        store.render(s -> rendered.add(s.phase()));
        store.dispatch(new OrderTrackingStoreDemo.Open("DD-1"));
        store.dispatch(new OrderTrackingStoreDemo.Refresh());   // ignored while loading: no render, no fetch
        api.answer(1, new OrderTrackingStoreDemo.Loaded(1, "PACKED", 20));
        assertEquals(List.of(OrderTrackingStoreDemo.Phase.IDLE, OrderTrackingStoreDemo.Phase.LOADING, OrderTrackingStoreDemo.Phase.SHOWING), rendered);
        assertTrue(api.pending.isEmpty());
    }

    @Test
    void restoreKeepsOnlyTheOrderId() {
        var restored = OrderTrackingStoreDemo.TrackingState.restore("DD-9");
        assertEquals("DD-9", restored.orderId());
        assertNull(restored.status());
    }

    @Test
    void presenterValidatesBeforeCallingTheApi() {
        var view = new MvpDemo.RecordingView();
        List<String> apiCalls = new ArrayList<>();
        new MvpDemo.LoginPresenter(view, (p, o) -> { apiCalls.add(p); return "x"; }).onLoginClicked("123", "123456");
        assertTrue(apiCalls.isEmpty());
        assertEquals(List.of("error: Enter a 10-digit phone number"), view.calls);
    }
}
```

## 15. Observability & Operations

Log screen state transitions (not raw data) in debug builds; in production, record intent names as breadcrumbs for crash reports (Firebase Crashlytics, Sentry). Track sync outbox size and age, offline time, and stale-response drops. For the BFF, trace each downstream call and measure per-screen latency (P50/P95) and payload size.

## 16. Version Notes

| Year / version | Change |
|----------------|--------|
| 1979 | MVC at Xerox PARC (Smalltalk) |
| 1996–2004 | MVP (Taligent, then Fowler's "Passive View") |
| 2005 | MVVM (Microsoft WPF) |
| 2015 | Redux, BFF named by Sam Newman |
| 2017 | Android Architecture Components (ViewModel, LiveData, Room) |
| 2018 | Flutter 1.0; BLoC popularised |
| 2021 | Jetpack Compose 1.0; Android guide recommends UDF |
| 2023+ | Kotlin Multiplatform shared ViewModels; Compose Multiplatform |

## 17. Expert Deep Dive

- **Process death is the real lifecycle test.** Rotation keeps ViewModels; background kills don't. Save IDs and user input, not fetched data, and test with "Don't keep activities".
- **State vs effects.** Things you can see (state) must be replayable; things that happen once (navigate, vibrate, toast) are effects. Mixing them is the root of many UI bugs.
- **Concurrency on the UI.** The same rules as server code apply: only the main thread touches views; async results race; cancellation and request IDs prevent stale writes.
- **Shared logic across platforms.** Kotlin Multiplatform and Dart let you share ViewModels and reducers between Android and iOS; pure reducers make this easy.
- **BFF vs GraphQL.** GraphQL lets clients pick fields from one gateway; a BFF lets client teams own aggregation code. Many companies use GraphQL as the BFF.

> 📖 **War story: the delivered order that came back**  
> DairyDay's tracking screen refreshed every 30 seconds and on pull-to-refresh. On slow networks, two requests could be in flight; when the older one returned last, the screen jumped from "Delivered" back to "Out for delivery" and sent a second "arriving soon" notification. Support tickets about "ghost deliveries" rose 4x during monsoon weeks. **Fixes:** the screen moved to MVI with request numbers, so the reducer ignored stale answers; notifications became effects emitted only on a status *transition*; and the BFF added `updatedAt` to responses so clients could reject older data even across app restarts. **Lesson:** async UI is concurrent programming; order and idempotency matter as much as on the server.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **What's MVC?** **Model** (data), **View** (display), **Controller** (input handling).
2. **Why separate UI from logic?** **Testability** and **reuse**.
3. **What does a ViewModel do?** Holds **screen state** and **actions**; survives rotation.
4. **What's offline-first?** **Local data is the source of truth**; sync later.
5. **What's a BFF?** A **backend per client type** shaping data for its screens.

**▸ 🟡 L3**

1. **MVP vs MVVM?** Presenter **calls the view** vs view **observes** ViewModel state.
2. **What's unidirectional data flow?** **Events → logic → state → view**, one direction.
3. **How do you handle one-off events?** As **effects/channels**, not persistent state.
4. **How do you avoid stale responses?** **Request IDs or cancellation** (`collectLatest`).
5. **Where does navigation logic go?** A **coordinator or navigation graph**.

**▸ 🔴 L4–L5**

1. **Design offline-first for a shopping list shared by family members.** **Local DB, outbox with idempotent ops, server merge or CRDT, conflict UI.**
2. **MVI vs MVVM for a large app?** **MVI** for complex async screens; **MVVM** with single state for simpler ones; consistency matters most.
3. **How do you survive process death?** **SavedStateHandle with IDs**, re-fetch data, **test with "Don't keep activities"**.
4. **When is a BFF a bad idea?** **Single client**, or when it accumulates **business rules** duplicated across BFFs.
5. **How do you share UI logic across Android and iOS?** **KMP/Dart shared ViewModels**, pure reducers.

**▸ 💻 Hands-on**

**Task:** Add auto-refresh to `OrderTrackingStoreDemo`: a `Tick` intent every 30 seconds that refreshes only when the screen is `SHOWING` and the order isn't delivered.

> ✅ **Solution**  
> ```java
> record Tick() implements Intent {}
> // in reduce():
> case Tick t -> s.phase() == Phase.SHOWING && !"DELIVERED".equals(s.status())
>         ? load(s)
>         : new Next(s, List.of());
> // the timer lives outside the reducer: a scheduler dispatches new Tick() every 30 s
> // while the screen is visible, and is cancelled when it isn't.
> ```

## 19. Practice

- [ ] Port `CartScreenDemo` to Android with Compose and a `StateFlow<CartUiState>`, or to Flutter with a Cubit.
    > 💡 **Hint**  
    > The state record and ViewModel logic carry over almost line for line.

- [ ] Add an intent log to the store and replay it to reproduce a bug.
    > 💡 **Hint**  
    > Record each intent before reducing; replay with `fold`.

- [ ] Add conflict handling to `OfflineFirstRepositoryDemo`: server wins for items deleted on another phone.
    > 💡 **Hint**  
    > Send a version with each outbox operation.

- [ ] Add a "Repeat last order" deep link to `CoordinatorDemo` that requires login.
    > 💡 **Hint**  
    > Reuse the pending-action mechanism used for checkout.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - View renders state, forwards events; logic in presenter/ViewModel/reducer.
> - MVC: controller → model → views. MVP: presenter → passive view. MVVM: view observes. MVI: intents → pure reducer → one state.
> - State is replayable; effects happen once.
> - Ignore stale async results; save IDs for process death.
> - Mobile: local DB is the source of truth; outbox for writes.
> - Coordinator owns navigation; BFF shapes data per client.
>
> ```java
> static Next reduce(State s, Intent i) {
>     return switch (i) {
>         case Loaded l when l.requestNo() != s.requestNo() -> new Next(s, List.of());
>         case Loaded l -> new Next(s.showing(l), List.of());
>         // ...
>     };
> }
> ```

## 21. Further Reading

- Martin Fowler, ["GUI Architectures"](https://martinfowler.com/eaaDev/uiArchs.html) and ["Presentation Model"](https://martinfowler.com/eaaDev/PresentationModel.html).
- [Android: Guide to app architecture](https://developer.android.com/topic/architecture) and [Offline-first apps](https://developer.android.com/topic/architecture/data-layer/offline-first).
- [Flutter: architecture recommendations](https://docs.flutter.dev/app-architecture).
- Sam Newman, ["Backends For Frontends"](https://samnewman.io/patterns/architectural/bff/).
- Hannes Dorfmann, ["Model-View-Intent on Android"](http://hannesdorfmann.com/android/mosby3-mvi-1/).

---

[← Previous: Enterprise Application Patterns](enterprise-application-patterns.md) · [Next: Error Handling Patterns →](error-handling-patterns.md)
