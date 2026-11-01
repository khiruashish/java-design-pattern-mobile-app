# Messaging and Integration Patterns

L4 🟠 Senior / Lead · ⏱ 35 min read · Prerequisites: [Distributed Data Patterns](distributed-data-patterns.md), [Observer](../l1-foundations/observer.md)

> 📌 **TL;DR**  
> Messaging connects systems through channels instead of direct calls, so senders and receivers don't need to be up at the same time or know each other. A **queue** (point-to-point) gives each message to one receiver; a **topic** (publish-subscribe) gives a copy to every subscriber. **Competing consumers** scale a queue, and **partitioning by key** keeps per-entity ordering. A **content-based router** sends each message to the right channel. A **dead letter channel** parks messages that keep failing, with the reason, so they neither block nor vanish. A **message translator** converts external formats into a canonical model at the edge. **Request-reply** over messaging uses a **correlation ID** and a reply-to channel. These are the Enterprise Integration Patterns (Hohpe and Woolf), implemented today by Kafka, RabbitMQ, Spring Integration and Apache Camel.

## 1. What It Is

**Plain English.** Instead of FreshCart's services calling each other and waiting, they drop messages into channels, like letters into post boxes. These patterns describe the post boxes (queues and topics), how many workers share the sorting, how letters get routed, what happens to letters that can't be delivered, how foreign letters get translated, and how to get an answer back.

**Definition.**

| Pattern | Purpose |
|---------|---------|
| Point-to-point channel (queue) | Each message processed by exactly one receiver |
| Publish-subscribe channel (topic) | Each message delivered to all subscribers |
| Competing consumers | Several receivers on one channel for throughput |
| Message router / content-based router | Choose the destination channel per message |
| Dead letter channel | Destination for messages that can't be processed |
| Message translator | Convert between message formats |
| Request-reply with correlation ID | Get a response over asynchronous channels |

## 2. Why It Exists

Synchronous calls couple availability (the callee must be up), throughput (the caller waits) and knowledge (the caller must know every callee). Messaging decouples all three: a broker buffers spikes, consumers process at their own pace, and new subscribers join without changing the producer. Hohpe and Woolf catalogued 65 patterns in *Enterprise Integration Patterns* (2003) from years of integration projects; the vocabulary still describes Kafka and RabbitMQ systems today.

## 3. Mental Model / Analogy

**Analogy.** A post office. A personal letter goes to one addressee (queue); a newsletter goes to every subscriber (topic). Several clerks share the counter (competing consumers), but each customer's packages stay with one clerk (partitioning by key). A sorter reads the address and picks the bag (router). Undeliverable mail goes to the dead letter office with a note. Foreign forms are translated at the border. A reply envelope carries your reference number so you know which letter it answers (correlation ID).

**Technical model.** Producers write to channels in a broker; consumers read with at-least-once delivery and acknowledge (or commit offsets) after processing; routing, translation and error handling are small, composable steps (pipes and filters).

## 4. How It Works

> 📊 *Diagram: open this page on the website to see it drawn.*

> 📊 *Diagram: open this page on the website to see it drawn.*

## 5. Subtopics

### Point-to-point and pub-sub

**Description.** A **point-to-point channel** (queue) delivers each message to one consumer: use it for commands and work items ("pick order FC-1"). A **publish-subscribe channel** (topic) delivers each message to every subscriber: use it for events ("OrderPlaced"). In Kafka, a topic plus consumer groups gives both: each group gets every message, and within a group each partition goes to one consumer.

**Advantages / features.** Producers don't know consumers; new subscribers join without changes; work is spread across receivers.

**PointToPointPubSubDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Point-to-point: a queue delivers each message to exactly one receiver (work distribution).
 * Publish-subscribe: a topic delivers a copy of each message to every subscriber (event broadcast).
 */
public class PointToPointPubSubDemo {

    static final class Queue {
        private final List<Consumer<String>> receivers = new ArrayList<>(); private int next;
        void addReceiver(Consumer<String> r) { receivers.add(r); }
        void send(String m) { receivers.get(next++ % receivers.size()).accept(m); }
    }

    static final class Topic {
        private final Map<String, Consumer<String>> subscribers = new LinkedHashMap<>();
        void subscribe(String name, Consumer<String> s) { subscribers.put(name, s); }
        void publish(String m) { subscribers.values().forEach(s -> s.accept(m)); }
    }

    public static void main(String[] args) {
        Queue pickTasks = new Queue();
        pickTasks.addReceiver(m -> System.out.println("  picker Ravi got " + m));
        pickTasks.addReceiver(m -> System.out.println("  picker Meena got " + m));
        System.out.println("queue: pick tasks");
        for (String o : List.of("pick FC-1", "pick FC-2", "pick FC-3")) pickTasks.send(o);

        Topic orderPlaced = new Topic();
        orderPlaced.subscribe("loyalty", m -> System.out.println("  loyalty: award points for " + m));
        orderPlaced.subscribe("analytics", m -> System.out.println("  analytics: count " + m));
        orderPlaced.subscribe("notifications", m -> System.out.println("  notifications: confirm " + m));
        System.out.println("topic: OrderPlaced");
        orderPlaced.publish("FC-1");
    }
}
```

1. A queue hands each message to one receiver.
2. A topic hands a copy to every subscriber.
3. Adding a subscriber doesn't change the publisher.

**Run it:** `java PointToPointPubSubDemo.java`

```text
queue: pick tasks
  picker Ravi got pick FC-1
  picker Meena got pick FC-2
  picker Ravi got pick FC-3
topic: OrderPlaced
  loyalty: award points for FC-1
  analytics: count FC-1
  notifications: confirm FC-1
