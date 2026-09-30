package tz.co.hmy.pis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import tz.co.hmy.pis.security.Authorities;

import java.util.List;
import java.util.Set;

/** Spring creates this factory, so it can use the same Authorities bean as a real login. */
public class WithRoleSecurityContextFactory implements WithSecurityContextFactory<WithRole> {

    @Autowired
    private Authorities authorities;

    @Override
    public SecurityContext createSecurityContext(WithRole withRole) {
        String username = withRole.username().isEmpty()
                ? withRole.value().toLowerCase()
                : withRole.username();
        List<GrantedAuthority> granted = authorities.of(Set.of(withRole.value()));

        User principal = new User(username, "not-used", granted);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, granted));
        return context;
    }
}
