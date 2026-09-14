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
           WHERE (CAST(:status AS string)   IS NULL OR s.status = :status)
             AND (CAST(:category AS string) IS NULL OR s.category = :category)
             AND (CAST(:search AS string)   IS NULL
                  OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                  OR s.tin LIKE CONCAT('%', CAST(:search AS string), '%'))
           """)
    Page<Supplier> search(@Param("status") SupplierStatus status,
                          @Param("category") SupplierCategory category,
                          @Param("search") String search,
                          Pageable pageable);
}
