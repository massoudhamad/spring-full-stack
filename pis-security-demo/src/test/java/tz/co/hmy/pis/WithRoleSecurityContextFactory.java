package tz.co.hmy.pis;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import tz.co.hmy.pis.security.Authorities;

import java.util.List;
import java.util.Set;

public class WithRoleSecurityContextFactory implements WithSecurityContextFactory<WithRole> {

    @Override
    public SecurityContext createSecurityContext(WithRole withRole) {
        String username = withRole.username().isEmpty()
                ? withRole.value().name().toLowerCase()
                : withRole.username();
        List<GrantedAuthority> authorities = Authorities.of(Set.of(withRole.value()));

        User principal = new User(username, "not-used", authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        return context;
    }
}
