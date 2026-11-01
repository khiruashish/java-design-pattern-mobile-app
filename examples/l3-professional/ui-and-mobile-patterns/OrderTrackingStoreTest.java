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
