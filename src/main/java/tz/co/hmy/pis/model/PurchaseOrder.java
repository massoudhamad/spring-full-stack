package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_order",
       uniqueConstraints = @UniqueConstraint(name = "uk_po_order_number", columnNames = "order_number"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "orderNumber", "status"})
public class PurchaseOrder extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Nullable: a direct purchase order need not originate from a requisition. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id")
    private Requisition requisition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @Builder
    public PurchaseOrder(String orderNumber, Supplier supplier, Requisition requisition,
                         LocalDate expectedDeliveryDate, String notes) {
        this.orderNumber = orderNumber;
        this.supplier = supplier;
        this.requisition = requisition;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.notes = notes;
    }

    public void update(Supplier supplier, LocalDate expectedDeliveryDate, String notes) {
        this.supplier = supplier;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.notes = notes;
    }

    public void addItem(PurchaseOrderItem item) {
        items.add(item);
        item.setPurchaseOrder(this);
    }

    public void clearItems() {
        items.forEach(i -> i.setPurchaseOrder(null));
        items.clear();
    }

    public void issue(LocalDate issuedDate) {
        this.issuedDate = issuedDate;
        this.status = PurchaseOrderStatus.ISSUED;
    }

    public void cancel() { this.status = PurchaseOrderStatus.CANCELLED; }

    public BigDecimal getTotal() {
        return items.stream()
                .map(PurchaseOrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<PurchaseOrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
