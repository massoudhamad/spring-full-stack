package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "requisition_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "description", "quantity"})   // requisition excluded: would recurse
public class RequisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning side. No cascade back to the parent — deleting a line must not delete the requisition. */
    @Setter(AccessLevel.PACKAGE)   // only Requisition's helpers may set this
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id")
    private Requisition requisition;

    @Column(name = "item_code", length = 40)
    private String itemCode;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitOfMeasure unit;

    @Column(name = "estimated_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedUnitPrice;

    @Builder
    public RequisitionItem(String itemCode, String description, int quantity,
                           UnitOfMeasure unit, BigDecimal estimatedUnitPrice) {
        this.itemCode = itemCode;
        this.description = description;
        this.quantity = quantity;
        this.unit = unit;
        this.estimatedUnitPrice = estimatedUnitPrice;
    }

    public BigDecimal getLineTotal() {
        return estimatedUnitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
