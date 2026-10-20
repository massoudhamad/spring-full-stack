package tz.co.hmy.pis.dto;

/** Which rule decides a request, and what it needs. rule is null when none matches. */
public record UrlRuleMatch(String method, String path, UrlRuleResponse rule, String decision) { }
