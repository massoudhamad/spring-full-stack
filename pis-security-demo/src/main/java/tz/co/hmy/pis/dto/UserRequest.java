package tz.co.hmy.pis.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

/** What an admin sends to create an account. The password arrives in plain text, over HTTPS, and is hashed at once. */
public record UserRequest(

        @NotBlank(message = "username is required")
        @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}",
                 message = "username must be 3-50 letters, digits, dots, dashes or underscores")
        String username,

        // BCrypt ignores everything after 72 bytes, so a longer password gives a false sense of security.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password,

        @NotBlank(message = "full name is required")
        @Size(max = 150)
        String fullName,

        @NotEmpty(message = "at least one role is required")
        Set<@Pattern(regexp = "[A-Z_]{2,20}", message = "role names are 2-20 capital letters or underscores") String> roles
) { }
