package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.hmy.pis.model.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** How many accounts hold a role: a role in use can't be deleted. */
    @Query("SELECT COUNT(u) FROM AppUser u JOIN u.roles r WHERE r = :role")
    long countWithRole(@Param("role") String role);
}
