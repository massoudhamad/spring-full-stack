package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoice", uniqueConstraints = @UniqueConstraint(name = "uk_invoice_number", columnNames = "invoice_number"))
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = { "id", "invoiceNumber", "invoiceStatus" }) // items excluded: lazy collection
public class Invoice extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Nullable: an invoice may arrive without a matching purchase order. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    /** Where the invoice sits in the approval and payment workflow. */
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_status", nullable = false, length = 24)
    private InvoiceStatus invoiceStatus = InvoiceStatus.DRAFT;

    /** Whether the record is still in use, independent of the workflow above. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvoiceItem> items = new ArrayList<>();

    @Builder
    public Invoice(String invoiceNumber, LocalDate invoiceDate, Supplier supplier,
            PurchaseOrder purchaseOrder, LocalDate dueDate, String notes) {
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.supplier = supplier;
        this.purchaseOrder = purchaseOrder;
        this.dueDate = dueDate;
        this.notes = notes;
    }

    public void update(LocalDate invoiceDate, Supplier supplier,
            PurchaseOrder purchaseOrder, LocalDate dueDate, String notes) {
        this.invoiceDate = invoiceDate;
        this.supplier = supplier;
        this.purchaseOrder = purchaseOrder;
        this.dueDate = dueDate;
        this.notes = notes;
    }

    public void addItem(InvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }

    public void clearItems() {
        items.forEach(i -> i.setInvoice(null));
        items.clear();
    }

    public void submit() {
        this.invoiceStatus = InvoiceStatus.SUBMITTED;
    }

    public void approve() {
        this.invoiceStatus = InvoiceStatus.APPROVED;
    }

    public void reject() {
        this.invoiceStatus = InvoiceStatus.REJECTED;
    }

    public void markPaid(LocalDate paidDate) {
        this.paidDate = paidDate;
        this.invoiceStatus = InvoiceStatus.PAID;
    }

    public void archive() {
        this.status = RecordStatus.INACTIVE;
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(InvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<InvoiceItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
