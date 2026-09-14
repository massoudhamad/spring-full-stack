package tz.co.hmy.pis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Turns on @CreatedDate and @LastModifiedDate in Auditable. */
@Configuration
@EnableJpaAuditing
public class JpaConfig { }
