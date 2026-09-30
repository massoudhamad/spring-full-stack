package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "supplier", uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_tin", columnNames = "tin"),
        @UniqueConstraint(name = "uk_supplier_reg_no", columnNames = "registration_number")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs it; nothing else may call it
@ToString(of = { "id", "name", "tin" }) // never the whole object
public class Supplier extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    /** Tanzania Revenue Authority Taxpayer Identification Number. */
    @Column(nullable = false, length = 20)
    private String tin;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SupplierStatus status = SupplierStatus.PENDING_APPROVAL;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 300)
    private String address;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Builder
    public Supplier(String name, String tin, String registrationNumber,
                    SupplierCategory category, String email, String phone,
                    String address, String contactPerson) {
        this.name = name;
        this.tin = tin;
        this.registrationNumber = registrationNumber;
        this.category = category;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.contactPerson = contactPerson;
    }

    public void update(String name, SupplierCategory category, String email,
                       String phone, String address, String contactPerson) {
        this.name = name;
        this.category = category;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.contactPerson = contactPerson;
    }

    public void changeStatus(SupplierStatus status) { this.status = status; }
}
