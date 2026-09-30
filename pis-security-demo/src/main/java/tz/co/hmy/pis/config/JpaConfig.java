package tz.co.hmy.pis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Turns on @CreatedDate, @LastModifiedDate, @CreatedBy and @LastModifiedBy in Auditable. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "currentUser")
public class JpaConfig {

    /**
     * Who is making this change? Spring Data calls this on every insert and update.
     *
     * The answer comes from the SecurityContext, which BasicAuthenticationFilter
     * filled in for this request. Work done with no logged-in user, such as the
     * startup AdminAccountInitializer, is recorded as "system".
     */
    @Bean
    public AuditorAware<String> currentUser() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(auth -> !(auth instanceof AnonymousAuthenticationToken))
                .map(Authentication::getName)
                .or(() -> Optional.of("system"));
    }
}
