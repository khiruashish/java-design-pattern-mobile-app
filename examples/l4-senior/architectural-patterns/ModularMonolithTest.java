import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModularMonolithTest {

    static final List<ModularMonolithFitnessDemo.Module> MODULES = List.of(
            new ModularMonolithFitnessDemo.Module("subscriptions", Set.of()),
            new ModularMonolithFitnessDemo.Module("billing", Set.of("subscriptions")),
            new ModularMonolithFitnessDemo.Module("delivery", Set.of("subscriptions")));

    @Test
    void declaredPublicAccessIsAllowed() {
        assertEquals(List.of(), ModularMonolithFitnessDemo.verify(MODULES,
                List.of(new ModularMonolithFitnessDemo.Access("billing", "subscriptions", "x.subscriptions.SubscriptionStarted"))));
    }

    @Test
    void internalsAndUndeclaredDependenciesAreReported() {
        var problems = ModularMonolithFitnessDemo.verify(MODULES, List.of(
                new ModularMonolithFitnessDemo.Access("billing", "subscriptions", "x.subscriptions.internal.Row"),
                new ModularMonolithFitnessDemo.Access("billing", "delivery", "x.delivery.Route")));
        assertEquals(2, problems.size());
    }

    @Test
    void cyclesAreFoundOnceEach() {
        var cyclic = List.of(
                new ModularMonolithFitnessDemo.Module("a", Set.of("b")),
                new ModularMonolithFitnessDemo.Module("b", Set.of("c")),
                new ModularMonolithFitnessDemo.Module("c", Set.of("a")));
        assertEquals(List.of("cycle: a -> b -> c -> a"), ModularMonolithFitnessDemo.cycles(cyclic));
        assertEquals(List.of(), ModularMonolithFitnessDemo.cycles(MODULES));
    }

    @Test
    void eventsArePublishedOnlyAfterCommit() {
        var bus = new ModularMonolithFitnessDemo.EventBus();
        var subscriptions = new ModularMonolithFitnessDemo.Subscriptions(bus);
        var billing = new ModularMonolithFitnessDemo.Billing(bus);
        var delivery = new ModularMonolithFitnessDemo.Delivery(bus);
        subscriptions.start("ok", 1, false);
        subscriptions.start("rolled-back", 1, true);
        assertEquals(Set.of("ok"), billing.monthlyPaise.keySet());
        assertEquals(Set.of("ok"), delivery.route);
    }

    @Test
    void everySlotAdapterPassesTheSameContract() {
        assertTrue(SlotProviderPortDemo.contract(new SlotProviderPortDemo.InHouseScheduler()).isEmpty());
        assertTrue(SlotProviderPortDemo.contract(new SlotProviderPortDemo.ParcelGoAdapter()).isEmpty());
    }

    @Test
    void cleanArchitectureRuleCatchesInwardViolation() {
        assertEquals(1, CleanOnionDemo.violations(CleanOnionDemo.BadRenewal.class).size());
        assertEquals(0, CleanOnionDemo.violations(CleanOnionDemo.Subscribe.class).size());
    }
}
