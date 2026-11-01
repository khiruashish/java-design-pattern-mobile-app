import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestPipelineTest {

    record Recording(String name, int order, List<String> log) implements RequestPipelineDemo.Handler {
        public RequestPipelineDemo.Response handle(RequestPipelineDemo.Request r, RequestPipelineDemo.Next next) {
            log.add(name + " before");
            var resp = next.call(r);
            log.add(name + " after");
            return resp;
        }
    }

    private static RequestPipelineDemo.Request get(Map<String, String> headers) {
        return new RequestPipelineDemo.Request("GET", "/x", headers, Map.of());
    }

    @Test
    void runsHandlersInDeclaredOrderRegardlessOfListOrder() {
        List<String> log = new ArrayList<>();
        var p = new RequestPipelineDemo.Pipeline(List.of(new Recording("b", 2, log), new Recording("a", 1, log)),
                r -> new RequestPipelineDemo.Response(200, "ok"));
        p.serve(get(Map.of()));
        assertEquals(List.of("a before", "b before", "b after", "a after"), log);
    }

    @Test
    void authShortCircuitsBeforeTheEndpoint() {
        int[] endpointCalls = {0};
        var p = new RequestPipelineDemo.Pipeline(List.of(new RequestPipelineDemo.Auth(Map.of())),
                r -> { endpointCalls[0]++; return new RequestPipelineDemo.Response(200, "ok"); });
        assertEquals(401, p.serve(get(Map.of())).status());
        assertEquals(0, endpointCalls[0]);
    }

    @Test
    void exceptionsBecome500() {
        var p = new RequestPipelineDemo.Pipeline(List.of(), r -> { throw new IllegalStateException("x"); });
        assertEquals(500, p.serve(get(Map.of())).status());
    }

    @Test
    void duplicateOrdersAreRejected() {
        List<String> log = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> new RequestPipelineDemo.Pipeline(
                List.of(new Recording("a", 1, log), new Recording("b", 1, log)), r -> null));
    }

    @Test
    void idempotencyReplaysPostResponses() {
        int[] created = {0};
        var p = new RequestPipelineDemo.Pipeline(List.of(new RequestPipelineDemo.Idempotency(new HashMap<>())),
                r -> new RequestPipelineDemo.Response(201, "order " + (++created[0])));
        var post = new RequestPipelineDemo.Request("POST", "/orders", Map.of("Idempotency-Key", "k"), Map.of());
        assertEquals(p.serve(post), p.serve(post));
        assertEquals(1, created[0]);
    }
}
