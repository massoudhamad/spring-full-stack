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
@Table(name = "requisition",
       uniqueConstraints = @UniqueConstraint(name = "uk_requisition_reference", columnNames = "reference"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "reference", "status"})   // items excluded: it is a lazy collection
public class Requisition extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String reference;

    @Column(nullable = false, length = 150)
    private String department;

    @Column(name = "requested_by", nullable = false, length = 150)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    @Column(length = 1000)
    private String justification;

    @Column(name = "required_by_date")
    private LocalDate requiredByDate;

    /**
     * Composition: items have no life outside the requisition.
     * cascade + orphanRemoval means saving the parent saves them, and removing
     * one from the list deletes the row.
     */
    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RequisitionItem> items = new ArrayList<>();

    @Builder
    public Requisition(String reference, String department, String requestedBy,
                       String justification, LocalDate requiredByDate) {
        this.reference = reference;
        this.department = department;
        this.requestedBy = requestedBy;
        this.justification = justification;
        this.requiredByDate = requiredByDate;
    }

    public void update(String department, String requestedBy,
                       String justification, LocalDate requiredByDate) {
        this.department = department;
        this.requestedBy = requestedBy;
        this.justification = justification;
        this.requiredByDate = requiredByDate;
    }

    // --- collection helpers: maintain both sides ---

    public void addItem(RequisitionItem item) {
        items.add(item);
        item.setRequisition(this);
    }

    public void clearItems() {
        items.forEach(i -> i.setRequisition(null));
        items.clear();
    }

    public void submit()        { this.status = RequisitionStatus.SUBMITTED; }
    public void approve()       { this.status = RequisitionStatus.APPROVED; }
    public void reject()        { this.status = RequisitionStatus.REJECTED; }
    public void markConverted() { this.status = RequisitionStatus.CONVERTED; }

    /** Derived, never stored — a stored total is a total that can disagree with its lines. */
    public BigDecimal getEstimatedTotal() {
        return items.stream()
                .map(RequisitionItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Declared explicitly so Lombok does not generate one that hands out the
     * mutable list. Items are added through addItem so both sides stay in step.
     */
    public List<RequisitionItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
