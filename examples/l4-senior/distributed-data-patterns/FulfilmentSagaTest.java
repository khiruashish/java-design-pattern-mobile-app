import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FulfilmentSagaTest {

    FulfilmentSagaDemo.SagaStore store;
    FulfilmentSagaDemo.Outbox outbox;
    FulfilmentSagaDemo.Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        store = new FulfilmentSagaDemo.SagaStore();
        outbox = new FulfilmentSagaDemo.Outbox();
        orchestrator = new FulfilmentSagaDemo.Orchestrator(store, outbox);
    }

    FulfilmentSagaDemo.Reply ok(String id, FulfilmentSagaDemo.Step step) { return new FulfilmentSagaDemo.Reply(id, step, true); }

    @Test
    void happyPathCompletesAndSendsEachCommandOnce() {
        orchestrator.start("FC-1", 0);
        for (var step : FulfilmentSagaDemo.Step.values()) orchestrator.onReply(ok("FC-1", step), 1);
        assertEquals("COMPLETED", store.rows.get("FC-1").status());
        assertEquals(List.of("RESERVE_STOCK FC-1", "CHARGE_PAYMENT FC-1", "BOOK_RIDER FC-1"), outbox.commands);
    }

    @Test
    void duplicateReplyDoesNotAdvanceTwice() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 1);
        String second = orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 2);
        assertTrue(second.startsWith("ignored"));
        assertEquals(2, outbox.commands.size());
    }

    @Test
    void failureCompensatesCompletedStepsInReverse() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 1);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.CHARGE_PAYMENT), 2);
        orchestrator.onReply(new FulfilmentSagaDemo.Reply("FC-1", FulfilmentSagaDemo.Step.BOOK_RIDER, false), 3);
        assertEquals("CANCELLED", store.rows.get("FC-1").status());
        assertEquals(List.of("RefundPayment FC-1", "ReleaseStock FC-1"), outbox.commands.subList(3, 5));
    }

    @Test
    void failureOnFirstStepNeedsNoCompensation() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(new FulfilmentSagaDemo.Reply("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK, false), 1);
        assertEquals(List.of("RESERVE_STOCK FC-1"), outbox.commands);
    }

    @Test
    void stepTimeoutCancelsAndLateSuccessIsCompensated() {
        orchestrator.start("FC-1", 0);
        assertTrue(orchestrator.checkTimeouts(60_000).isEmpty());
        assertEquals(1, orchestrator.checkTimeouts(60_001).size());
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 70_000);
        assertEquals("ReleaseStock FC-1", outbox.commands.get(outbox.commands.size() - 1));
    }

    @Test
    void anotherOrchestratorInstanceContinuesFromTheStore() {
        orchestrator.start("FC-1", 0);
        orchestrator.onReply(ok("FC-1", FulfilmentSagaDemo.Step.RESERVE_STOCK), 1);
        var replacement = new FulfilmentSagaDemo.Orchestrator(store, outbox);
        replacement.onReply(ok("FC-1", FulfilmentSagaDemo.Step.CHARGE_PAYMENT), 2);
        replacement.onReply(ok("FC-1", FulfilmentSagaDemo.Step.BOOK_RIDER), 3);
        assertEquals("COMPLETED", store.rows.get("FC-1").status());
    }
}
