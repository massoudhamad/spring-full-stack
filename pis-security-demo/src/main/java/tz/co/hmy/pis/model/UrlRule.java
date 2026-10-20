package tz.co.hmy.pis.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One URL rule: requests matching httpMethod + pattern need this permission.
 * The url_rule table replaces the requestMatchers(...) lines of SecurityConfig.
 */
@Entity
@Table(name = "url_rule")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"priority", "httpMethod", "pattern", "permission"})
public class UrlRule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower is checked first. The first matching rule decides. */
    @Column(nullable = false, unique = true)
    private int priority;

    /** GET, POST, PUT, PATCH or DELETE; null means any method. */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(nullable = false, length = 200)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Permission permission;

    @Column(nullable = false, length = 200)
    private String description;

    public UrlRule(int priority, String httpMethod, String pattern, Permission permission, String description) {
        change(priority, httpMethod, pattern, permission, description);
    }

    public void change(int priority, String httpMethod, String pattern, Permission permission, String description) {
        this.priority = priority;
        this.httpMethod = httpMethod;
        this.pattern = pattern;
        this.permission = permission;
        this.description = description;
    }
}
