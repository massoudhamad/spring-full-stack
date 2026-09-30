package tz.co.hmy.pis.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static tz.co.hmy.pis.model.Permission.*;

/**
 * A role is a named bundle of permissions. Stored by name in app_user_role.role.
 *
 * The mapping lives here, in code, so it is reviewed and versioned like code.
 * Changing who may do what is a one-line change in this file, and no rule in
 * SecurityConfig or @PreAuthorize has to change. (Exercise 4 moves it into a table.)
 */
public enum Role {

    /** Raises requisitions, registers suppliers, prepares orders and invoices. */
    OFFICER(EnumSet.of(
            SUPPLIER_READ, SUPPLIER_WRITE,
            REQUISITION_READ, REQUISITION_WRITE,
            PURCHASE_ORDER_READ, PURCHASE_ORDER_WRITE,
            INVOICE_READ, INVOICE_WRITE)),

    /** Approves what officers raise. Reads everything, writes nothing. */
    APPROVER(EnumSet.of(
            SUPPLIER_READ, SUPPLIER_APPROVE,
            REQUISITION_READ, REQUISITION_APPROVE,
            PURCHASE_ORDER_READ,
            INVOICE_READ)),

    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) { this.permissions = Collections.unmodifiableSet(permissions); }

    public Set<Permission> permissions() { return permissions; }
}
