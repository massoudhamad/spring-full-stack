package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotNull;

public record UserEnabledRequest(@NotNull(message = "enabled is required") Boolean enabled) { }
