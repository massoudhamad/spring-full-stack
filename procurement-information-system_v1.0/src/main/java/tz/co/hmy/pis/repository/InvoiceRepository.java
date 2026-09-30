package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.hmy.pis.model.Invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** Fetches the items and supplier in one query instead of three. */
    
    @EntityGraph(attributePaths = {"items", "supplier"})
    Optional<Invoice> findWithItemsById(UUID id);
    boolean existsByInvoiceNumber(String invoiceNumber);
}