```

| | Queue | Topic |
|-|-------|-------|
| Delivery | One receiver per message | Every subscriber |
| Typical content | Commands, tasks | Events, notifications |
| RabbitMQ | Queue | Fanout/topic exchange + queue per subscriber |
| Kafka | Topic + one consumer group | Topic + one group per subscriber |
| JMS | `Queue` | `Topic` |

**Use** queues for work, topics for facts. **Avoid** using a topic as a command bus ("everyone, maybe do this").
**Real-world Java:** Spring Kafka `@KafkaListener(groupId=...)`, Spring AMQP `@RabbitListener`, Jakarta Messaging.
**Best practice:** name events in the past tense (`OrderPlaced`) and commands as imperatives (`PickOrder`). **Gotcha:** a subscriber that's offline misses messages on non-durable topics; use durable subscriptions or log-based brokers.

### Competing consumers

**Description.** Several consumers read from the same channel; the broker gives each message to one of them. This scales throughput horizontally. Ordering is lost unless messages are **partitioned by key**: all messages for one order go to the same partition and therefore the same consumer.

**Advantages / features.** Horizontal scaling; failover when a consumer dies; load spreads automatically.

**CompetingConsumersDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Competing consumers: several consumers share one queue to scale throughput. Plain round-robin breaks per-order
 * ordering when consumers run at different speeds; partitioning by key keeps each order on one consumer.
 */
public class CompetingConsumersDemo {

    record Msg(String orderId, int seq, String status) {}

    /** Returns the order in which statuses are applied, given an assignment rule and consumer speeds. */
    static Map<String, List<String>> simulate(List<Msg> msgs, int consumers, boolean byKey, int[] msPerMessage) {
        record Done(long at, Msg m) {}
        long[] freeAt = new long[consumers];
        List<Done> done = new ArrayList<>();
        for (int i = 0; i < msgs.size(); i++) {
            Msg m = msgs.get(i);
            int c = byKey ? Math.floorMod(m.orderId().hashCode(), consumers) : i % consumers;
            freeAt[c] = Math.max(freeAt[c], i) + msPerMessage[c];
            done.add(new Done(freeAt[c], m));
        }
        done.sort((a, b) -> Long.compare(a.at(), b.at()));
        Map<String, List<String>> applied = new TreeMap<>();
        done.forEach(d -> applied.computeIfAbsent(d.m().orderId(), k -> new ArrayList<>()).add(d.m().status()));
        return applied;
    }

    public static void main(String[] args) {
        List<Msg> msgs = List.of(
                new Msg("FC-1", 1, "PLACED"), new Msg("FC-1", 2, "PACKED"), new Msg("FC-2", 1, "PLACED"),
                new Msg("FC-1", 3, "DELIVERED"), new Msg("FC-2", 2, "PACKED"), new Msg("FC-2", 3, "DELIVERED"));
        int[] speeds = {50, 5};                                                                   // consumer 0 is slow (GC pause, cold cache)
        System.out.println("round-robin: " + simulate(msgs, 2, false, speeds));
        System.out.println("by order key: " + simulate(msgs, 2, true, speeds));
    }
}
```

1. Round-robin spreads messages evenly; key hashing pins each order to one consumer.
2. Each consumer processes one message at a time at its own speed.
3. With a slow consumer, round-robin applies statuses out of order; key partitioning doesn't.

**Run it:** `java CompetingConsumersDemo.java`

```text
round-robin: {FC-1=[PACKED, DELIVERED, PLACED], FC-2=[DELIVERED, PLACED, PACKED]}
by order key: {FC-1=[PLACED, PACKED, DELIVERED], FC-2=[PLACED, PACKED, DELIVERED]}
```

| Concern | Kafka | RabbitMQ |
|---------|-------|----------|
| Unit of parallelism | Partition | Queue consumer + prefetch |
| Ordering | Per partition | Per queue, lost with many consumers |
| Max useful consumers | Number of partitions | Unbounded |
| Key-based ordering | Partition by key | Consistent-hash exchange |

**Use** for scaling any queue. **Avoid** more consumers than partitions in Kafka; extras sit idle.
**Real-world Java:** Spring Kafka `concurrency`, Spring AMQP `concurrentConsumers` and `prefetchCount`.
**Best practice:** choose the key that defines your ordering need (order ID, customer ID). **Gotcha:** a hot key (one huge customer) overloads one partition; consider a composite key.

### Message router and content-based router

**Description.** A **message router** consumes from one channel and sends each message to one of several channels. A **content-based router** decides using the message content (payment type, city, size). Related patterns: message filter (drop), recipient list (send to several), splitter and aggregator.

**Advantages / features.** Producers stay simple; routing rules live in one place and can change without touching consumers.

**ContentBasedRouterDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Content-based router: inspect each message and send it to the right channel. The rules are data, so a new route
 * doesn't change the producers or the other consumers.
 */
public class ContentBasedRouterDemo {

    record Order(String id, String payment, boolean express, int items, String city) {}
    record Route(String channel, Predicate<Order> when) {}

    static final class Router {
        private final List<Route> routes = new ArrayList<>();
        private final String fallbackChannel;
        final Map<String, List<String>> channels = new TreeMap<>();
        Router(String fallbackChannel) { this.fallbackChannel = fallbackChannel; }
        Router when(String channel, Predicate<Order> rule) { routes.add(new Route(channel, rule)); return this; }
        void route(Order o) {
            String channel = routes.stream().filter(r -> r.when().test(o)).map(Route::channel)
                    .findFirst().orElse(fallbackChannel);
            channels.computeIfAbsent(channel, k -> new ArrayList<>()).add(o.id());
        }
    }

