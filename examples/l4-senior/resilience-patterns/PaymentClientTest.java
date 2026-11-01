import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class PaymentClientTest {

    static PaymentClientDemo.Reply ok() { return new PaymentClientDemo.Reply(200, 100); }
    static PaymentClientDemo.Reply unavailable() { return new PaymentClientDemo.Reply(503, 50); }

    @Test
    void retriesUseTheSameIdempotencyKey() {
        List<PaymentClientDemo.Attempt> attempts = new ArrayList<>();
        var client = new PaymentClientDemo.PaymentClient(a -> {
            attempts.add(a);
            return a.number() == 1 ? new PaymentClientDemo.Reply(200, 5000) : ok();
        });
        assertEquals("paid", client.charge("FC-1").result());
        assertEquals(2, attempts.size());
        assertEquals(attempts.get(0).idempotencyKey(), attempts.get(1).idempotencyKey());
    }

    @Test
    void declinedCardIsNotRetriedAndNotAFailure() {
        int[] calls = {0};
        var client = new PaymentClientDemo.PaymentClient(a -> { calls[0]++; return new PaymentClientDemo.Reply(402, 50); });
        for (int i = 0; i < 8; i++) assertEquals("declined: card refused", client.charge("FC-" + i).result());
        assertEquals(8, calls[0]);
        assertEquals("CLOSED", client.state());
    }

    @Test
    void breakerOpensAndRejectsWithoutCallingPayLink() {
        int[] calls = {0};
        var client = new PaymentClientDemo.PaymentClient(a -> { calls[0]++; return unavailable(); });
        for (int i = 0; i < 4; i++) client.charge("FC-" + i);
        assertEquals("OPEN", client.state());
        int before = calls[0];
        var outcome = client.charge("FC-99");
        assertEquals(0, outcome.remoteCalls());
        assertEquals(before, calls[0]);
        assertTrue(outcome.result().startsWith("pending"));
    }

    @Test
    void halfOpenTrialClosesOnSuccessAndReopensOnFailure() {
        boolean[] up = {false};
        Function<PaymentClientDemo.Attempt, PaymentClientDemo.Reply> paylink = a -> up[0] ? ok() : unavailable();
        var client = new PaymentClientDemo.PaymentClient(paylink);
        for (int i = 0; i < 4; i++) client.charge("FC-" + i);
        client.now += 31_000;
        assertEquals("HALF_OPEN", client.state());
        client.charge("FC-trial-1");
        assertEquals("OPEN", client.state());
        client.now += 31_000;
        up[0] = true;
        assertEquals("paid", client.charge("FC-trial-2").result());
        assertEquals("CLOSED", client.state());
    }

    @Test
    void retryBudgetCapsRetriesDuringAnOutage() {
        int[] calls = {0};
        var client = new PaymentClientDemo.PaymentClient(a -> { calls[0]++; return new PaymentClientDemo.Reply(200, 5000); });
        for (int i = 0; i < 3; i++) client.charge("FC-" + i);    // timeouts: 3 requests, but not 9 calls
        assertTrue(calls[0] < 6, "calls were " + calls[0]);
        assertTrue(client.metrics.get("retry-skipped") >= 1);
    }
}
