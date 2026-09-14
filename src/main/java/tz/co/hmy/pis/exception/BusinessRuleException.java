package tz.co.hmy.pis.exception;

/** A request that is well-formed and authorised but not allowed by the domain. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) { super(message); }
}
