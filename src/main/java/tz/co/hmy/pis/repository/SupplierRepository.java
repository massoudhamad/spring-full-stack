package tz.co.hmy.pis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.hmy.pis.model.Supplier;
import tz.co.hmy.pis.model.SupplierCategory;
import tz.co.hmy.pis.model.SupplierStatus;

import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsByTin(String tin);

    boolean existsByRegistrationNumber(String registrationNumber);

    @Query("""
           SELECT s FROM Supplier s
           WHERE (:status   IS NULL OR s.status = :status)
             AND (:category IS NULL OR s.category = :category)
             AND (:search   IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                    OR s.tin LIKE CONCAT('%', :search, '%'))
           """)
    Page<Supplier> search(@Param("status") SupplierStatus status,
                          @Param("category") SupplierCategory category,
                          @Param("search") String search,
                          Pageable pageable);
}
