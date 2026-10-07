package tz.co.hmy.supplier;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Three fixed suppliers, the same in every copy of this service, so it doesn't
 * matter which copy answers. (With a real database, every copy would share it.)
 */
@Repository
public class SupplierRepository {

    private final Map<String, Supplier> suppliers = new TreeMap<>(Map.of(
            "S-001", new Supplier("S-001", "Kisiwa ICT Consultants", "101-201-301", SupplierStatus.ACTIVE),
            "S-002", new Supplier("S-002", "Unguja Stationers Ltd", "555-666-777", SupplierStatus.ACTIVE),
            "S-003", new Supplier("S-003", "Pemba Hardware Ltd", "888-777-666", SupplierStatus.SUSPENDED)));

    public List<Supplier> findAll() {
        return List.copyOf(suppliers.values());
    }

    public Optional<Supplier> findById(String id) {
        return Optional.ofNullable(suppliers.get(id));
    }
}
