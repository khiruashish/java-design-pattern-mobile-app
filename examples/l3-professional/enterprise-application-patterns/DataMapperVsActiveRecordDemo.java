import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Active Record: the object saves itself. Data Mapper: a separate class moves data between objects and tables. */
public class DataMapperVsActiveRecordDemo {

    /** A tiny "database": table name -> primary key -> row. Every statement is logged. */
    static final Map<String, Map<String, Map<String, Object>>> DB = new TreeMap<>();
    static final List<String> SQL = new ArrayList<>();

    static void upsert(String table, String id, Map<String, Object> row) {
        SQL.add("UPSERT " + table + " " + new TreeMap<>(row));
        DB.computeIfAbsent(table, t -> new TreeMap<>()).put(id, row);
    }

    // ---- Active Record: one class = one table row, with persistence methods on it. ----
    static final class ProductRecord {                                  // (1)!
        String sku; String name; long pricePaise;
        ProductRecord(String sku, String name, long pricePaise) { this.sku = sku; this.name = name; this.pricePaise = pricePaise; }
        void save() { upsert("product", sku, Map.of("sku", sku, "name", name, "price_paise", pricePaise)); }
        static ProductRecord find(String sku) {
            var row = DB.get("product").get(sku);
            return new ProductRecord((String) row.get("sku"), (String) row.get("name"), (Long) row.get("price_paise"));
        }
    }

    // ---- Data Mapper: the domain object knows nothing about storage. ----
    record Money(long paise) {
        Money { if (paise < 0) throw new IllegalArgumentException("negative price"); }
        @Override public String toString() { return "Rs " + paise / 100 + "." + String.format("%02d", paise % 100); }
    }
    record Product(String sku, String name, Money price) {               // (2)!
        Product withDiscount(int percent) { return new Product(sku, name, new Money(price.paise() * (100 - percent) / 100)); }
    }

    static final class ProductMapper {                                  // (3)!
        void insert(Product p) {
            upsert("catalog_item", p.sku(), Map.of("item_code", p.sku(), "title", p.name(), "mrp_paise", p.price().paise()));
        }
        Product find(String sku) {
            var row = DB.get("catalog_item").get(sku);
            return new Product((String) row.get("item_code"), (String) row.get("title"), new Money((Long) row.get("mrp_paise")));
        }
    }

    public static void main(String[] args) {
        new ProductRecord("CURD-400G", "Curd 400g", 4_500).save();
        ProductRecord curd = ProductRecord.find("CURD-400G");
        curd.pricePaise = -100;                     // nothing protects the row's rules
        curd.save();

        ProductMapper mapper = new ProductMapper();
        mapper.insert(new Product("GHEE-1L", "Ghee 1L", new Money(65_000)));
        Product ghee = mapper.find("GHEE-1L").withDiscount(10);
        mapper.insert(ghee);
        System.out.println("domain object: " + ghee);
        SQL.forEach(s -> System.out.println("  " + s));
    }
}
