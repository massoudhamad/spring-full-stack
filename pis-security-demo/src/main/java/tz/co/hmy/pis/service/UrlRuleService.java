package tz.co.hmy.pis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.PatternParseException;
import tz.co.hmy.pis.dto.UrlRuleMatch;
import tz.co.hmy.pis.dto.UrlRuleRequest;
import tz.co.hmy.pis.dto.UrlRuleResponse;
import tz.co.hmy.pis.exception.BusinessRuleException;
import tz.co.hmy.pis.exception.DuplicateResourceException;
import tz.co.hmy.pis.exception.ResourceNotFoundException;
import tz.co.hmy.pis.model.UrlRule;
import tz.co.hmy.pis.repository.UrlRuleRepository;
import tz.co.hmy.pis.security.DatabaseUrlRules;

import java.util.List;
import java.util.Locale;

/**
 * URL rules are data now: an admin changes what an endpoint requires while PIS is
 * running. Four checks keep that safe:
 *   1. A pattern must be under /api/v1/: only those requests are decided by the table.
 *   2. It can't be under an admin endpoint: those are protected in code (SecurityConfig),
 *      so no rule can lock the admins out of fixing the rules.
 *   3. Spring must be able to read the pattern; otherwise it would never match anything.
 *   4. Two rules can't share a priority, so the order is never a coin toss.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UrlRuleService {

    /** Protected in SecurityConfig, in code. */
    static final List<String> IN_CODE = List.of("/api/v1/users", "/api/v1/roles", "/api/v1/permissions", "/api/v1/url-rules");

    private final UrlRuleRepository rules;
    private final DatabaseUrlRules urlRules;

    public List<UrlRuleResponse> findAll() {
        return rules.findAllByOrderByPriorityAsc().stream().map(UrlRuleResponse::from).toList();
    }

    /** Which rule decides this request: the same lookup the security check does. */
    public UrlRuleMatch match(String method, String path) {
        String m = method.toUpperCase(Locale.ROOT);
        return urlRules.find(m, path)
                .map(r -> new UrlRuleMatch(m, path, UrlRuleResponse.from(getOrThrow(r.id())),
                        "needs " + r.permission().authority() + " (rule " + r.priority() + ")"))
                .orElseGet(() -> new UrlRuleMatch(m, path, null, "no rule matches: denied"));
    }

    @Transactional
    public UrlRuleResponse create(UrlRuleRequest request) {
        check(request, null);
        UrlRule rule = new UrlRule(request.priority(), request.method(), request.pattern(),
                request.permission(), request.description());
        return UrlRuleResponse.from(rules.saveAndFlush(rule));
    }

    @Transactional
    public UrlRuleResponse update(Long id, UrlRuleRequest request) {
        UrlRule rule = getOrThrow(id);
        check(request, id);
        rule.change(request.priority(), request.method(), request.pattern(), request.permission(), request.description());
        return UrlRuleResponse.from(rules.saveAndFlush(rule));
    }

    @Transactional
    public void delete(Long id) {
        rules.delete(getOrThrow(id));
    }

    private void check(UrlRuleRequest request, Long id) {
        String pattern = request.pattern();
        if (!pattern.startsWith("/api/v1/")) {
            throw new BusinessRuleException("A rule's pattern must start with /api/v1/: only those requests are decided by the rules");
        }
        if (IN_CODE.stream().anyMatch(p -> pattern.equals(p) || pattern.startsWith(p + "/"))) {
            throw new BusinessRuleException(pattern + " is an admin endpoint. Those are protected in code, "
                    + "so a rule can never lock the admins out");
        }
        try {
            DatabaseUrlRules.parse(pattern);
        } catch (PatternParseException e) {
            throw new BusinessRuleException("Pattern " + pattern + " is not valid: " + e.getMessage());
        }
        rules.findByPriority(request.priority())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Priority " + request.priority() + " is already used by rule "
                            + other.getId() + " (" + other.getPattern() + ")");
                });
    }

    private UrlRule getOrThrow(Long id) {
        return rules.findById(id).orElseThrow(() -> new ResourceNotFoundException("URL rule", id));
    }
}
