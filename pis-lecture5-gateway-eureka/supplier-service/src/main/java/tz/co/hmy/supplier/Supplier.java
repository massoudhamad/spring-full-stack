package tz.co.hmy.supplier;

/** A supplier. Kept in memory: this lecture is about finding services, not storing data. */
public record Supplier(String id, String name, String tin, SupplierStatus status) { }
