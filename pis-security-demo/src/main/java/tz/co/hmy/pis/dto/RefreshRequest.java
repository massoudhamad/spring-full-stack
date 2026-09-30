package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "refresh token is required") String refreshToken) { }
