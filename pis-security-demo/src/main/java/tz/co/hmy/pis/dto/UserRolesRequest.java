package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UserRolesRequest(
        @NotEmpty(message = "at least one role is required")
        Set<@Pattern(regexp = "[A-Z_]{2,20}", message = "role names are 2-20 capital letters or underscores") String> roles
) { }
