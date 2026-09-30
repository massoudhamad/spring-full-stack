package tz.co.hmy.pis.model;

/**
 * One permission per action. The code checks these, never role names:
 *   hasAuthority("requisition:approve")   not   hasRole("APPROVER")
 *
 * The string is what Spring Security sees as the authority, and what goes
 * into the JWT's "permissions" claim.
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
    USER_MANAGE("user:manage");

    private final String authority;

    Permission(String authority) { this.authority = authority; }

    public String authority() { return authority; }
}
