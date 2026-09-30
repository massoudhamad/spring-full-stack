package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.Invoice;
import tz.co.hmy.pis.model.InvoiceStatus;
import tz.co.hmy.pis.model.RecordStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        LocalDate invoiceDate,
        UUID supplierId,
        String supplierName,
        InvoiceStatus invoiceStatus,
        RecordStatus status,
        LocalDate dueDate,
        LocalDate paidDate,
        BigDecimal total,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(
                i.getId(), i.getInvoiceNumber(), i.getInvoiceDate(),
                i.getSupplier().getId(), i.getSupplier().getName(),
                i.getInvoiceStatus(), i.getStatus(),
                i.getDueDate(), i.getPaidDate(), i.getTotal(), i.getNotes(),
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
