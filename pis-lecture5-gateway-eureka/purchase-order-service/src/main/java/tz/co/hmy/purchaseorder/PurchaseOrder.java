package tz.co.hmy.purchaseorder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A purchase order. It copies the supplier's name at the time of ordering: this
 * service never reads the supplier table, it asks supplier-service.
 *
 * @param checkedBy which copy of supplier-service confirmed the supplier
 */
public record PurchaseOrder(String id, String supplierId, String supplierName, String item,
                            int quantity, BigDecimal unitPrice, BigDecimal total,
                            String checkedBy, Instant createdAt) { }