    public static void main(String[] args) {
        Router router = new Router("standard-fulfilment")
                .when("unsupported-city", o -> !List.of("BLR", "PUN", "DEL").contains(o.city()))
                .when("bulk-orders", o -> o.items() > 40)
                .when("express-fulfilment", Order::express)
                .when("cod-verification", o -> o.payment().equals("COD"));
        List.of(new Order("FC-1", "UPI", true, 5, "BLR"), new Order("FC-2", "COD", false, 3, "PUN"),
                new Order("FC-3", "CARD", false, 60, "BLR"), new Order("FC-4", "UPI", false, 2, "GOA"),
                new Order("FC-5", "COD", true, 4, "DEL"), new Order("FC-6", "UPI", false, 7, "DEL")).forEach(router::route);
        router.channels.forEach((channel, ids) -> System.out.println(channel + ": " + ids));
    }
}
```

1. Routes are an ordered list of rules: data, not code paths in every producer.
2. The first matching rule wins; anything else goes to a default channel.
3. Rule order matters: unsupported cities are caught before any other route.

**Run it:** `java ContentBasedRouterDemo.java`

```text
bulk-orders: [FC-3]
cod-verification: [FC-2]
express-fulfilment: [FC-1, FC-5]
standard-fulfilment: [FC-6]
unsupported-city: [FC-4]
```

| Routing pattern | Effect |
|-----------------|--------|
| Content-based router | One destination chosen per message |
| Message filter | Drop unwanted messages |
| Recipient list | Several destinations per message |
| Splitter / aggregator | One message into many / many into one |
| Routing slip | Route carried in the message itself |

**Use** when different message types need different processing paths. **Avoid** routers that grow into hidden business-logic engines.
**Real-world Java:** Spring Integration `@Router`, Apache Camel `choice().when(...)`, RabbitMQ topic exchanges (routing by key).
**Best practice:** always have a default route and count messages per route. **Gotcha:** overlapping rules depend on order; test the rule table, not just individual rules.

### Dead letter channel

**Description.** When a message can't be processed after a limited number of attempts (poison message, bug, invalid data), move it to a dead letter channel with metadata (reason, attempts, original topic and offset). The main flow continues. After a fix, operators **redrive** dead letters back.

**Advantages / features.** One bad message doesn't block a queue or partition; nothing is silently dropped; failures are inspectable.

**DeadLetterChannelDemo.java**

```java
import java.util.ArrayList;
import java.util.List;

/**
 * Dead letter channel: a message that keeps failing is moved aside, with the reason and attempt count, so it
 * doesn't block the queue or get lost. After a fix, dead letters are redriven.
 */
public class DeadLetterChannelDemo {

    record Message(String id, String body, int attempts) {}
    record DeadLetter(Message message, String reason) {}

    static final int MAX_ATTEMPTS = 3;
    static boolean fixedParser = false;

    static void process(Message m) {
        if (m.body().contains("qty=-") && !fixedParser) throw new IllegalArgumentException("negative quantity");
        if (m.body().contains("qty=-")) return;   // fixed parser treats negative quantity as a return
    }

    static void consume(List<Message> queue, List<DeadLetter> dlq, List<String> log) {
        while (!queue.isEmpty()) {
            Message m = queue.remove(0);
            try {
                process(m);
                log.add("processed " + m.id() + " after " + (m.attempts() + 1) + " attempt(s)");
            } catch (RuntimeException e) {
                Message retried = new Message(m.id(), m.body(), m.attempts() + 1);
                if (retried.attempts() < MAX_ATTEMPTS) queue.add(retried);
                else { dlq.add(new DeadLetter(retried, e.getMessage())); log.add("dead-lettered " + m.id() + ": " + e.getMessage()); }
            }
        }
    }

    public static void main(String[] args) {
        List<Message> queue = new ArrayList<>(List.of(
                new Message("m1", "sku=MILK-1L qty=2", 0), new Message("m2", "sku=CURD-500G qty=-1", 0), new Message("m3", "sku=EGGS-12 qty=1", 0)));
        List<DeadLetter> dlq = new ArrayList<>();
        List<String> log = new ArrayList<>();
        consume(queue, dlq, log);
        log.forEach(System.out::println);
        dlq.forEach(d -> System.out.println("DLQ: " + d.message().id() + " attempts=" + d.message().attempts() + " reason=" + d.reason()));

        fixedParser = true;
        log.clear();
        dlq.forEach(d -> queue.add(new Message(d.message().id(), d.message().body(), 0)));
        dlq.clear();
        consume(queue, dlq, log);
        System.out.println("after fix and redrive: " + log + ", DLQ size " + dlq.size());
    }
}
```

1. Failed messages are retried until the attempt limit.
2. Then they're dead-lettered with the reason, and the queue moves on.
3. After the parser is fixed, dead letters are redriven and processed.

**Run it:** `java DeadLetterChannelDemo.java`

```text
processed m1 after 1 attempt(s)
processed m3 after 1 attempt(s)
dead-lettered m2: negative quantity
DLQ: m2 attempts=3 reason=negative quantity
after fix and redrive: [processed m2 after 1 attempt(s)], DLQ size 0
```

| Failure type | Handling |
|--------------|----------|
| Transient (timeout, 503) | Retry with backoff (retry topic or delayed redelivery) |
| Poison (unparseable) | Dead letter immediately or after few attempts |
| Business rejection | Not an error: publish a rejection event |
| Bug in consumer | Dead letter, fix, redrive |

**Use** on every consumer. **Avoid** infinite redelivery loops (RabbitMQ requeue without limit, Kafka consumer stuck retrying one offset).
**Real-world Java:** Spring Kafka `DeadLetterPublishingRecoverer` and `@RetryableTopic`, RabbitMQ dead-letter exchanges (`x-dead-letter-exchange`), SQS redrive policies.
**Best practice:** alert on DLQ growth and have a documented redrive tool. **Gotcha:** a DLQ nobody watches is a slow data-loss bug.

### Message translator

**Description.** Converts messages from one format to another. At system edges, translate each external format into a **canonical data model**, so internal consumers see one shape. This is the messaging form of the Adapter pattern and of DDD's anti-corruption layer.

**Advantages / features.** Internal code is insulated from partner formats; adding a partner adds one translator.

**MessageTranslatorDemo.java**

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Message translator: convert each partner's format into one canonical model at the edge, so internal consumers
 * only ever see one shape. N formats need N translators, not N x M.
 */
public class MessageTranslatorDemo {

    record PriceUpdate(String sku, long pricePaise, String effectiveDate) {}

    static final Map<String, Function<String, PriceUpdate>> TRANSLATORS = new LinkedHashMap<>();
    static {
        TRANSLATORS.put("dairyday-csv", line -> {
            String[] f = line.split(",");
            return new PriceUpdate(f[0].trim(), Math.round(Double.parseDouble(f[1].trim()) * 100), f[2].trim());
        });
        TRANSLATORS.put("greenvalley-kv", text -> {
            Map<String, String> kv = new LinkedHashMap<>();
            for (String pair : text.split(";")) { String[] p = pair.split("="); kv.put(p[0].trim(), p[1].trim()); }
            String[] dmy = kv.get("from").split("/");
            return new PriceUpdate("GV-" + kv.get("item"), Long.parseLong(kv.get("paise")), dmy[2] + "-" + dmy[1] + "-" + dmy[0]);
        });
    }

    public static void main(String[] args) {
        List<String[]> inbound = List.of(
                new String[] {"dairyday-csv", "MILK-1L, 68.50, 2026-11-02"},
                new String[] {"greenvalley-kv", "item=TOMATO-1KG; paise=3200; from=02/11/2026"});
        for (String[] in : inbound) System.out.println(in[0] + " -> " + TRANSLATORS.get(in[0]).apply(in[1]));
    }
}
```

