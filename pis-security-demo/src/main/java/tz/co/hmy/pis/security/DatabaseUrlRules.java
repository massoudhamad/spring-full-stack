package tz.co.hmy.pis.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tz.co.hmy.pis.model.Permission;
import tz.co.hmy.pis.repository.UrlRuleRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The URL rules, read from the url_rule table instead of written in SecurityConfig.
 *
 * SecurityConfig hands every /api/v1 request (except the admin endpoints, which stay
 * in code) to check(). The rules are tried in priority order and the FIRST match
 * decides: the caller needs that rule's permission. No rule matches: denied. So a
 * missing or broken rule can only close an endpoint, never open one.
 *
 * The rules are kept in memory, because a query on every request would be slow.
 * UrlRuleController calls reload() after each change. With more than one copy of
 * PIS running, the OTHER copies keep their old rules until they reload too.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseUrlRules implements AuthorizationManager<RequestAuthorizationContext> {

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    /** One url_rule row, with its pattern parsed once. method null = any method. */
    public record Rule(Long id, int priority, String method, PathPattern pattern, Permission permission) {
        boolean matches(String requestMethod, String path) {
            return (method == null || method.equals(requestMethod))
                    && pattern.matches(PathContainer.parsePath(path));
        }
    }

    private final UrlRuleRepository repository;
    private volatile List<Rule> rules;          // null until first needed

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        Optional<Rule> rule = find(request.getMethod(), PATHS.getPathWithinApplication(request));
        if (rule.isEmpty()) {
            return new AuthorizationDecision(false);                     // no rule: denied, never allowed
        }
        String needed = rule.get().permission().authority();
        boolean granted = authentication.get().getAuthorities().stream()
                .anyMatch(a -> needed.equals(a.getAuthority()));
        return new AuthorizationDecision(granted);
    }

    /** The rule that decides this method and path, if any. */
    public Optional<Rule> find(String method, String path) {
        List<Rule> current = rules;
        if (current == null) current = reload();
        return current.stream().filter(r -> r.matches(method, path)).findFirst();
    }

    /** Reads the table again. Call it after every change to url_rule. */
    public List<Rule> reload() {
        List<Rule> loaded = repository.findAllByOrderByPriorityAsc().stream()
                .map(r -> new Rule(r.getId(), r.getPriority(), r.getHttpMethod(), parse(r.getPattern()), r.getPermission()))
                .toList();
        rules = loaded;
        log.info("Loaded {} URL rules", loaded.size());
        return loaded;
    }

    /** Throws PatternParseException for a pattern Spring can't read: ** followed by more segments, say. */
    public static PathPattern parse(String pattern) {
        return PathPatternParser.defaultInstance.parse(pattern);
    }
}
