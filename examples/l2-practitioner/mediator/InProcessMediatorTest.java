import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InProcessMediatorTest {

    @Test
    void requestGoesToItsSingleHandler() {
        var m = new InProcessMediatorDemo.Mediator().handle(InProcessMediatorDemo.GetOrderStatus.class, q -> "status of " + q.orderId());
        assertEquals("status of FC-1", m.send(new InProcessMediatorDemo.GetOrderStatus("FC-1")));
    }

    @Test
    void behavioursWrapInRegistrationOrder() {
        List<String> log = new ArrayList<>();
        var m = new InProcessMediatorDemo.Mediator()
                .behaviour((q, next) -> { log.add("outer"); return next.apply(q); })
                .behaviour((q, next) -> { log.add("inner"); return next.apply(q); })
                .handle(InProcessMediatorDemo.GetOrderStatus.class, q -> { log.add("handler"); return "x"; });
        m.send(new InProcessMediatorDemo.GetOrderStatus("FC-1"));
        assertEquals(List.of("outer", "inner", "handler"), log);
    }

    @Test
    void oneFailingListenerDoesNotStopOthers() {
        List<String> got = new ArrayList<>();
        var m = new InProcessMediatorDemo.Mediator()
                .on(InProcessMediatorDemo.OrderPlaced.class, n -> { throw new IllegalStateException("down"); })
                .on(InProcessMediatorDemo.OrderPlaced.class, n -> got.add(n.orderId()));
        m.publish(new InProcessMediatorDemo.OrderPlaced("FC-1", "asha"));
        assertEquals(List.of("FC-1"), got);
        assertEquals(1, m.failures.size());
    }

    @Test
    void startupValidationFindsMissingHandlers() {
        assertThrows(IllegalStateException.class,
                () -> new InProcessMediatorDemo.Mediator().validate(List.of(InProcessMediatorDemo.PlaceOrder.class)));
    }
}
