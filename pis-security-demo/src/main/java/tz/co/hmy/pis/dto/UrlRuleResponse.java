package tz.co.hmy.pis.dto;

import tz.co.hmy.pis.model.UrlRule;

import java.time.Instant;

/** method is "ANY" when the rule matches every method. */
public record UrlRuleResponse(
        Long id,
        int priority,
        String method,
        String pattern,
        String permission,
        String description,
        Instant updatedAt,
        String updatedBy
) {
    public static UrlRuleResponse from(UrlRule rule) {
        return new UrlRuleResponse(rule.getId(), rule.getPriority(),
                rule.getHttpMethod() == null ? "ANY" : rule.getHttpMethod(),
                rule.getPattern(), rule.getPermission().authority(), rule.getDescription(),
                rule.getUpdatedAt() != null ? rule.getUpdatedAt() : rule.getCreatedAt(),
                rule.getUpdatedBy() != null ? rule.getUpdatedBy() : rule.getCreatedBy());
    }
}
