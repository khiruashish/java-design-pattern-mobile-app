import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModernCheckoutTest {

    private static ModernCheckoutDemo.PaymentRequest upi(long paise, String vpa) {
        return new ModernCheckoutDemo.PaymentRequest("FC-T", ModernCheckoutDemo.Mode.UPI, paise, vpa, List.of("MILK"));
    }

    @Test
    void everyModeHasAProcessor() {
        var processors = ModernCheckoutDemo.processors();
        assertEquals(ModernCheckoutDemo.Mode.values().length, processors.size());
    }

    @Test
    void everyResultTypeMapsToADistinctStatus() {
        // one sample per permitted subclass, so a new result type without a sample fails this test
        List<ModernCheckoutDemo.PaymentResult> samples = List.of(
                new ModernCheckoutDemo.Captured("p", 1), new ModernCheckoutDemo.Declined("X", false), new ModernCheckoutDemo.NeedsOtp("c", "h"));
        assertEquals(ModernCheckoutDemo.PaymentResult.class.getPermittedSubclasses().length,
                samples.stream().map(Object::getClass).distinct().count());
        assertEquals(List.of(201, 402, 202), samples.stream().map(r -> ModernCheckoutDemo.toHttp(r).status()).toList());
    }

    @Test
    void requestsValidateAndCopyTheirLists() {
        assertThrows(IllegalArgumentException.class, () -> upi(0, "a@okbank"));
        assertThrows(IllegalArgumentException.class, () -> upi(100, " "));
        List<String> skus = new ArrayList<>(List.of("MILK"));
        var req = new ModernCheckoutDemo.PaymentRequest("FC-T", ModernCheckoutDemo.Mode.CARD, 100, "4242", skus);
        skus.add("BREAD");
        assertEquals(List.of("MILK"), req.skus());
        assertThrows(UnsupportedOperationException.class, () -> req.skus().add("X"));
    }

    @Test
    void sensitiveComponentsAreMaskedInLogsButNotInDefaultToString() {
        var req = upi(100, "asha@okbank");
        assertFalse(ModernCheckoutDemo.toLog(req).contains("asha"));
        assertTrue(req.toString().contains("asha"), "record toString() does not know about @Sensitive");
    }

    @Test
    void recordsAreValueEqual() {
        assertEquals(new RecordsValueObjectDemo.Money(100, "INR"), new RecordsValueObjectDemo.Money(100, "INR"));
        assertEquals(List.of("Upi", "Card", "CashOnDelivery"),
                Arrays.stream(SealedHierarchyDemo.Payment.class.getPermittedSubclasses()).map(Class::getSimpleName).toList());
    }
}
