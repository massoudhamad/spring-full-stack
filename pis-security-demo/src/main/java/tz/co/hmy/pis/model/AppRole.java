package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A role is a named bundle of permissions, stored in the role and
 * role_permission tables. Replaces the Role enum from Lesson 3C.
 *
 * The name is the primary key because it is what app_user_role stores and what
 * the "roles" claim in a JWT carries, e.g. "OFFICER".
 */
@Entity
@Table(name = "role")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = "name")
public class AppRole extends Auditable {

    /** The role everyone else depends on: it always holds every permission. */
    public static final String ADMIN = "ADMIN";

    @Id
    @Column(length = 20)
    private String name;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @ElementCollection
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<Permission> permissions = new HashSet<>();

    public AppRole(String name, String description, Set<Permission> permissions) {
        this.name = name;
        this.description = description;
        this.permissions = permissions.isEmpty() ? new HashSet<>() : EnumSet.copyOf(permissions);
    }

    /** Replaces every permission. Hibernate turns this into DELETE + INSERT on role_permission. */
    public void replacePermissions(Set<Permission> permissions) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
