package tz.co.hmy.pis.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * One permission per action. The code checks these, never role names:
 *   hasAuthority("requisition:approve")   not   hasRole("APPROVER")
 *
 * Permissions stay in code even though roles now live in the database: a
 * permission is what a rule checks, so a new one always comes with new code
 * (and a migration that grants it to ADMIN).
 *
 * In JSON a permission is written as its authority, "requisition:approve".
 * In the role_permission table it is stored by name, REQUISITION_APPROVE.
 */
public enum Permission {

    SUPPLIER_READ("supplier:read"),
    SUPPLIER_WRITE("supplier:write"),
    SUPPLIER_APPROVE("supplier:approve"),

    REQUISITION_READ("requisition:read"),
    REQUISITION_WRITE("requisition:write"),
    REQUISITION_APPROVE("requisition:approve"),

    PURCHASE_ORDER_READ("purchase-order:read"),
    PURCHASE_ORDER_WRITE("purchase-order:write"),

    INVOICE_READ("invoice:read"),
    INVOICE_WRITE("invoice:write"),

    RECORD_DELETE("record:delete"),
    USER_MANAGE("user:manage"),
    ROLE_MANAGE("role:manage");

    private final String authority;

    Permission(String authority) { this.authority = authority; }

    @JsonValue
    public String authority() { return authority; }

    /** "requisition:approve" → REQUISITION_APPROVE. An unknown value becomes a 400, not a 500. */
    @JsonCreator
    public static Permission fromAuthority(String authority) {
        return Arrays.stream(values())
                .filter(p -> p.authority.equals(authority))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown permission: " + authority));
    }
}
