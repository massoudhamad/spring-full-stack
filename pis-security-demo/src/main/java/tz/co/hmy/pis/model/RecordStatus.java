package tz.co.hmy.pis.model;

/**
 * Whether a record is still in use. Separate from the workflow status: an
 * invoice can be PAID (workflow) and INACTIVE (archived) at the same time.
 */
public enum RecordStatus {
    ACTIVE,
    INACTIVE
}
