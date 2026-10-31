import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderPersistenceTest {

    OrderPersistenceDemo.Database db;
    OrderPersistenceDemo.ShipmentService service;

    @BeforeEach
    void setUp() {
        db = new OrderPersistenceDemo.Database();
        service = new OrderPersistenceDemo.ShipmentService(db);
        service.book("PG-1");
    }

    @Test
    void identityMapReturnsOneObjectPerRowAndQueriesOnce() {
        var unit = new OrderPersistenceDemo.UnitOfWork(db);
        db.selects = 0;
        assertSame(unit.find("PG-1").orElseThrow(), unit.find("PG-1").orElseThrow());
        assertEquals(1, db.selects);
    }

    @Test
    void unchangedObjectsWriteNothing() {
        var unit = new OrderPersistenceDemo.UnitOfWork(db);
        unit.find("PG-1");
        int before = db.log.size();
        unit.commit();
        assertEquals(before, db.log.size());
    }

    @Test
    void secondConcurrentWriterGetsAConflictAndNothingIsLost() {
        var a = new OrderPersistenceDemo.UnitOfWork(db);
        var b = new OrderPersistenceDemo.UnitOfWork(db);
        a.find("PG-1").orElseThrow().assign("R-A");
        b.find("PG-1").orElseThrow().assign("R-B");
        a.commit();
        assertThrows(OrderPersistenceDemo.StaleUpdate.class, b::commit);
        assertEquals("R-A", db.shipments.get("PG-1").rider());
        assertEquals(1, db.shipments.get("PG-1").version());
    }

    @Test
    void batchIsAllOrNothing() {
        var stale = new OrderPersistenceDemo.Row("PG-1", "ASSIGNED", "R-X", 7);
        var fresh = new OrderPersistenceDemo.Row("PG-2", "BOOKED", null, 0);
        assertThrows(OrderPersistenceDemo.StaleUpdate.class, () -> db.executeBatch(List.of(fresh), List.of(stale)));
        assertEquals(false, db.shipments.containsKey("PG-2"));
    }

    @Test
    void domainRulesAreReportedAsInvalidNotPersisted() {
        assertInstanceOf(OrderPersistenceDemo.Invalid.class, service.pickUp("PG-1"));    // not assigned yet
        assertEquals("BOOKED", db.shipments.get("PG-1").status());
    }

    @Test
    void checkoutRollsBackStockWhenAnyLineFails() {
        var store = new CheckoutServiceDemo.Store();
        var products = new CheckoutServiceDemo.ProductRepository(store);
        products.save(new CheckoutServiceDemo.Product("A", "A", 100, 5));
        products.save(new CheckoutServiceDemo.Product("B", "B", 100, 1));
        var checkout = new CheckoutServiceDemo.CheckoutService(store);
        var result = checkout.checkout(new CheckoutServiceDemo.CheckoutRequest("x",
                List.of(new CheckoutServiceDemo.CartItem("A", 2), new CheckoutServiceDemo.CartItem("B", 3))));
        assertInstanceOf(CheckoutServiceDemo.Rejected.class, result);
        assertEquals(5, products.find("A").orElseThrow().stock());
    }
}