1. The canonical model every internal consumer understands.
2. One translator per external format, registered by format name.
3. Translators also normalise units, identifiers and dates.

**Run it:** `java MessageTranslatorDemo.java`

```text
dairyday-csv -> PriceUpdate[sku=MILK-1L, pricePaise=6850, effectiveDate=2026-11-02]
greenvalley-kv -> PriceUpdate[sku=GV-TOMATO-1KG, pricePaise=3200, effectiveDate=2026-11-02]
```

| Translation level | Example |
|-------------------|---------|
| Transport | HTTP webhook to Kafka message |
| Data format | CSV, XML, key-value to JSON/Avro |
| Data types | Rupees with decimals to paise; DD/MM/YYYY to ISO dates |
| Data structure | Partner item codes to FreshCart SKUs |

**Use** at every boundary with external formats. **Avoid** letting partner formats travel deep into your system.
**Real-world Java:** Spring Integration transformers, Camel `unmarshal()`/`marshal()`, MapStruct, Jackson.
**Best practice:** keep translators pure functions with tests built from real partner samples. **Gotcha:** a "canonical model" for the whole company becomes a God model; keep it per bounded context.

### Request-reply and correlation

**Description.** When an answer is needed over messaging, the requester sends a message with a **reply-to** channel and a **correlation ID**; the replier sends the response to that channel with the same ID. The requester matches replies to pending requests and times out ones that never come back.

**Advantages / features.** Request-response semantics with broker buffering and load-levelling; many requests in flight on one channel.

**RequestReplyDemo.java**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-reply over messaging: the requester sends a correlation ID and a reply-to address; the replier copies the
 * correlation ID into its reply. Replies can arrive in any order, late, or not at all.
 */
public class RequestReplyDemo {

    record Request(String correlationId, String replyTo, String body) {}
    record Reply(String correlationId, String body) {}

    static final class Requester {
        private final Map<String, Long> pending = new LinkedHashMap<>();
        private int seq;
        final List<String> log = new ArrayList<>();
        Request send(String body, long now) {
            String id = "corr-" + ++seq;
            pending.put(id, now);
            return new Request(id, "replies.checkout-7", body);
        }
        void onReply(Reply r) {
            if (pending.remove(r.correlationId()) == null) log.add("unknown or late reply " + r.correlationId() + " dropped");
            else log.add(r.correlationId() + " -> " + r.body());
        }
        void expire(long now, long timeoutMs) {
            pending.entrySet().removeIf(e -> {
                boolean old = now - e.getValue() > timeoutMs;
                if (old) log.add(e.getKey() + " timed out");
                return old;
            });
        }
    }

    public static void main(String[] args) {
        Requester checkout = new Requester();
        Request a = checkout.send("quote delivery fee BLR 560001", 0);
        Request b = checkout.send("quote delivery fee BLR 560102", 0);
        Request c = checkout.send("quote delivery fee PUN 411001", 0);
        System.out.println("sent " + List.of(a.correlationId(), b.correlationId(), c.correlationId()) + " with replyTo=" + a.replyTo());
        checkout.onReply(new Reply(b.correlationId(), "Rs 25"));
        checkout.onReply(new Reply(a.correlationId(), "Rs 0 (free above Rs 199)"));
        checkout.expire(5_000, 3_000);
        checkout.onReply(new Reply(c.correlationId(), "Rs 30"));
        checkout.log.forEach(System.out::println);
    }
}
```

1. Pending requests are tracked by correlation ID.
2. Each request names where to reply.
3. Replies are matched by ID, in whatever order they arrive; unknown or late ones are dropped.

**Run it:** `java RequestReplyDemo.java`

```text
sent [corr-1, corr-2, corr-3] with replyTo=replies.checkout-7
corr-2 -> Rs 25
corr-1 -> Rs 0 (free above Rs 199)
corr-3 timed out
unknown or late reply corr-3 dropped
```

| Option | Notes |
|--------|-------|
| Temporary reply queue per requester | Simple; lost on restart |
| Shared reply topic + correlation ID | Scales; filter by ID |
| RabbitMQ direct reply-to | No queue declaration needed |
| Spring `ReplyingKafkaTemplate` | Request-reply on Kafka |
| Plain HTTP | Often simpler if you just need an answer now |

**Use** when the responder must be decoupled or load-levelled. **Avoid** it as a slower replacement for HTTP when both sides are online anyway.
**Real-world Java:** Spring AMQP `RabbitTemplate.convertSendAndReceive`, Spring Kafka `ReplyingKafkaTemplate`, JMS `JMSReplyTo`/`JMSCorrelationID`.
**Best practice:** propagate the correlation ID into logs and traces too. **Gotcha:** a requester that restarts loses its pending map; late replies must be safely ignorable.

## 6. Examples (exactly 3, progressively harder)

### Example 1: Minimal (L2)

**Scenario:** when paneer runs low, the buying team and the app both need to know. The warehouse shouldn't call each of them.

**LowStockAlertDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Minimal: the warehouse publishes LowStock once; buyers and the app react without the warehouse knowing them. */
public class LowStockAlertDemo {

    record LowStock(String sku, int unitsLeft) {}

    static final List<Consumer<LowStock>> subscribers = new ArrayList<>();

    public static void main(String[] args) {
        subscribers.add(e -> System.out.println("buying team: raise PO to DairyDay for " + e.sku()));
        subscribers.add(e -> System.out.println("app: show 'only " + e.unitsLeft() + " left' on " + e.sku()));
        LowStock event = new LowStock("PANEER-200G", 3);
        subscribers.forEach(s -> s.accept(event));
    }
}
```

