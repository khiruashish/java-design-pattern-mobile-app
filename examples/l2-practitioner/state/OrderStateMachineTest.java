import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    private OrderStateMachineDemo.OrderStore store;
    private OrderStateMachineDemo.OrderService svc;

    @BeforeEach
    void setUp() {
        store = new OrderStateMachineDemo.OrderStore();
        store.rows.put("O1", new OrderStateMachineDemo.Order("O1", OrderStateMachineDemo.S.PLACED, 1, null));
        svc = new OrderStateMachineDemo.OrderService(OrderStateMachineDemo.orderMachine(), store, () -> Instant.EPOCH);
    }

    private OrderStateMachineDemo.Order fire(String id, OrderStateMachineDemo.E type, String rider) {
        return svc.handle("O1", new OrderStateMachineDemo.Event(id, type, rider), store.rows.get("O1"));
    }

    @Test
    void happyPathRecordsHistory() {
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        fire("e2", OrderStateMachineDemo.E.DISPATCH, "R1");
        fire("e3", OrderStateMachineDemo.E.DELIVER, null);
        assertEquals(OrderStateMachineDemo.S.DELIVERED, store.rows.get("O1").state());
        assertEquals(3, store.history.size());
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThrows(IllegalStateException.class, () -> fire("e1", OrderStateMachineDemo.E.DELIVER, null));
    }

    @Test
    void guardBlocksDispatchWithoutRider() {
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        assertThrows(IllegalStateException.class, () -> fire("e2", OrderStateMachineDemo.E.DISPATCH, null));
    }

    @Test
    void duplicateEventIsANoOp() {
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        assertEquals(2, store.rows.get("O1").version());
    }

    @Test
    void staleVersionIsRejected() {
        var stale = store.rows.get("O1");
        fire("e1", OrderStateMachineDemo.E.PACK, null);
        assertThrows(IllegalStateException.class,
                () -> svc.handle("O1", new OrderStateMachineDemo.Event("e2", OrderStateMachineDemo.E.CANCEL, null), stale));
    }

    @Test
    void duplicateTransitionDefinitionFailsAtStartup() {
        var t = new OrderStateMachineDemo.Transition(OrderStateMachineDemo.S.PLACED, OrderStateMachineDemo.E.PACK, OrderStateMachineDemo.S.PACKED, e -> true, "");
        assertThrows(IllegalArgumentException.class, () -> new OrderStateMachineDemo.StateMachine(List.of(t, t)));
    }
}
