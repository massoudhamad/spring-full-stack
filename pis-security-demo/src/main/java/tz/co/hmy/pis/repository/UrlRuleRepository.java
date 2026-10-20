package tz.co.hmy.pis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tz.co.hmy.pis.model.UrlRule;

import java.util.List;
import java.util.Optional;

public interface UrlRuleRepository extends JpaRepository<UrlRule, Long> {

    /** In the order they are checked. */
    List<UrlRule> findAllByOrderByPriorityAsc();

    Optional<UrlRule> findByPriority(int priority);
}