1. Each interested party subscribes on its own.
2. The warehouse publishes once and doesn't know who listens.

**Run it:** `java LowStockAlertDemo.java`

**Output:**

```text
buying team: raise PO to DairyDay for PANEER-200G
app: show 'only 3 left' on PANEER-200G
```

**What to notice:**

- Adding a third reaction (email the store manager) changes nothing in the warehouse code.

### Example 2: Realistic (in FreshCart grocery delivery)

**Scenario:** DairyDay sends prices as CSV with rupees; GreenValley sends pipe-separated paise. Large changes need human review, and bad lines must not stop the feed.

**SupplierFeedPipelineDemo.java**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Realistic: supplier price feeds flow through a pipeline of small steps (pipes and filters): translate each format
 * to the canonical model, validate, then route by content. Bad messages go to a dead letter channel with a reason.
 */
public class SupplierFeedPipelineDemo {

    record Inbound(String supplier, String payload) {}
    record PriceUpdate(String supplier, String sku, long pricePaise, int changePercent) {}

    static final Map<String, Long> currentPrice = Map.of("MILK-1L", 6800L, "TOMATO-1KG", 3000L, "CURD-500G", 4500L);
    static final Map<String, List<String>> channels = new TreeMap<>();

    static PriceUpdate translate(Inbound in) {
        String[] f = in.payload().split(in.supplier().equals("DairyDay") ? "," : "\\|");
        long paise = in.supplier().equals("DairyDay") ? Math.round(Double.parseDouble(f[1]) * 100) : Long.parseLong(f[1]);
        long old = currentPrice.getOrDefault(f[0], paise);
        return new PriceUpdate(in.supplier(), f[0], paise, (int) Math.round((paise - old) * 100.0 / old));
    }

    static void validate(PriceUpdate u) {
        if (!currentPrice.containsKey(u.sku())) throw new IllegalArgumentException("unknown SKU " + u.sku());
        if (u.pricePaise() <= 0) throw new IllegalArgumentException("non-positive price");
    }

    static String route(PriceUpdate u) {
        return Math.abs(u.changePercent()) > 20 ? "price-review" : "price-apply";
    }

