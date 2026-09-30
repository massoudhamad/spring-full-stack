package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tz.co.hmy.pis.repository.PurchaseOrderRepository;
import tz.co.hmy.pis.repository.RequisitionRepository;

import java.time.Year;

/**
 * Human-readable references: REQ-2026-000147, PO-2026-000032.
 *
 * Counting existing rows is fine at this scale and wrong under concurrency —
 * two simultaneous creates can produce the same number. When that matters,
 * replace the count with a database sequence per year. Noted rather than
 * solved, because a sequence is the right answer and a lock is not.
 */
@RequiredArgsConstructor
@Component
public class ReferenceGenerator {

    private final RequisitionRepository requisitions;
    private final PurchaseOrderRepository purchaseOrders;

    public String nextRequisitionReference() {
        String prefix = "REQ-%d-".formatted(Year.now().getValue());
        return prefix + "%06d".formatted(requisitions.countByReferenceStartingWith(prefix) + 1);
    }

    public String nextPurchaseOrderNumber() {
        String prefix = "PO-%d-".formatted(Year.now().getValue());
        return prefix + "%06d".formatted(purchaseOrders.countByOrderNumberStartingWith(prefix) + 1);
    }
}
