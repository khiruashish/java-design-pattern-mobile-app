import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutApiTest {

    int created;
    boolean failing;
    CheckoutApiDemo.CheckoutApi api;

    @BeforeEach
    void setUp() {
        created = 0; failing = false;
        api = new CheckoutApiDemo.CheckoutApi(body -> {
            if (failing) throw new IllegalStateException("down");
            if (body.contains("qty=0")) throw new IllegalArgumentException("bad qty");
            created++;
            return new CheckoutApiDemo.Placed("FC-" + created, 4500);
        });
    }

    static Map<String, String> key(String k) { return Map.of("Idempotency-Key", k); }

    @Test
    void retryWithSameKeyReplaysWithoutCreatingAgain() {
        var first = api.post("v2", key("a"), "x");
        var second = api.post("v2", key("a"), "x");
        assertEquals(first.body(), second.body());
        assertEquals("true", second.headers().get("Idempotent-Replayed"));
        assertEquals(1, created);
    }

    @Test
    void sameKeyDifferentBodyIsRejected() {
        api.post("v2", key("a"), "x");
        assertEquals(422, api.post("v2", key("a"), "y").status());
    }

    @Test
    void sameKeyDifferentVersionIsADifferentRequest() {
        api.post("v2", key("a"), "x");
        assertEquals(422, api.post("v1", key("a"), "x").status());
    }

    @Test
    void concurrentDuplicateGets409() {
        int[] status = {0};
        api.whileProcessing = () -> status[0] = api.post("v2", key("a"), "x").status();
        assertEquals(201, api.post("v2", key("a"), "x").status());
        assertEquals(409, status[0]);
        assertEquals(1, created);
    }

    @Test
    void validationFailuresAreStoredAndReplayed() {
        assertEquals(422, api.post("v2", key("a"), "qty=0").status());
        assertEquals("true", api.post("v2", key("a"), "qty=0").headers().get("Idempotent-Replayed"));
    }

    @Test
    void serverErrorsReleaseTheKeySoTheClientCanRetry() {
        failing = true;
        assertEquals(503, api.post("v2", key("a"), "x").status());
        failing = false;
        assertEquals(201, api.post("v2", key("a"), "x").status());
    }

    @Test
    void missingKeyIsAProblemDetailsError() {
        var r = api.post("v2", Map.of(), "x");
        assertEquals(400, r.status());
        assertEquals("application/problem+json", r.headers().get("Content-Type"));
    }

    @Test
    void versionsRenderTheSameOrderDifferently() {
        assertTrue(api.post("v1", key("a"), "x").body().contains("\"total\":45.00"));
        assertTrue(api.post("v2", key("b"), "x").body().contains("\"amountPaise\":4500"));
    }
}
