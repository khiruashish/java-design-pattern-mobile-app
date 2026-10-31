import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AppContainerTest {

    @Test
    void singletonsAreSharedAndPerRequestObjectsAreFresh() {
        try (var app = AppContainerDemo.production().start()) {
            var first = app.resolve(AppContainerDemo.CheckoutHandler.class);
            var second = app.resolve(AppContainerDemo.CheckoutHandler.class);
            assertNotSame(first, second);
            assertNotSame(first.request, second.request);
            assertSame(first.service, second.service);
        }
    }

    @Test
    void testCanOverrideOneBindingWithoutTouchingTheRest() {
        try (var app = AppContainerDemo.production()
                .bind(AppContainerDemo.Payments.class, AppContainerDemo.Scope.SINGLETON, List.of(), r -> (AppContainerDemo.Payments) c -> "FAKE " + c)
                .start()) {
            assertTrue(app.resolve(AppContainerDemo.CheckoutHandler.class).handle(100).endsWith("FAKE 100"));
        }
    }

    @Test
    void startupReportsMissingBindingsBeforeAnyRequest() {
        var c = new AppContainerDemo.Container()
                .bind(AppContainerDemo.OrderRepository.class, AppContainerDemo.Scope.SINGLETON, List.of(AppContainerDemo.DbPool.class), r -> null);
        var e = assertThrows(IllegalStateException.class, c::start);
        assertTrue(e.getMessage().contains("OrderRepository needs DbPool"));
    }

    @Test
    void closesSingletonsInReverseCreationOrder() {
        var app = AppContainerDemo.production().start();
        app.close();
        assertEquals(List.of("closed Payments", "closed DbPool"), app.log.stream().filter(l -> l.startsWith("closed")).toList());
    }
}
