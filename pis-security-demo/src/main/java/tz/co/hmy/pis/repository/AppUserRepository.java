package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.hmy.pis.model.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
