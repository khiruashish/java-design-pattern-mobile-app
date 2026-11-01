import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A Unit of Work tracks what changed during a business transaction and writes it all at once, or not at all. */
public class UnitOfWorkDemo {

    static final class Item {
        final String sku; int stock;
        Item(String sku, int stock) { this.sku = sku; this.stock = stock; }
    }

    static final class UnitOfWork {
        private final Set<Item> newItems = new LinkedHashSet<>();         // (1)!
        private final Set<Item> dirty = new LinkedHashSet<>();
        private final Set<Item> removed = new LinkedHashSet<>();
        final List<String> executed = new ArrayList<>();

        void registerNew(Item i) { newItems.add(i); }
        void registerDirty(Item i) { if (!newItems.contains(i)) dirty.add(i); }   // (2)!
        void registerRemoved(Item i) {
            if (newItems.remove(i)) return;            // created and deleted in the same unit: no SQL at all
            dirty.remove(i);
            removed.add(i);
        }

        void commit() {                                                   // (3)!
            List<String> batch = new ArrayList<>();
            newItems.forEach(i -> batch.add("INSERT item(" + i.sku + ", " + i.stock + ")"));
            dirty.forEach(i -> batch.add("UPDATE item SET stock=" + i.stock + " WHERE sku=" + i.sku));
            removed.forEach(i -> batch.add("DELETE item WHERE sku=" + i.sku));
            executed.add("BEGIN");
            executed.addAll(batch);
            executed.add("COMMIT");
            newItems.clear(); dirty.clear(); removed.clear();
        }
        void rollback() { newItems.clear(); dirty.clear(); removed.clear(); executed.add("(nothing written)"); }
    }

    public static void main(String[] args) {
        Item milk = new Item("MILK-1L", 40), bread = new Item("BREAD", 12), stale = new Item("CAKE-OLD", 2);

        UnitOfWork uow = new UnitOfWork();
        Item eggs = new Item("EGGS-12", 30);
        uow.registerNew(eggs);
        milk.stock -= 2; uow.registerDirty(milk);
        milk.stock -= 1; uow.registerDirty(milk);      // second change, still one UPDATE
        eggs.stock -= 6; uow.registerDirty(eggs);      // new item: its INSERT already has the latest value
        uow.registerRemoved(stale);
        Item temp = new Item("TEMP", 1);
        uow.registerNew(temp); uow.registerRemoved(temp);
        uow.commit();
        uow.executed.forEach(s -> System.out.println("  " + s));

        UnitOfWork failed = new UnitOfWork();
        bread.stock -= 50; failed.registerDirty(bread);
        if (bread.stock < 0) failed.rollback();
        System.out.println("second unit: " + failed.executed);
    }
}
