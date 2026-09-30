package tz.co.hmy.pis.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.co.hmy.pis.model.AppUser;
import tz.co.hmy.pis.model.Role;
import tz.co.hmy.pis.repository.AppUserRepository;

import java.util.Set;

/**
 * Solves the chicken-and-egg problem: only an admin can create users, so who
 * creates the first admin?
 *
 * On startup, if app_user is empty, create one admin from ADMIN_PASSWORD.
 * Once any user exists this does nothing, so changing ADMIN_PASSWORD later has no effect.
 * A password in a Flyway migration would be in git forever; an environment variable is not.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Value("${pis.security.admin-password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        users.save(new AppUser("admin", passwordEncoder.encode(adminPassword), "System Administrator",
                Set.of(Role.ADMIN, Role.APPROVER, Role.OFFICER)));
        log.warn("No users found. Created the initial 'admin' account from ADMIN_PASSWORD.");
    }
}
