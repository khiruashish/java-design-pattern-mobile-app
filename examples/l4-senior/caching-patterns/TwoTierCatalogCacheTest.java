import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwoTierCatalogCacheTest {

    TwoTierCatalogCacheDemo.Database db;
    TwoTierCatalogCacheDemo.SharedCache l2;
    TwoTierCatalogCacheDemo.AppInstance a, b;

    @BeforeEach
    void setUp() {
        db = new TwoTierCatalogCacheDemo.Database();
        db.rows.put("MILK-1L", new TwoTierCatalogCacheDemo.Product("MILK-1L", "Milk", 68));
        l2 = new TwoTierCatalogCacheDemo.SharedCache();
        a = new TwoTierCatalogCacheDemo.AppInstance("a", l2, db);
        b = new TwoTierCatalogCacheDemo.AppInstance("b", l2, db);
    }

    @Test
    void secondInstanceIsServedFromSharedCache() {
        a.get("MILK-1L", 0);
        assertTrue(b.get("MILK-1L", 1).contains("from L2"));
        assertEquals(1, db.reads);
    }

    @Test
    void updateIsVisibleOnEveryInstanceImmediately() {
        a.get("MILK-1L", 0);
        b.get("MILK-1L", 0);
        a.updatePrice("MILK-1L", 70);
        assertTrue(a.get("MILK-1L", 1).endsWith("Rs 70"));
        assertTrue(b.get("MILK-1L", 1).endsWith("Rs 70"));
    }

    @Test
    void missingSkuIsCachedButOnlyBriefly() {
        a.get("NOPE", 0);
        a.get("NOPE", 10_000);
        b.get("NOPE", 20_000);
        assertEquals(1, db.reads);
        b.get("NOPE", TwoTierCatalogCacheDemo.AppInstance.NEGATIVE_TTL + 1);
        assertEquals(2, db.reads);
    }

    @Test
    void localCopyExpiresBeforeSharedCopy() {
        a.get("MILK-1L", 0);
        assertTrue(a.get("MILK-1L", TwoTierCatalogCacheDemo.AppInstance.L1_TTL + 1).contains("from L2"));
    }

    @Test
    void localCacheIsBoundedLeastRecentlyUsed() {
        for (String s : new String[] {"S1", "S2", "S3", "S4"}) db.rows.put(s, new TwoTierCatalogCacheDemo.Product(s, s, 1));
        a.get("S1", 0); a.get("S2", 0); a.get("S3", 0);
        a.get("S1", 1);                 // S1 recently used, S2 is now eldest
        a.get("S4", 2);                 // evicts S2
        assertTrue(a.get("S1", 3).contains("from L1"));
        assertTrue(a.get("S2", 3).contains("from L2"));
    }

    @Test
    void keysCarryTheSchemaVersion() {
        a.get("MILK-1L", 0);
        assertTrue(l2.entries.containsKey("catalog:v2:MILK-1L"));
    }
}
