package tz.co.hmy.purchaseorder;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The business rule that needs the OTHER service: only an ACTIVE supplier can receive an order. */
@Service
public class PurchaseOrderService {

    private final SupplierClient suppliers;
    private final Map<String, PurchaseOrder> orders = new ConcurrentSkipListMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public PurchaseOrderService(SupplierClient suppliers) {
        this.suppliers = suppliers;
    }

    public PurchaseOrder create(PurchaseOrderRequest request) {
        SupplierClient.SupplierLookup lookup = suppliers.find(request.supplierId())
                .orElseThrow(() -> new BusinessRuleException("Unknown supplier " + request.supplierId()));
        if (!lookup.supplier().isActive()) {
            throw new BusinessRuleException("Supplier " + request.supplierId() + " is "
                    + lookup.supplier().status() + ", so it can't receive purchase orders");
        }

        String id = "PO-%04d".formatted(sequence.incrementAndGet());
        BigDecimal total = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        PurchaseOrder order = new PurchaseOrder(id, request.supplierId(), lookup.supplier().name(),
                request.item(), request.quantity(), request.unitPrice(), total,
                lookup.servedBy(), Instant.now());
        orders.put(id, order);
        return order;
    }

    public List<PurchaseOrder> findAll() {
        return List.copyOf(orders.values());
    }

    public Optional<PurchaseOrder> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public static class BusinessRuleException extends RuntimeException {
        BusinessRuleException(String message) { super(message); }
    }
}
