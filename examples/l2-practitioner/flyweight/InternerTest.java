import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InternerTest {

    record Unit(String code) { }

    @Test
    void equalValuesBecomeTheSameInstance() {
        var interner = new CatalogInternerDemo.Interner<>(Unit.class, 10);
        Unit first = interner.intern(new Unit("KG"));
        assertSame(first, interner.intern(new Unit("KG")));
    }

    @Test
    void stopsSharingAboveTheCap() {
        var interner = new CatalogInternerDemo.Interner<>(Unit.class, 1);
        interner.intern(new Unit("KG"));
        Unit l1 = interner.intern(new Unit("L"));
        assertNotSame(l1, interner.intern(new Unit("L")));
        assertEquals(2, interner.overCap.sum());
    }

    @Test
    void concurrentCallersAllGetOneInstance() {
        var interner = new CatalogInternerDemo.Interner<>(Unit.class, 10);
        var seen = ConcurrentHashMap.<Unit>newKeySet();
        IntStream.range(0, 10_000).parallel().forEach(i -> seen.add(interner.intern(new Unit("KG"))));
        assertEquals(1, seen.stream().map(System::identityHashCode).distinct().count());
    }

    @Test
    void refusesMutableTypes() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogInternerDemo.Interner<>(StringBuilder.class, 10));
    }
}
