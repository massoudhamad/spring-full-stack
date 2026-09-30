package tz.co.hmy.pis;

import org.springframework.security.test.context.support.WithSecurityContext;
import tz.co.hmy.pis.model.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Like @WithMockUser(roles = "OFFICER"), but the fake user also gets every
 * permission Role.OFFICER has, taken from the same mapping production uses.
 *
 * @WithMockUser(roles = ...) gives ONLY "ROLE_OFFICER", which no
 * hasAuthority("supplier:write") rule accepts.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@WithSecurityContext(factory = WithRoleSecurityContextFactory.class)
public @interface WithRole {

    Role value();

    /** Defaults to the role name in lower case: "officer", "approver", "admin". */
    String username() default "";
}
