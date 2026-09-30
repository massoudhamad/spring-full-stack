package tz.co.hmy.pis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.hmy.pis.model.Requisition;
import tz.co.hmy.pis.model.RequisitionStatus;

import java.util.Optional;
import java.util.UUID;

public interface RequisitionRepository extends JpaRepository<Requisition, UUID> {

    /** Fetches the items in the same query — without this, reading a requisition is two round trips. */
    @EntityGraph(attributePaths = "items")
    Optional<Requisition> findWithItemsById(UUID id);

    boolean existsByReference(String reference);

    @Query("""
           SELECT r FROM Requisition r
           WHERE (CAST(:status AS string)     IS NULL OR r.status = :status)
             AND (CAST(:department AS string) IS NULL
                  OR LOWER(r.department) = LOWER(CAST(:department AS string)))
           """)
    Page<Requisition> search(@Param("status") RequisitionStatus status,
                             @Param("department") String department,
                             Pageable pageable);

    /** Counts by reference prefix (REQ-2026-) rather than a date function — portable, and indexed. */
    long countByReferenceStartingWith(String prefix);
}
