package tz.co.hmy.pis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.hmy.pis.model.PurchaseOrder;
import tz.co.hmy.pis.model.PurchaseOrderStatus;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @EntityGraph(attributePaths = {"items", "supplier", "requisition"})
    Optional<PurchaseOrder> findWithItemsById(UUID id);

    boolean existsByOrderNumber(String orderNumber);

    @Query("""
           SELECT po FROM PurchaseOrder po
           WHERE (:status     IS NULL OR po.status = :status)
             AND (:supplierId IS NULL OR po.supplier.id = :supplierId)
           """)
    Page<PurchaseOrder> search(@Param("status") PurchaseOrderStatus status,
                               @Param("supplierId") UUID supplierId,
                               Pageable pageable);

    long countByOrderNumberStartingWith(String prefix);
}
