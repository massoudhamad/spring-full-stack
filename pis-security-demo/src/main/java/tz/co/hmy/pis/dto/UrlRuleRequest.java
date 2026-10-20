package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tz.co.hmy.pis.model.Permission;

/**
 * A new or changed URL rule. Leave method out for "any method".
 * The permission is written as an authority: "supplier:read".
 */
public record UrlRuleRequest(

        @NotNull(message = "priority is required")
        @Min(value = 1, message = "priority is 1 or more")
        Integer priority,

        @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", message = "method is GET, POST, PUT, PATCH or DELETE (or leave it out for any method)")
        String method,

        @NotBlank(message = "pattern is required")
        @Size(max = 200)
        String pattern,

        @NotNull(message = "permission is required")
        Permission permission,

        @NotBlank(message = "description is required: say why the rule exists")
        @Size(max = 200)
        String description
) { }
