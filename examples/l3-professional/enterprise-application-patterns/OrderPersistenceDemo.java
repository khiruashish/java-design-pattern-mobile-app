import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * ParcelGo shipment persistence: Data Mapper + Identity Map + Unit of Work with dirty checking and
 * optimistic locking, behind a Repository, with DTOs at the service boundary. This is the core of what JPA does.
 */
public class OrderPersistenceDemo {

    // ---------- A tiny database with versioned rows and atomic batches ----------
    record Row(String id, String status, String rider, long version) {}

    static final class Database {
        final Map<String, Row> shipments = new TreeMap<>();
        final List<String> log = new ArrayList<>();
        int selects = 0;

        Optional<Row> select(String id) { selects++; return Optional.ofNullable(shipments.get(id)); }

        /** Applies all statements or none. An UPDATE whose version doesn't match fails the whole batch. */
        synchronized void executeBatch(List<Row> inserts, List<Row> updates) {
            for (Row u : updates) {
                Row current = shipments.get(u.id());
                if (current == null || current.version() != u.version())
                    throw new StaleUpdate(u.id(), u.version(), current == null ? -1 : current.version());
            }
            inserts.forEach(r -> { shipments.put(r.id(), r); log.add("INSERT " + r); });
            updates.forEach(r -> {
                Row next = new Row(r.id(), r.status(), r.rider(), r.version() + 1);
                shipments.put(r.id(), next);
                log.add("UPDATE " + r.id() + " SET status=" + r.status() + ", rider=" + r.rider() + ", version=" + next.version() + " WHERE version=" + r.version());
            });
        }
    }
    static final class StaleUpdate extends RuntimeException {
        StaleUpdate(String id, long expected, long actual) { super(id + " changed by someone else (had v" + expected + ", now v" + actual + ")"); }
    }

    // ---------- Domain model: no persistence code ----------
    static final class Shipment {
        enum Status { BOOKED, ASSIGNED, PICKED_UP, DELIVERED }
        private final String id; private Status status; private String rider;
        Shipment(String id) { this(id, Status.BOOKED, null); }
        Shipment(String id, Status status, String rider) { this.id = id; this.status = status; this.rider = rider; }
        void assign(String riderId) {
            if (status != Status.BOOKED && status != Status.ASSIGNED) throw new IllegalStateException(id + " is " + status);
            rider = riderId; status = Status.ASSIGNED;
        }
        void pickUp() {
            if (status != Status.ASSIGNED) throw new IllegalStateException(id + " is " + status);
            status = Status.PICKED_UP;
        }
        String id() { return id; } Status status() { return status; } String rider() { return rider; }
    }

    // ---------- Data Mapper ----------
    static final class ShipmentMapper {                                  // (1)!
        Shipment toDomain(Row r) { return new Shipment(r.id(), Shipment.Status.valueOf(r.status()), r.rider()); }
        Row toRow(Shipment s, long version) { return new Row(s.id(), s.status().name(), s.rider(), version); }
    }

    // ---------- Unit of Work with Identity Map, dirty checking and optimistic locking ----------
    static final class UnitOfWork implements AutoCloseable {
        private final Database db; private final ShipmentMapper mapper = new ShipmentMapper();
        private final Map<String, Shipment> identityMap = new LinkedHashMap<>();       // (2)!
        private final Map<String, Row> snapshots = new LinkedHashMap<>();
        private final List<Shipment> created = new ArrayList<>();

        UnitOfWork(Database db) { this.db = db; }

        Optional<Shipment> find(String id) {
            if (identityMap.containsKey(id)) return Optional.of(identityMap.get(id));
            return db.select(id).map(row -> {
                Shipment s = mapper.toDomain(row);
                identityMap.put(id, s);
                snapshots.put(id, row);                                   // remember what was loaded, with its version
                return s;
            });
        }
        void add(Shipment s) { created.add(s); identityMap.put(s.id(), s); }

        void commit() {
            List<Row> inserts = created.stream().map(s -> mapper.toRow(s, 0)).toList();
            List<Row> updates = new ArrayList<>();
            snapshots.forEach((id, loaded) -> {                           // (3)!
                Row now = mapper.toRow(identityMap.get(id), loaded.version());
                if (!now.equals(loaded)) updates.add(now);                 // unchanged objects produce no SQL
            });
            db.executeBatch(inserts, updates);                            // (4)!
            created.clear(); snapshots.clear(); identityMap.clear();
        }
        @Override public void close() { identityMap.clear(); snapshots.clear(); created.clear(); }
    }

    // ---------- Service layer with DTOs ----------
    record ShipmentView(String id, String status, String rider) {}      // (5)!
    sealed interface Outcome permits Done, Conflict, Invalid {}
    record Done(ShipmentView shipment) implements Outcome {}
    record Conflict(String message) implements Outcome {}
    record Invalid(String message) implements Outcome {}

    record ShipmentService(Database db) {
        Outcome book(String id) { return inUnit(u -> { Shipment s = new Shipment(id); u.add(s); return s; }); }
        Outcome assign(String id, String rider) { return inUnit(u -> { Shipment s = load(u, id); s.assign(rider); return s; }); }
        Outcome pickUp(String id) { return inUnit(u -> { Shipment s = load(u, id); s.pickUp(); return s; }); }

        private Shipment load(UnitOfWork u, String id) { return u.find(id).orElseThrow(() -> new IllegalStateException("no shipment " + id)); }
        private Outcome inUnit(Function<UnitOfWork, Shipment> work) {
            try (UnitOfWork u = new UnitOfWork(db)) {
                Shipment s = work.apply(u);
                u.commit();
                return new Done(view(s));
            } catch (StaleUpdate e) { return new Conflict(e.getMessage()); }
            catch (IllegalStateException e) { return new Invalid(e.getMessage()); }
        }
        static ShipmentView view(Shipment s) { return new ShipmentView(s.id(), s.status().name(), s.rider() == null ? "-" : s.rider()); }
    }

    public static void main(String[] args) {
        Database db = new Database();
        ShipmentService service = new ShipmentService(db);

        System.out.println(service.book("PG-1"));
        System.out.println(service.assign("PG-1", "rider-arjun"));

        // Two dispatchers load PG-1 at the same time and both reassign it.
        UnitOfWork deskA = new UnitOfWork(db), deskB = new UnitOfWork(db);
        deskA.find("PG-1").orElseThrow().assign("rider-bina");
        deskB.find("PG-1").orElseThrow().assign("rider-chetan");
        deskA.commit();
        try { deskB.commit(); } catch (StaleUpdate e) { System.out.println("desk B: " + e.getMessage()); }

        // Same row twice in one unit: one object, one SELECT. Unchanged object: no UPDATE.
        db.selects = 0;
        UnitOfWork reader = new UnitOfWork(db);
        System.out.println("same object: " + (reader.find("PG-1").get() == reader.find("PG-1").get()) + ", selects: " + db.selects);
        int before = db.log.size();
        reader.commit();
        System.out.println("statements for a read-only unit: " + (db.log.size() - before));

        System.out.println(service.pickUp("PG-1"));
        System.out.println(service.assign("PG-1", "rider-arjun"));
        System.out.println(service.pickUp("PG-9"));
        System.out.println("SQL log:");
        db.log.forEach(s -> System.out.println("  " + s));
    }
}
