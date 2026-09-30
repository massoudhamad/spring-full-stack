package tz.co.hmy.pis.model;

/** Where an invoice sits in the approval and payment workflow. */
public enum InvoiceStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    PAID,
    REJECTED
}
