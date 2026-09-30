package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tz.co.hmy.pis.model.AppRole;

import java.util.Collection;
import java.util.List;

public interface AppRoleRepository extends JpaRepository<AppRole, String> {

    /**
     * The roles AND their permissions in one query. Login needs both on every
     * request, and a separate lazy load per role would be one query each.
     */
    @Query("SELECT DISTINCT r FROM AppRole r LEFT JOIN FETCH r.permissions WHERE r.name IN :names")
    List<AppRole> findWithPermissions(@Param("names") Collection<String> names);

    @Query("SELECT DISTINCT r FROM AppRole r LEFT JOIN FETCH r.permissions ORDER BY r.name")
    List<AppRole> findAllWithPermissions();
}
