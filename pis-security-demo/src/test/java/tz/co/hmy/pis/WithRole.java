package tz.co.hmy.pis;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Like @WithMockUser(roles = "OFFICER"), but the fake user also gets every
 * permission the OFFICER role has, read from the role tables exactly as a real
 * login reads them.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@WithSecurityContext(factory = WithRoleSecurityContextFactory.class)
public @interface WithRole {

    /** A role name from the role table, e.g. "OFFICER". */
    String value();

    /** Defaults to the role name in lower case: "officer", "approver", "admin". */
    String username() default "";
}