    static void handle(Inbound in) {
        String channel, entry;
        try {
            PriceUpdate u = translate(in);
            validate(u);
            channel = route(u);
            entry = u.sku() + " " + u.pricePaise() + " (" + (u.changePercent() >= 0 ? "+" : "") + u.changePercent() + "%)";
        } catch (RuntimeException e) {
            channel = "dead-letter";
            entry = in.supplier() + " '" + in.payload() + "': " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        channels.computeIfAbsent(channel, k -> new ArrayList<>()).add(entry);
    }

    public static void main(String[] args) {
        List.of(new Inbound("DairyDay", "MILK-1L,70.00"), new Inbound("GreenValley", "TOMATO-1KG|6000"),
                new Inbound("DairyDay", "CURD-500G,abc"), new Inbound("GreenValley", "MANGO-1KG|15000"),
                new Inbound("DairyDay", "CURD-500G,44.00")).forEach(SupplierFeedPipelineDemo::handle);
        channels.forEach((c, entries) -> { System.out.println(c + ":"); entries.forEach(e -> System.out.println("  " + e)); });
    }
}
```

1. Translate each supplier's format into the canonical `PriceUpdate`.
2. Validate against FreshCart's catalogue.
3. Route by content: big changes go to review.

**Run it:** `java SupplierFeedPipelineDemo.java`

**Output:**

```text
dead-letter:
  DairyDay 'CURD-500G,abc': NumberFormatException For input string: "abc"
  GreenValley 'MANGO-1KG|15000': IllegalArgumentException unknown SKU MANGO-1KG
price-apply:
  MILK-1L 7000 (+3%)
  CURD-500G 4400 (-2%)
price-review:
  TOMATO-1KG 6000 (+100%)
```

**What to notice:**

- The unparseable price and the unknown SKU went to the dead letter channel with reasons; the other three lines were processed.
- A tomato price doubling went to review instead of straight to the app.

### Example 3: Production-grade

**Scenario:** the Loyalty service consumes FreshCart's partitioned `order-events` topic in a consumer group. Consumers crash, the group rebalances, the loyalty backend has outages, and one order's events always fail.

**OrderEventsConsumerDemo.java**

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Production-grade: a consumer group reading a partitioned order-events topic. Keys pin each order to a partition;
 * offsets are committed only after processing; transient failures go to a retry topic, poison messages to a DLQ
 * after three attempts; processing is idempotent so redelivery after a crash is harmless.
 */
public class OrderEventsConsumerDemo {

    record Event(String id, String orderKey, String type, int attempts) {}
    static final class TransientFailure extends RuntimeException { TransientFailure(String m) { super(m); } }

    static final class Broker {
        final List<List<Event>> partitions = new ArrayList<>();
        final int[] committed;
        final List<Event> retryTopic = new ArrayList<>(), deadLetters = new ArrayList<>();
        Broker(int partitionCount) { for (int i = 0; i < partitionCount; i++) partitions.add(new ArrayList<>()); committed = new int[partitionCount]; }
        void publish(Event e) { partitions.get(Math.floorMod(e.orderKey().hashCode(), partitions.size())).add(e); }
    }

    static final class Member {
        final String name; final Set<Integer> assigned = new HashSet<>();
        Member(String name) { this.name = name; }
    }

    static final class OrderEventsConsumer {
        static final int MAX_ATTEMPTS = 3;
        private final Broker broker; private final Consumer<Event> handler;
        final Set<String> processedIds = new HashSet<>();
        final List<String> log = new ArrayList<>();
        OrderEventsConsumer(Broker broker, Consumer<Event> handler) { this.broker = broker; this.handler = handler; }

        void assign(List<Member> members) {
            members.forEach(m -> m.assigned.clear());
            for (int p = 0; p < broker.partitions.size(); p++) members.get(p % members.size()).assigned.add(p);
            log.add("assignment: " + members.stream().map(m -> m.name + "=" + m.assigned.stream().sorted().toList()).toList());
        }

        /** Processes everything after the committed offset; crashBeforeCommit simulates dying after the work. */
        void poll(Member m, boolean crashBeforeCommit) {
            for (int p : m.assigned.stream().sorted().toList()) {
                List<Event> part = broker.partitions.get(p);
                int offset = broker.committed[p];
                for (; offset < part.size(); offset++) process(m, part.get(offset));
                if (crashBeforeCommit) { log.add(m.name + " crashed before committing p" + p); return; }
                broker.committed[p] = offset;
            }
        }

        void drainRetryTopic(Member m) {
            List<Event> batch = new ArrayList<>(broker.retryTopic);
            broker.retryTopic.clear();
            batch.forEach(e -> process(m, e));
        }

        private void process(Member m, Event e) {
            if (processedIds.contains(e.id())) { log.add(m.name + " skip duplicate " + e.id()); return; }
            try {
                handler.accept(e);
                processedIds.add(e.id());
                log.add(m.name + " handled " + e.id() + " " + e.orderKey() + " " + e.type());
            } catch (TransientFailure ex) {
                Event next = new Event(e.id(), e.orderKey(), e.type(), e.attempts() + 1);
                if (next.attempts() >= MAX_ATTEMPTS) { broker.deadLetters.add(next); log.add(m.name + " dead-lettered " + e.id() + ": " + ex.getMessage()); }
                else { broker.retryTopic.add(next); log.add(m.name + " sent " + e.id() + " to retry topic (attempt " + next.attempts() + ")"); }
            }
        }
    }

    public static void main(String[] args) {
        Broker broker = new Broker(3);
        int[] loyaltyCalls = {0};
        OrderEventsConsumer group = new OrderEventsConsumer(broker, e -> {
            if (e.type().equals("PAID") && e.orderKey().equals("FC-2") && e.attempts() < 1) throw new TransientFailure("loyalty service 503");
            if (e.type().equals("PAID") && e.orderKey().equals("FC-3")) throw new TransientFailure("loyalty service 503");
            if (e.type().equals("PAID")) loyaltyCalls[0]++;
        });
        int n = 0;
        for (String order : List.of("FC-1", "FC-2", "FC-3"))
            for (String type : List.of("PLACED", "PAID")) broker.publish(new Event("e" + ++n, order, type, 0));

        Member a = new Member("A"), b = new Member("B");
        group.assign(List.of(a, b));
        group.poll(a, true);
        group.assign(List.of(b));
        group.poll(b, false);
        for (int round = 0; round < 3; round++) group.drainRetryTopic(b);
        group.log.forEach(System.out::println);
        System.out.println("loyalty awarded: " + loyaltyCalls[0] + ", DLQ: " + broker.deadLetters.stream().map(Event::id).toList()
                + ", committed offsets: " + java.util.Arrays.toString(broker.committed));
    }
}
```

1. The order ID is the key, so all events for one order share a partition.
2. Partitions are spread over the group's members; a rebalance reassigns them.
3. Offsets are committed only after the batch is processed.
4. Processing is idempotent: event IDs already handled are skipped.
5. Transient failures go to a retry topic; after three attempts, to the dead letter topic.

**Run it:** `java OrderEventsConsumerDemo.java`

**Output:**

```text
assignment: [A=[0, 2], B=[1]]
A handled e1 FC-1 PLACED
A handled e2 FC-1 PAID
A crashed before committing p0
assignment: [B=[0, 1, 2]]
B skip duplicate e1
B skip duplicate e2
B handled e3 FC-2 PLACED
B sent e4 to retry topic (attempt 1)
B handled e5 FC-3 PLACED
B sent e6 to retry topic (attempt 1)
B handled e4 FC-2 PAID
B sent e6 to retry topic (attempt 2)
B dead-lettered e6: loyalty service 503
loyalty awarded: 2, DLQ: [e6], committed offsets: [2, 2, 2]
```

**What to notice:**

- A crashed before committing, so B received e1 and e2 again and skipped them; loyalty points weren't awarded twice.
- e4 failed once, went to the retry topic and succeeded; e6 failed three times and was dead-lettered.
- The failing event didn't block its partition: all committed offsets reached the end.
- A retry topic gives up strict per-key ordering for that event; if order matters, block the partition (with a timeout) instead.

## 7. Pros, Cons & Trade-offs

| Pattern | Pros | Cons |
|---------|------|------|
| Queue / topic | Decoupling, buffering | Broker to operate; eventual consistency |
| Competing consumers | Throughput, failover | Ordering only per key |
| Content-based router | Central, changeable routing | Can accumulate business logic |
| Dead letter channel | No blocking, no silent loss | Needs monitoring and redrive tooling |
| Message translator | Insulates internal model | One more mapping to maintain |
| Request-reply | Decoupled responses | Complexity vs plain HTTP; timeouts |

## 8. When to Use / When NOT to Use

**Use messaging when:**

- producers and consumers have different availability or speed;
- several systems react to the same event;
- integrating partners with different formats and protocols.

**Don't when:**

- the caller needs an immediate answer and both sides are online (use HTTP/gRPC);
- you'd be adding a broker for two services and a handful of messages;
- strict global ordering across all messages is required.

## 9. Best Practices

1. **Events on topics, commands on queues.** *(Consensus.)*
2. **Partition by the key that defines ordering.** *(Consensus.)*
3. **Commit or acknowledge only after processing; make consumers idempotent.** *(Consensus.)*
4. **Every consumer has a retry policy and a dead letter channel.** *(Consensus.)*
5. **Translate external formats at the edge.** *(Consensus.)*
6. **Carry message ID, correlation ID and trace context in headers.** *(Consensus.)*
7. **Prefer a schema registry for shared topics.** *(Widely used.)*

## 10. Pitfalls & Anti-Patterns

> 🚨 **Poison message blocks a partition (production failure mode)**  
> **Symptom:** loyalty points stopped for a third of customers; consumer lag on one partition grew for hours.
> **Root cause:** a malformed event threw on every attempt; the consumer retried it forever without committing.
> **Fix:** bounded retries, retry topic for transient errors, dead letter topic, alerts on lag per partition.

> 🚨 **Auto-commit before processing**  
> **Symptom:** messages "disappeared" after consumer crashes.
> **Root cause:** offsets auto-committed on poll; the crash happened mid-batch.
> **Fix:** `enable.auto.commit=false`, commit after processing (Spring Kafka `AckMode.RECORD` or `BATCH`).

> 🚨 **Ordering assumed without keys**  
> **Symptom:** orders showed `DELIVERED` then `PACKED`.
> **Root cause:** events published without a key, spread across partitions.
> **Fix:** key by order ID; version checks in consumers.

> 🚨 **Request-reply everywhere**  
> **Symptom:** a messaging system that behaves like slow, fragile RPC.
> **Root cause:** every interaction modelled as request-reply over queues.
> **Fix:** use events for facts, HTTP for queries, request-reply only where decoupling matters.

## 11. Configuration & Tuning

| Setting | Default | What it does | When to change it | Risk of changing |
|---------|---------|--------------|-------------------|------------------|
| Kafka `max.poll.records` | 500 | Records per poll | Lower for slow processing | Lower throughput |
| Kafka `max.poll.interval.ms` | 300000 | Max time between polls before rebalance | Long processing | Slow failure detection |
| Kafka `partition.assignment.strategy` | Range, CooperativeSticky | How partitions are assigned | Cooperative for fewer pauses | Upgrade coordination |
| RabbitMQ `prefetch` (Spring) | 250 | Unacked messages per consumer | Lower for fairness, higher for throughput | Memory, unfairness |
| RabbitMQ `x-delivery-limit` (quorum queues) | 20 (RabbitMQ 4.0+) | Redeliveries before dead-lettering | Lower for poison messages | Early DLQ |
| Spring `@RetryableTopic(attempts)` | 3 | Attempts incl. retry topics | Per consumer | Retry storms |

## 12. Security Considerations

- Authenticate producers and consumers (SASL/mTLS) and authorise per topic/queue with ACLs.
- Encrypt in transit; consider payload encryption for personal data in long-retention topics.
- Validate and size-limit messages from external partners before translation.
- Dead letter topics contain the same sensitive data as the main topic: same access controls and retention.

## 13. Performance & Scalability

Throughput scales with partitions (Kafka) or consumers (RabbitMQ); batch sends and compression cut network cost. Consumer lag is the key capacity signal. Large messages hurt brokers; store blobs elsewhere and send references (claim check pattern). Retry topics with delays avoid blocking hot partitions. Request-reply adds broker round trips; expect higher latency than direct calls.

## 14. Testing It

Test consumer behaviour (ordering, idempotency, retries, dead letters, rebalances) with an in-memory broker model:

**OrderEventsConsumerTest.java**

```java
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderEventsConsumerTest {

    static OrderEventsConsumerDemo.Event event(String id, String key, String type) { return new OrderEventsConsumerDemo.Event(id, key, type, 0); }

    @Test
    void sameKeyAlwaysLandsInTheSamePartition() {
        var broker = new OrderEventsConsumerDemo.Broker(4);
        for (int i = 0; i < 5; i++) broker.publish(event("e" + i, "FC-7", "T" + i));
        assertEquals(1, broker.partitions.stream().filter(p -> !p.isEmpty()).count());
    }

    @Test
    void eventsForOneOrderAreHandledInPublishOrder() {
        var broker = new OrderEventsConsumerDemo.Broker(3);
        List<String> seen = new ArrayList<>();
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> seen.add(e.orderKey() + ":" + e.type()));
        for (String t : List.of("PLACED", "PACKED", "DELIVERED")) broker.publish(event("x-" + t, "FC-1", t));
        var m = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(m));
        group.poll(m, false);
        assertEquals(List.of("FC-1:PLACED", "FC-1:PACKED", "FC-1:DELIVERED"), seen);
    }

    @Test
    void redeliveryAfterCrashDoesNotRepeatSideEffects() {
        var broker = new OrderEventsConsumerDemo.Broker(1);
        int[] effects = {0};
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> effects[0]++);
        broker.publish(event("e1", "FC-1", "PAID"));
        var a = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(a));
        group.poll(a, true);
        assertEquals(0, broker.committed[0]);
        group.poll(a, false);
        assertEquals(1, effects[0]);
        assertEquals(1, broker.committed[0]);
    }

    @Test
    void transientFailureIsRetriedThenSucceeds() {
        var broker = new OrderEventsConsumerDemo.Broker(1);
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> {
            if (e.attempts() == 0) throw new OrderEventsConsumerDemo.TransientFailure("503");
        });
        broker.publish(event("e1", "FC-1", "PAID"));
        var a = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(a));
        group.poll(a, false);
        assertEquals(1, broker.retryTopic.size());
        group.drainRetryTopic(a);
        assertTrue(group.processedIds.contains("e1"));
        assertTrue(broker.deadLetters.isEmpty());
    }

    @Test
    void persistentFailureEndsInDeadLetterAfterMaxAttempts() {
        var broker = new OrderEventsConsumerDemo.Broker(1);
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> { throw new OrderEventsConsumerDemo.TransientFailure("down"); });
        broker.publish(event("e1", "FC-1", "PAID"));
        var a = new OrderEventsConsumerDemo.Member("A");
        group.assign(List.of(a));
        group.poll(a, false);
        for (int i = 0; i < 5; i++) group.drainRetryTopic(a);
        assertEquals(1, broker.deadLetters.size());
        assertEquals(OrderEventsConsumerDemo.OrderEventsConsumer.MAX_ATTEMPTS, broker.deadLetters.get(0).attempts());
        assertEquals(1, broker.committed[0], "a failing message must not block the partition");
    }

    @Test
    void everyPartitionIsAssignedToExactlyOneMember() {
        var broker = new OrderEventsConsumerDemo.Broker(5);
        var group = new OrderEventsConsumerDemo.OrderEventsConsumer(broker, e -> {});
        var a = new OrderEventsConsumerDemo.Member("A");
        var b = new OrderEventsConsumerDemo.Member("B");
        group.assign(List.of(a, b));
        assertEquals(5, a.assigned.size() + b.assigned.size());
        a.assigned.forEach(p -> assertFalse(b.assigned.contains(p)));
    }
}
```

Then run integration tests against a real broker with Testcontainers (Kafka, RabbitMQ), or Spring's `@EmbeddedKafka`.

## 15. Observability & Operations

Monitor consumer lag per partition, processing rate and errors per consumer group, retry and DLQ rates, and message age. Propagate trace context in message headers (OpenTelemetry instruments Kafka and RabbitMQ clients). Keep tooling to inspect, replay and redrive messages. Document each topic's owner, schema and retention.

## 16. Version Notes

| Year | Change |
|------|--------|
| 2001 | JMS 1.0.2 widely adopted |
| 2003 | Hohpe and Woolf, *Enterprise Integration Patterns* |
| 2007 | RabbitMQ (AMQP 0-9-1); Apache Camel 1.0 |
| 2011 | Apache Kafka open-sourced |
| 2019 | Kafka incremental cooperative rebalancing (2.4) |
| 2024 | RabbitMQ 4.0: AMQP 1.0 core, quorum queue delivery limit default |
| 2025 | Kafka 4.0 removes ZooKeeper (KRaft only); share groups (queues for Kafka) in preview |

## 17. Expert Deep Dive

- **Log vs queue semantics.** Kafka keeps messages after consumption (replay, multiple groups); classic queues delete on ack. Kafka share groups (KIP-932) add queue-like per-message acks.
- **Claim check.** Store large payloads in object storage and send a reference.
- **Delivery guarantees.** At-most-once (commit before processing), at-least-once (after), effectively-once (at-least-once + idempotency).
- **Backpressure.** Prefetch limits and pausing partitions keep slow consumers from being overwhelmed.
- **Event-driven vs message-driven.** Events state facts and have many consumers; messages to a queue address one consumer. Mixing the two causes coupling.

> 📖 **War story: one bad event, four hours of missing points**  
> A FreshCart release published one `OrderPaid` event with a null amount. The loyalty consumer threw a `NullPointerException` on it, Spring Kafka's default error handler retried it in a tight loop, and the partition stopped moving. A third of customers got no points for four hours; the lag alert existed but was set at 100,000 messages. **Fixes:** `DefaultErrorHandler` with bounded backoff and a `DeadLetterPublishingRecoverer`, `@RetryableTopic` for transient errors, a per-partition lag alert based on message age (over 5 minutes), schema validation in the producer, and a redrive tool. **Lesson:** decide what happens to a message that can never succeed before the first one arrives.

## 18. Interview Corner

**▸ 🟢 L1–L2**

1. **Queue vs topic?** **One receiver** vs **every subscriber**.
2. **Why use messaging?** **Decoupling** in time, speed and knowledge.
3. **What's a dead letter queue?** Where **unprocessable messages** go.
4. **What's a correlation ID?** An ID **linking a reply to its request**.
5. **What's a message translator?** Converts **one format to another**.

**▸ 🟡 L3**

1. **How do you keep ordering with competing consumers?** **Partition by key.**
2. **Content-based router vs recipient list?** **One destination** vs **several**.
3. **When to commit offsets?** **After processing.**
4. **What's a poison message?** One that **fails every time**; dead-letter it.
5. **Kafka consumer group?** Consumers sharing partitions; **each group gets every message**.

**▸ 🔴 L4–L5**

1. **Retry topics vs blocking retries?** **Retry topics keep the partition moving but break per-key order**; blocking keeps order but stalls.
2. **Delivery guarantees?** **At-most, at-least, effectively-once** via idempotency.
3. **When would you choose RabbitMQ over Kafka?** **Routing-heavy work queues, per-message acks, low volume**; Kafka for **event logs, replay, high throughput**.
4. **Large payloads?** **Claim check.**
5. **Canonical data model risks?** Becomes a **company-wide God model**; keep it per context.

**▸ 💻 Hands-on**

**Task:** Add a message filter to `ContentBasedRouterDemo` that drops test orders (IDs starting with `TEST-`) before routing, and counts them.

> ✅ **Solution**  
> ```java
> int dropped = 0;
> void accept(Order o) {
>     if (o.id().startsWith("TEST-")) { dropped++; return; }   // filter: never reaches any channel
>     route(o);
> }
> ```

## 19. Practice

- [ ] Run Kafka in Testcontainers and consume one topic with two consumer groups.
    > 💡 **Hint**  
    > Each group should see every message.

- [ ] Configure Spring Kafka `@RetryableTopic` with 3 attempts and a DLT handler.
    > 💡 **Hint**  
    > Annotate a `@DltHandler` method in the same class.

- [ ] Add a recipient-list route to `ContentBasedRouterDemo` that also copies COD orders to a fraud channel.
    > 💡 **Hint**  
    > Return a list of channels instead of one.

- [ ] Measure ordering with 3 consumers and no key in `CompetingConsumersDemo`.
    > 💡 **Hint**  
    > Use different speeds per consumer.

## 20. Quick Revision Card

> 💡 **Remember in 30 seconds**  
> - Queue = one receiver (commands); topic = all subscribers (events).
> - Competing consumers scale; partition by key for ordering.
> - Router chooses the channel; keep business logic out.
> - Bounded retries, retry topic, dead letter channel, redrive.
> - Translate external formats at the edge into your model.
> - Request-reply: correlation ID + reply-to + timeout.
>
> ```java
> int partition = Math.floorMod(orderId.hashCode(), partitions);   // same key, same partition, same order
> ```

## 21. Further Reading

- Gregor Hohpe and Bobby Woolf, *Enterprise Integration Patterns* (2003) and [enterpriseintegrationpatterns.com](https://www.enterpriseintegrationpatterns.com/).
- [Spring for Apache Kafka reference](https://docs.spring.io/spring-kafka/reference/), "Non-Blocking Retries".
- Neha Narkhede, Gwen Shapira and Todd Palino, *Kafka: The Definitive Guide* (2nd ed., 2021).
- [RabbitMQ: Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx).

---

[← Previous: Distributed Data Patterns](distributed-data-patterns.md) · [Next: Caching Patterns →](caching-patterns.md)
